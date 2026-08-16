package com.miragemock.tcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.enums.ConnMode;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.engine.TcpMockResult;
import com.miragemock.core.log.RequestLogEntry;
import com.miragemock.core.log.RequestLogSink;
import com.miragemock.core.render.RenderedResponse;
import com.miragemock.dsl.crypto.Codec;
import com.miragemock.tcp.codec.RouteExtractor;
import com.miragemock.tcp.frame.FrameEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * TCP Mock 处理器（@Sharable，一个监听器共享一个实例）。
 *
 * <p>处理流程：帧→解析→路由提取→规则匹配（阶段一，不阻塞）→[延迟异步调度]→渲染/故障（阶段二）→
 * 编码 → 按 frame_config 加帧 → 回写；支持长短连接、故障注入、主动推送。</p>
 *
 * <p>规则延迟在事件循环上异步调度（{@link ctx.executor().schedule}），不阻塞 Netty EventLoop；
 * 响应按 frame_config 加帧（length_field 前置长度头等），与入站解码互逆，避免长度头协议客户端错位。</p>
 */
@ChannelHandler.Sharable
public class TcpMockHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpMockHandler.class);
    private static final AttributeKey<ByteBuf> ACC = AttributeKey.valueOf("mirageTcpAcc");

    private final TcpListenerRuntime runtime;
    private final MockEngine engine;
    private final RequestLogSink sink;
    private final TcpServerManager manager;

    public TcpMockHandler(TcpListenerRuntime runtime, MockEngine engine,
                          RequestLogSink sink, TcpServerManager manager) {
        this.runtime = runtime;
        this.engine = engine;
        this.sink = sink;
        this.manager = manager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        manager.registerChannel(runtime.getListener().getId(), ctx.channel());
        // onConnect 推送
        for (Map<String, Object> push : runtime.getOnConnect()) {
            long delay = toLong(push.get("delayMs"), 0);
            Object tpl = push.get("template");
            if (tpl == null) {
                continue;
            }
            if (delay <= 0) {
                writePush(ctx, tpl);
            } else {
                ctx.executor().schedule(() -> writePush(ctx, tpl), delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        manager.unregisterChannel(runtime.getListener().getId(), ctx.channel());
        ByteBuf acc = ctx.channel().attr(ACC).get();
        if (acc != null) {
            acc.release();
            ctx.channel().attr(ACC).set(null);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            if (runtime.isFramed()) {
                byte[] frame = readBytes(buf);
                process(ctx, frame);
            } else {
                // close_end：累积
                ByteBuf acc = ctx.channel().attr(ACC).get();
                if (acc == null) {
                    acc = ctx.alloc().buffer();
                    ctx.channel().attr(ACC).set(acc);
                }
                acc.writeBytes(buf);
            }
        } finally {
            buf.release();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (!runtime.isFramed()) {
            ByteBuf acc = ctx.channel().attr(ACC).get();
            if (acc != null && acc.readableBytes() > 0) {
                byte[] frame = readBytes(acc);
                acc.clear();
                process(ctx, frame);
            }
        }
    }

    private void process(ChannelHandlerContext ctx, byte[] frame) {
        long t0 = System.currentTimeMillis();
        TcpListener listener = runtime.getListener();
        String clientAddr = ctx.channel().remoteAddress() == null ? "?" : ctx.channel().remoteAddress().toString();
        Map<String, Object> fields;
        try {
            fields = runtime.getParser().parse(frame, runtime.getFormatConfig());
        } catch (Throwable t) {
            log.error("TCP 报文解析失败, listener={}, port={}", listener.getName(), listener.getPort(), t);
            writeErrorFrame(ctx, listener, t);
            appendLog(listener, null, null, clientAddr, frame, null, Codec.hex(frame), false, t0);
            return;
        }
        String route = RouteExtractor.extract(fields, listener.getRouteExtract());
        MockEngine.TcpMatch match = engine.matchTcp(listener.getProjectId(), listener.getId(), route, fields, clientAddr);
        if (!match.isMatched()) {
            TcpMockResult result = TcpMockResult.notMatched(match.getProjectId(), match.getInterfaceId(), route);
            finishTcp(ctx, listener, result, clientAddr, frame, fields, t0);
            return;
        }
        long delayMs = match.getDelayMs();
        if (delayMs > 0) {
            // 异步延迟：不阻塞事件循环；延迟结束后仍回到该连接的事件循环线程继续处理，
            // 与后续 channelRead 保持同线程串行语义。
            ctx.executor().schedule(() -> {
                if (!ctx.channel().isActive()) {
                    log.debug("延迟期间连接已关闭, listener={}, port={}", listener.getName(), listener.getPort());
                    return;
                }
                finishTcp(ctx, listener, engine.renderTcp(match), clientAddr, frame, fields, t0);
            }, delayMs, TimeUnit.MILLISECONDS);
        } else {
            finishTcp(ctx, listener, engine.renderTcp(match), clientAddr, frame, fields, t0);
        }
    }

    /** 阶段二收尾：故障动作 + 编码 + 加帧回写 + 日志（延迟场景在事件循环调度任务中执行）。 */
    private void finishTcp(ChannelHandlerContext ctx, TcpListener listener, TcpMockResult result,
                           String clientAddr, byte[] frame, Map<String, Object> fields, long t0) {
        byte[] payloadBytes = new byte[0]; // 报文正文（去帧），用于日志展示，与请求一样不含帧头
        byte[] respBytes = new byte[0];    // 实际回写的线路字节（含帧）
        try {
            switch (result.getAction()) {
                case RESET:
                    ctx.close();
                    return;
                case TIMEOUT:
                    return;
                case WRITE:
                default:
                    payloadBytes = runtime.getParser().encode(result.getFields(), runtime.getFormatConfig());
                    break;
            }
            // 响应按 frame_config 加帧（length_field 前置长度头等），与入站解码互逆——
            // 否则长度头协议的客户端会把正文首字节当帧头/内容错位（如 0x74 被读成 't'）。
            respBytes = FrameEncoder.encode(payloadBytes, listener.getFrameConfig());
            boolean shortConn = parseConnMode(listener.getConnMode()) == ConnMode.SHORT;
            ByteBuf out = Unpooled.wrappedBuffer(respBytes);
            if (shortConn) {
                ctx.writeAndFlush(out).addListener(future -> ctx.close());
            } else {
                ctx.writeAndFlush(out);
            }
        } catch (Throwable t) {
            log.error("TCP 处理异常, listener={}, port={}", listener.getName(), listener.getPort(), t);
            try {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "MOCK_INTERNAL_ERROR");
                err.put("message", t.getMessage());
                payloadBytes = runtime.getParser().encode(err, runtime.getFormatConfig());
                respBytes = FrameEncoder.encode(payloadBytes, listener.getFrameConfig());
                ctx.writeAndFlush(Unpooled.wrappedBuffer(respBytes));
            } catch (Exception ignore) {
                ctx.close();
            }
        } finally {
            appendLog(listener, result.getInterfaceId(), result.getRuleId(), clientAddr,
                    frame, fields, new String(payloadBytes, StandardCharsets.UTF_8), result.isMatched(), t0);
        }
    }

    /** 解析失败时回写错误帧（不泄露内部堆栈细节，仅通用错误码；仍按 frame_config 加帧）。 */
    private void writeErrorFrame(ChannelHandlerContext ctx, TcpListener listener, Throwable t) {
        try {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "MOCK_INTERNAL_ERROR");
            err.put("message", "报文解析失败");
            byte[] payload = runtime.getParser().encode(err, runtime.getFormatConfig());
            ctx.writeAndFlush(Unpooled.wrappedBuffer(FrameEncoder.encode(payload, listener.getFrameConfig())));
        } catch (Exception ignore) {
            ctx.close();
        }
    }

    /** 统一异步日志落库（响应记录去帧后的报文正文）。 */
    private void appendLog(TcpListener listener, Long interfaceId, Long ruleId, String clientAddr,
                           byte[] frame, Map<String, Object> fields, String responsePayload,
                           boolean matched, long t0) {
        int cost = (int) (System.currentTimeMillis() - t0);
        try {
            sink.append(RequestLogEntry.builder()
                    .projectId(listener.getProjectId())
                    .interfaceId(interfaceId)
                    .ruleId(ruleId)
                    .protocol("TCP")
                    .clientAddr(clientAddr)
                    .requestRaw(truncate(Codec.hex(frame)))
                    .requestParsed(fields == null ? null : truncate(JsonUtils.toJson(fields)))
                    .responseRaw(truncate(responsePayload))
                    .matched(matched)
                    .costMs(cost)
                    .build());
        } catch (Exception e) {
            log.debug("TCP 日志写入失败", e);
        }
    }

    /** 渲染推送模板并写入当前连接（按 frame_config 加帧）。 */
    @SuppressWarnings("unchecked")
    private void writePush(ChannelHandlerContext ctx, Object template) {
        try {
            JsonNode node = JsonUtils.mapper().valueToTree(template);
            RenderedResponse rr = engine.renderForEval(node, new HashMap<>(), runtime.getListener().getProjectId());
            Object body = rr.getBody();
            Map<String, Object> fields = (body instanceof Map) ? (Map<String, Object>) body : new LinkedHashMap<>();
            byte[] bytes = runtime.getParser().encode(fields, runtime.getFormatConfig());
            bytes = FrameEncoder.encode(bytes, runtime.getListener().getFrameConfig());
            if (ctx.channel().isActive()) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes));
            }
        } catch (Exception e) {
            log.warn("推送渲染失败: {}", e.getMessage());
        }
    }

    /** 兼容存量字符串配置的连接模式解析：非法/缺失值按长连接处理 */
    private static ConnMode parseConnMode(String value) {
        if (value == null) {
            return ConnMode.LONG;
        }
        try {
            return ConnMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ConnMode.LONG;
        }
    }

    private byte[] readBytes(ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    private long toLong(Object o, long def) {
        if (o == null) {
            return def;
        }
        try {
            return o instanceof Number ? ((Number) o).longValue() : Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 8000 ? s.substring(0, 8000) + "...(truncated)" : s;
    }
}
