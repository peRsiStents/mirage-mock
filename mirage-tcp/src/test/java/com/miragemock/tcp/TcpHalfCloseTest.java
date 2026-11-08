package com.miragemock.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 半关闭（half-close）行为测试。
 *
 * <p>close_end 帧的报文边界是「对端关闭输入（收到 FIN）」。Netty 默认 allowHalfClosure=false，
 * 收到 FIN 会直接关闭通道：{@code ChannelInputShutdownEvent} 不触发，响应也写不出去，
 * 客户端只能收到 0 字节。故子通道必须设置 ALLOW_HALF_CLOSURE=true。
 */
class TcpHalfCloseTest {

    @Test
    @DisplayName("close_end：对端半关闭（shutdownOutput）后服务端仍能回写响应")
    void respondsAfterPeerHalfClose() throws Exception {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup(1);
        Channel server = null;
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                private final StringBuilder acc = new StringBuilder();

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    acc.append(buf.toString(StandardCharsets.UTF_8));
                                    buf.release();
                                }

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof ChannelInputShutdownEvent) {
                                        ctx.writeAndFlush(Unpooled.copiedBuffer("PONG:" + acc, StandardCharsets.UTF_8));
                                    } else {
                                        ctx.fireUserEventTriggered(evt);
                                    }
                                }
                            });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128);
            TcpServerManager.configureChildOptions(bootstrap);
            server = bootstrap.bind(0).sync().channel();
            int port = ((InetSocketAddress) server.localAddress()).getPort();

            try (Socket sock = new Socket("127.0.0.1", port)) {
                sock.setSoTimeout(5000);
                sock.getOutputStream().write("PING".getBytes(StandardCharsets.UTF_8));
                sock.getOutputStream().flush();
                // 半关闭：只关写方向，读方向留给服务端回写
                sock.shutdownOutput();
                byte[] buf = new byte[64];
                int n = sock.getInputStream().read(buf);
                assertEquals("PONG:PING", new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8));
            }
        } finally {
            if (server != null) {
                server.close().sync();
            }
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
