package com.miragemock.tcp;

import com.miragemock.tcp.frame.FrameDecoderFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * TCP 管道初始化：帧切分解码器（close_end 不加）+ 空闲检测 + Mock 处理器。
 *
 * <p>帧配置在监听器启动时已解析校验一次（{@link TcpListenerRuntime}），
 * 这里不再每连接重复解析 JSON。
 */
public class TcpServerInitializer extends ChannelInitializer<SocketChannel> {

    private final TcpListenerRuntime runtime;
    private final TcpMockHandler handler;
    private final int readerIdleSeconds;

    public TcpServerInitializer(TcpListenerRuntime runtime, TcpMockHandler handler) {
        this(runtime, handler, 0);
    }

    public TcpServerInitializer(TcpListenerRuntime runtime, TcpMockHandler handler, int readerIdleSeconds) {
        this.runtime = runtime;
        this.handler = handler;
        this.readerIdleSeconds = readerIdleSeconds;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        ChannelHandler decoder = FrameDecoderFactory.create(runtime.getListener().getFrameConfig());
        if (decoder != null) {
            pipeline.addLast("frame", decoder);
        }
        // 空闲回收：读空闲超时触发 IdleStateEvent，由 handler 关闭连接
        if (readerIdleSeconds > 0) {
            pipeline.addLast("idle", new IdleStateHandler(readerIdleSeconds, 0, 0, TimeUnit.SECONDS));
        }
        pipeline.addLast("mock", handler);
    }
}
