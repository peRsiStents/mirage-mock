package com.miragemock.tcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.engine.TcpMockResult;
import com.miragemock.core.log.RequestLogEntry;
import com.miragemock.core.log.RequestLogSink;
import com.miragemock.core.render.RenderedResponse;
import com.miragemock.dsl.crypto.Codec;
import com.miragemock.tcp.codec.RouteExtractor;
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
 * 处理流程：帧→解析→路由提取→规则匹配→渲染→编码→回写；支持长短连接、故障注入、主动推送。
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
        Map<String, Object> fields = new LinkedHashMap<>();
        byte[] respBytes = new byte[0];
        Long ifaceId = null;
        Long ruleId = null;
        boolean matched = false;
        boolean closeAfter = false;

        try {
            fields = runtime.getParser().parse(frame, runtime.getFormatConfig());
            String route = RouteExtractor.extract(fields, listener.getRouteExtract());
            TcpMockResult result = engine.handleTcp(
                    listener.getProjectId(), listener.getId(), route, fields, clientAddr);
            ifaceId = result.getInterfaceId();
            ruleId = result.getRuleId();
            matched = result.isMatched();

            switch (result.getAction()) {
                case RESET:
                    ctx.close();
                    return;
                case TIMEOUT:
                    return;
                case WRITE:
                default:
                    respBytes = runtime.getParser().encode(result.getFields(), runtime.getFormatConfig());
                    break;
            }
            boolean shortConn = "SHORT".equalsIgnoreCase(listener.getConnMode());
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
                byte[] errBytes = runtime.getParser().encode(err, runtime.getFormatConfig());
                ctx.writeAndFlush(Unpooled.wrappedBuffer(errBytes));
            } catch (Exception ignore) {
                ctx.close();
            }
        } finally {
            int cost = (int) (System.currentTimeMillis() - t0);
            try {
                sink.append(RequestLogEntry.builder()
                        .projectId(listener.getProjectId())
                        .interfaceId(ifaceId)
                        .ruleId(ruleId)
                        .protocol("TCP")
                        .clientAddr(clientAddr)
                        .requestRaw(truncate(Codec.hex(frame)))
                        .requestParsed(truncate(JsonUtils.toJson(fields)))
                        .responseRaw(truncate(new String(respBytes, StandardCharsets.UTF_8)))
                        .matched(matched)
                        .costMs(cost)
                        .build());
            } catch (Exception e) {
                log.debug("TCP 日志写入失败", e);
            }
        }
    }

    /** 渲染推送模板并写入当前连接 */
    @SuppressWarnings("unchecked")
    private void writePush(ChannelHandlerContext ctx, Object template) {
        try {
            JsonNode node = JsonUtils.mapper().valueToTree(template);
            RenderedResponse rr = engine.renderForEval(node, new HashMap<>(), runtime.getListener().getProjectId());
            Object body = rr.getBody();
            Map<String, Object> fields = (body instanceof Map) ? (Map<String, Object>) body : new LinkedHashMap<>();
            byte[] bytes = runtime.getParser().encode(fields, runtime.getFormatConfig());
            if (ctx.channel().isActive()) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes));
            }
        } catch (Exception e) {
            log.warn("推送渲染失败: {}", e.getMessage());
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
