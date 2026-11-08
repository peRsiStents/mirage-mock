package com.miragemock.tcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.render.RenderedResponse;
import com.miragemock.tcp.codec.MessageParserRegistry;
import com.miragemock.tcp.frame.FrameEncoder;
import com.miragemock.core.log.RequestLogSink;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TCP 服务管理器：启用的监听器绑定 Netty 端口；管理连接通道与定时推送。
 *
 * <p>线程模型：所有监听器**共享**一组 boss/worker EventLoopGroup
 * （原先每个监听器各建一组，10 个监听器 ≈ 170 个线程）；
 * 单个监听器停止只关自己的 serverChannel 与连接，不影响共享线程池。
 */
@Component
public class TcpServerManager {

    private static final Logger log = LoggerFactory.getLogger(TcpServerManager.class);

    /** 关闭等待上限（秒） */
    private static final long CLOSE_TIMEOUT_SECONDS = 3L;

    private final MockEngine engine;
    private final MessageParserRegistry parserRegistry;
    private final RequestLogSink sink;
    private final TaskScheduler taskScheduler;

    /** 单监听器最大连接数 */
    private final int maxConnectionsPerListener;
    /** 读空闲超时秒数（0 = 不启用空闲回收） */
    private final int readerIdleSeconds;

    private final Map<Long, ServerEntry> servers = new ConcurrentHashMap<>();

    /** 全局共享的 EventLoopGroup */
    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;

