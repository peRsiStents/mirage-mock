package com.miragemock.tcp;

import com.miragemock.tcp.frame.FrameDecoderFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * TCP 管道初始化：帧切分解码器（close_end 不加）+ Mock 处理器。
 */
public class TcpServerInitializer extends ChannelInitializer<SocketChannel> {

    private final TcpListenerRuntime runtime;
    private final TcpMockHandler handler;

    public TcpServerInitializer(TcpListenerRuntime runtime, TcpMockHandler handler) {
        this.runtime = runtime;
        this.handler = handler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        ChannelHandler decoder = FrameDecoderFactory.create(runtime.getListener().getFrameConfig());
        if (decoder != null) {
            pipeline.addLast("frame", decoder);
        }
        pipeline.addLast("mock", handler);
    }
}
