package com.miragemock.tcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.render.RenderedResponse;
import com.miragemock.tcp.codec.MessageParserRegistry;
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

/**
 * TCP 服务管理器：启用的监听器绑定 Netty 端口；管理连接通道与定时推送。
 */
@Component
public class TcpServerManager {

    private static final Logger log = LoggerFactory.getLogger(TcpServerManager.class);

    private final MockEngine engine;
    private final MessageParserRegistry parserRegistry;
    private final RequestLogSink sink;
    private final TaskScheduler taskScheduler;

    private final Map<Long, ServerEntry> servers = new ConcurrentHashMap<>();

    @Autowired
    public TcpServerManager(MockEngine engine, MessageParserRegistry parserRegistry,
                            RequestLogSink sink, @Qualifier("tcpTaskScheduler") TaskScheduler taskScheduler) {
        this.engine = engine;
        this.parserRegistry = parserRegistry;
        this.sink = sink;
        this.taskScheduler = taskScheduler;
    }

    public synchronized void start(TcpListener listener) {
        Long id = listener.getId();
        if (servers.containsKey(id)) {
            log.warn("监听器 {} 已在运行，跳过", listener.getName());
            return;
        }
        TcpListenerRuntime runtime = new TcpListenerRuntime(listener, parserRegistry);
        TcpMockHandler handler = new TcpMockHandler(runtime, engine, sink, this);
        TcpServerInitializer initializer = new TcpServerInitializer(runtime, handler);

        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(initializer)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true);
            Channel serverChannel = bootstrap.bind(listener.getPort()).sync().channel();

            ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            ServerEntry entry = new ServerEntry(runtime, handler, serverChannel, boss, worker, channels);
            servers.put(id, entry);
            schedulePush(entry);
            log.info("TCP 监听器 [{}] 已启动，端口 {}", listener.getName(), listener.getPort());
        } catch (Exception e) {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
            log.error("TCP 监听器 [{}] 启动失败，端口 {}", listener.getName(), listener.getPort(), e);
        }
    }

    public synchronized void stop(Long id) {
        ServerEntry entry = servers.remove(id);
        if (entry == null) {
            return;
        }
        for (ScheduledFuture<?> f : entry.pushTasks) {
            f.cancel(false);
        }
        entry.channels.close();
        entry.serverChannel.close();
        entry.boss.shutdownGracefully();
        entry.worker.shutdownGracefully();
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
                start(l);
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

    public void registerChannel(Long listenerId, Channel channel) {
        ServerEntry entry = servers.get(listenerId);
        if (entry != null) {
            entry.channels.add(channel);
        }
    }

    public void unregisterChannel(Long listenerId, Channel channel) {
        // DefaultChannelGroup 在连接关闭时自动移除，无需手动处理
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
    }

    private static final class ServerEntry {
        final TcpListenerRuntime runtime;
        final TcpMockHandler handler;
        final Channel serverChannel;
        final NioEventLoopGroup boss;
        final NioEventLoopGroup worker;
        final ChannelGroup channels;
        final List<ScheduledFuture<?>> pushTasks = new java.util.concurrent.CopyOnWriteArrayList<>();

        ServerEntry(TcpListenerRuntime runtime, TcpMockHandler handler, Channel serverChannel,
                    NioEventLoopGroup boss, NioEventLoopGroup worker, ChannelGroup channels) {
            this.runtime = runtime;
            this.handler = handler;
            this.serverChannel = serverChannel;
            this.boss = boss;
            this.worker = worker;
            this.channels = channels;
        }
    }
}