    @Autowired
    public TcpServerManager(MockEngine engine, MessageParserRegistry parserRegistry,
                            RequestLogSink sink, @Qualifier("tcpTaskScheduler") TaskScheduler taskScheduler,
                            @Value("${mirage.tcp.max-connections-per-listener:1024}") int maxConnectionsPerListener,
                            @Value("${mirage.tcp.reader-idle-seconds:600}") int readerIdleSeconds) {
        this.engine = engine;
        this.parserRegistry = parserRegistry;
        this.sink = sink;
        this.taskScheduler = taskScheduler;
        this.maxConnectionsPerListener = maxConnectionsPerListener;
        this.readerIdleSeconds = readerIdleSeconds;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    /**
     * 启动监听器。
     *
     * @throws IllegalStateException 端口占用 / 帧配置非法等启动失败原因会抛出，
     *                               由调用方（管理端）反馈给用户，
     *                               不能静默吞掉（否则 UI 显示「已启用」但端口从未监听）
     */
    public synchronized void start(TcpListener listener) {
        Long id = listener.getId();
        if (servers.containsKey(id)) {
            log.warn("监听器 {} 已在运行，跳过", listener.getName());
            return;
        }
        // 帧/格式配置在启动前校验一次：避免配置错误只能在「每条新连接」上逐次暴露
        TcpListenerRuntime runtime = new TcpListenerRuntime(listener, parserRegistry);
        runtime.validate();
        TcpMockHandler handler = new TcpMockHandler(runtime, engine, sink, this);
        TcpServerInitializer initializer = new TcpServerInitializer(runtime, handler, readerIdleSeconds);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(initializer)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_REUSEADDR, true);
            configureChildOptions(bootstrap);
            Channel serverChannel = bootstrap.bind(listener.getPort()).sync().channel();

            ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            ServerEntry entry = new ServerEntry(runtime, handler, serverChannel, channels);
            servers.put(id, entry);
            schedulePush(entry);
            log.info("TCP 监听器 [{}] 已启动，端口 {}", listener.getName(), listener.getPort());
        } catch (Exception e) {
            log.error("TCP 监听器 [{}] 启动失败，端口 {}", listener.getName(), listener.getPort(), e);
            throw new IllegalStateException("监听器 '" + listener.getName() + "' 启动失败（端口 "
                    + listener.getPort() + "）：" + rootMessage(e), e);
        }
    }

    /**
     * 子通道选项。
     *
     * <p><b>ALLOW_HALF_CLOSURE 必须为 true</b>：close_end 帧的报文边界就是「对端关闭输入（收到 FIN）」。
     * Netty 默认 allowHalfClosure=false，收到 FIN 会直接关闭通道，
     * 于是 {@code ChannelInputShutdownEvent} 不触发、响应也写不出去（客户端只拿到 0 字节）。
     *
     * <p>包级可见，便于单元测试锁定该选项不被误删。
     */
    static ServerBootstrap configureChildOptions(ServerBootstrap bootstrap) {
        return bootstrap
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.ALLOW_HALF_CLOSURE, true);
    }

    public synchronized void stop(Long id) {
        ServerEntry entry = servers.remove(id);
        if (entry == null) {
            return;
        }
        for (ScheduledFuture<?> f : entry.pushTasks) {
            f.cancel(false);
        }
        // 关闭必须等待完成：否则紧接着重绑同端口会因旧 socket 尚未释放而 BindException
        entry.channels.close().awaitUninterruptibly(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            entry.serverChannel.close().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("TCP 监听器 id={} 已停止", id);
    }

    public synchronized void restart(TcpListener listener) {
        stop(listener.getId());
        if (listener.getStatus() != null && listener.getStatus() == 1) {
            start(listener);
        }
    }

    public void startAll(Collection<TcpListener> listeners) {
        if (listeners == null) {
            return;
        }
        for (TcpListener l : listeners) {
            if (l.getStatus() != null && l.getStatus() == 1) {
                try {
                    start(l);
                } catch (Exception e) {
                    // 单个监听器启动失败不影响其他监听器
                    log.error("监听器 [{}] 启动失败: {}", l.getName(), e.getMessage());
                }
            }
        }
    }

    public void stopAll() {
        for (Long id : new java.util.ArrayList<>(servers.keySet())) {
            stop(id);
        }
    }

    public boolean isRunning(Long id) {
        return servers.containsKey(id);
    }

    /**
     * 注册连接；超过单监听器连接上限则直接关闭新连接，
     * 避免连接无限囤积（配合 exceptionCaught 关闭僵尸连接）。
     *
     * @return true 表示连接被接受
     */
    public boolean registerChannel(Long listenerId, Channel channel) {
        ServerEntry entry = servers.get(listenerId);
        if (entry == null) {
            return false;
        }
        if (maxConnectionsPerListener > 0 && entry.channels.size() >= maxConnectionsPerListener) {
            log.warn("监听器 {} 连接数已达上限 {}，拒绝新连接",
                    entry.runtime.getListener().getName(), maxConnectionsPerListener);
            channel.close();
            return false;
        }
        entry.channels.add(channel);
        return true;
    }

    public void unregisterChannel(Long listenerId, Channel channel) {
        // DefaultChannelGroup 在连接关闭时自动移除，无需手动处理
    }

    public int getMaxConnectionsPerListener() {
        return maxConnectionsPerListener;
    }

    public int getReaderIdleSeconds() {
        return readerIdleSeconds;
    }

    private void schedulePush(ServerEntry entry) {
        List<Map<String, Object>> schedules = entry.runtime.getSchedule();
        Long projectId = entry.runtime.getListener().getProjectId();
        for (Map<String, Object> s : schedules) {
            String cron = s.get("cron") == null ? null : s.get("cron").toString();
            Object tpl = s.get("template");
            if (cron == null || tpl == null) {
                continue;
            }
            try {
                CronTrigger trigger = new CronTrigger(cron);
                ScheduledFuture<?> future = taskScheduler.schedule(() -> broadcastPush(entry, tpl, projectId), trigger);
                entry.pushTasks.add(future);
                log.info("监听器 [{}] 注册定时推送 cron={}", entry.runtime.getListener().getName(), cron);
            } catch (Exception e) {
                log.warn("定时推送注册失败 cron={}: {}", cron, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void broadcastPush(ServerEntry entry, Object template, Long projectId) {
        try {
            JsonNode node = JsonUtils.mapper().valueToTree(template);
            RenderedResponse rr = engine.renderForEval(node, new LinkedHashMap<>(), projectId);
            Object body = rr.getBody();
            Map<String, Object> fields = (body instanceof Map) ? (Map<String, Object>) body : new LinkedHashMap<>();
            byte[] bytes = entry.runtime.getParser().encode(fields, entry.runtime.getFormatConfig());
            // 推送同样要按 frame_config 加帧：否则 length_field 协议客户端收到的裸 payload
            // 会让下一帧读错位（与正常响应路径保持一致）
            bytes = FrameEncoder.encode(bytes, entry.runtime.getListener().getFrameConfig());
            if (bytes.length > 0) {
                entry.channels.writeAndFlush(Unpooled.wrappedBuffer(bytes));
            }
        } catch (Exception e) {
            log.warn("定时推送失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        stopAll();
        bossGroup.shutdownGracefully(0, CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        workerGroup.shutdownGracefully(0, CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        String m = t.getMessage();
        int i = 0;
        while (c.getCause() != null && i++ < 5) {
            c = c.getCause();
            if (c.getMessage() != null) {
                m = c.getMessage();
            }
        }
        return m == null ? c.getClass().getSimpleName() : m;
    }

    private static final class ServerEntry {
        final TcpListenerRuntime runtime;
        final TcpMockHandler handler;
        final Channel serverChannel;
        final ChannelGroup channels;
        final List<ScheduledFuture<?>> pushTasks = new java.util.concurrent.CopyOnWriteArrayList<>();

        ServerEntry(TcpListenerRuntime runtime, TcpMockHandler handler, Channel serverChannel,
                    ChannelGroup channels) {
            this.runtime = runtime;
            this.handler = handler;
            this.serverChannel = serverChannel;
            this.channels = channels;
        }
    }
}
