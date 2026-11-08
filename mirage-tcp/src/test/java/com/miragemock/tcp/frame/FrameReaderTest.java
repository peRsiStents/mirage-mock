package com.miragemock.tcp.frame;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FrameReader} 单测：验证「按 frame_config 精确读一帧」的语义。
 *
 * <p>这是 TCP 测试用例链路的核心：读错长度头 → 拿到错位正文 → 断言全失败；
 * 读成「读到超时」→ 每条用例白等 10s。故此处钉死各类帧的读取边界。
 */
class FrameReaderTest {

    /** 起一个一次性 TCP 服务端：接收连接后按脚本写数据（可延迟），然后可选关闭。 */
    private static class Server implements AutoCloseable {
        final ServerSocket ss;
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        volatile Socket client;

        Server(byte[] toSend, boolean closeAfter, long delayMs) throws IOException {
            ss = new ServerSocket(0);
            pool.submit(() -> {
                try (Socket s = ss.accept()) {
                    client = s;
                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                    if (toSend != null && toSend.length > 0) {
                        OutputStream out = s.getOutputStream();
                        out.write(toSend);
                        out.flush();
                    }
                    if (closeAfter) {
                        s.close();
                    } else {
                        // 不关闭：保持连接，验证「读到一帧即返回」而不是等超时
                        Thread.sleep(5000);
                    }
                } catch (Exception ignored) {
                    // 服务端异常不影响断言
                }
            });
        }

        int port() {
            return ss.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            pool.shutdownNow();
            ss.close();
        }
    }

    private static byte[] bytes(int... vals) {
        byte[] b = new byte[vals.length];
        for (int i = 0; i < vals.length; i++) {
            b[i] = (byte) vals[i];
        }
        return b;
    }

    @Test
    void lengthFieldReadsExactlyOneFrame() throws Exception {
        // 4 字节大端长度头，声明 3 字节正文
        byte[] payload = bytes(0, 0, 0, 3, 'a', 'b', 'c');
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"length_field\",\"lenBytes\":4}", 3000);
            assertArrayEquals(payload, got);
        }
    }

    @Test
    void lengthFieldDoesNotWaitForTimeout() throws Exception {
        // 服务端写完一帧后保持连接不关闭：若实现是「读到超时」，这里会耗满 timeout
        byte[] payload = bytes(0, 0, 0, 2, 'h', 'i');
        long t0 = System.currentTimeMillis();
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"length_field\",\"lenBytes\":4}", 10000);
            long cost = System.currentTimeMillis() - t0;
            assertArrayEquals(payload, got);
            assertTrue(cost < 3000, "读到一帧后应立即返回，实际耗时 " + cost + "ms（疑似退化为读超时）");
        }
    }

    @Test
    void lengthFieldWithOffsetReadsPrefixToo() throws Exception {
        // offset=2：帧起始有 2 字节前缀，随后 4 字节长度头
        byte[] payload = bytes(0xAA, 0xBB, 0, 0, 0, 2, 'x', 'y');
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"length_field\",\"lenBytes\":4,\"offset\":2}", 3000);
            assertArrayEquals(payload, got);
        }
    }

    @Test
    void lengthFieldWithAdjustmentAddsToBodyLength() throws Exception {
        // adjustment=2：帧总长 = 4(头) + declared + 2，故声明 1 时实际正文 3 字节
        byte[] payload = bytes(0, 0, 0, 1, 'a', 'b', 'c');
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"length_field\",\"lenBytes\":4,\"adjustment\":2}", 3000);
            assertArrayEquals(payload, got);
        }
    }

    @Test
    void lengthFieldTwoBytesBigEndian() throws Exception {
        byte[] payload = bytes(0, 3, 'a', 'b', 'c');
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"length_field\",\"lenBytes\":2}", 3000);
            assertArrayEquals(payload, got);
        }
    }

    @Test
    void fixedReadsExactLength() throws Exception {
        byte[] payload = bytes('1', '2', '3', '4', '5');
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"fixed\",\"length\":5}", 3000);
            assertArrayEquals(payload, got);
        }
    }

    @Test
    void delimiterReadsThroughSeparator() throws Exception {
        // 分隔符帧：一帧 = 到「第一个」分隔符为止（含分隔符）
        byte[] payload = "line1\nline2\n".getBytes(StandardCharsets.UTF_8);
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"delimiter\",\"value\":\"\\n\"}", 3000);
            assertArrayEquals("line1\n".getBytes(StandardCharsets.UTF_8), got);
            assertEquals(6, got.length);
        }
    }

    @Test
    void closeEndReadsUntilPeerCloses() throws Exception {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        try (Server srv = new Server(payload, true, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"close_end\"}", 3000);
            assertArrayEquals(payload, got);
        }
    }

    @Test
    void closeEndUsesIdleWindowWhenPeerKeepsConnection() throws Exception {
        // 对端不关闭：应在空闲窗口（300ms）后判定本帧结束，而不是等满 timeout
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        long t0 = System.currentTimeMillis();
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "{\"type\":\"close_end\"}", 10000);
            long cost = System.currentTimeMillis() - t0;
            assertArrayEquals(payload, got);
            assertTrue(cost < 3000, "close_end 应按空闲窗口结束，实际耗时 " + cost + "ms");
        }
    }

    @Test
    void incompleteFrameReportsProgress() {
        // 服务端声明 5 字节但只发 2 字节后关闭：应报「不完整 + 已读字节数」
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (ServerSocket ss = new ServerSocket(0)) {
            Future<?> f = pool.submit(() -> {
                try (Socket s = ss.accept()) {
                    s.getOutputStream().write(bytes(0, 0, 0, 5, 'a', 'b'));
                    s.getOutputStream().flush();
                    Thread.sleep(200);
                } catch (Exception ignored) {
                }
            });
            try (Socket sock = new Socket("127.0.0.1", ss.getLocalPort())) {
                IOException e = assertThrows(IOException.class,
                        () -> FrameReader.readFrame(sock, "{\"type\":\"length_field\",\"lenBytes\":4}", 3000));
                assertTrue(e.getMessage().contains("2/5") || e.getMessage().contains("已读 2/5"),
                        "错误信息应说明已读字节数，实际: " + e.getMessage());
            } finally {
                f.get();
                pool.shutdownNow();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void timeoutIsReportedDistinctly() {
        // 服务端接受连接但不发任何数据：应报「超时」而不是「连接已关闭」
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (ServerSocket ss = new ServerSocket(0)) {
            Future<?> f = pool.submit(() -> {
                try (Socket s = ss.accept()) {
                    Thread.sleep(2000);
                } catch (Exception ignored) {
                }
            });
            try (Socket sock = new Socket("127.0.0.1", ss.getLocalPort())) {
                IOException e = assertThrows(IOException.class,
                        () -> FrameReader.readFrame(sock, "{\"type\":\"length_field\",\"lenBytes\":4}", 500));
                assertTrue(e.getMessage().contains("超时"), "应提示超时，实际: " + e.getMessage());
            } finally {
                f.get();
                pool.shutdownNow();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void unknownFrameTypeIsRejected() {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (ServerSocket ss = new ServerSocket(0)) {
            Future<?> f = pool.submit(() -> {
                try (Socket s = ss.accept()) {
                    Thread.sleep(500);
                } catch (Exception ignored) {
                }
            });
            try (Socket sock = new Socket("127.0.0.1", ss.getLocalPort())) {
                IOException e = assertThrows(IOException.class,
                        () -> FrameReader.readFrame(sock, "{\"type\":\"bogus\"}", 1000));
                assertTrue(e.getMessage().contains("bogus"), "应报出未知帧类型，实际: " + e.getMessage());
            } finally {
                f.get();
                pool.shutdownNow();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void emptyConfigUsesDefaultLengthField() throws Exception {
        byte[] payload = bytes(0, 0, 0, 1, 'z');
        try (Server srv = new Server(payload, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, "", 3000);
            assertArrayEquals(payload, got);
            // 默认帧 = 4 字节大端长度头 + 1 字节正文 = 5 字节
            assertEquals(5, got.length);
        }
    }

    @Test
    void readerAndEncoderAgreeOnFraming() throws Exception {
        // 与服务端编码器互操作：encode 出的帧，reader 能原样读回
        String cfg = "{\"type\":\"length_field\",\"lenBytes\":4,\"offset\":2,\"adjustment\":0}";
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] framed = FrameEncoder.encode(payload, cfg);
        try (Server srv = new Server(framed, false, 0);
             Socket sock = new Socket("127.0.0.1", srv.port())) {
            byte[] got = FrameReader.readFrame(sock, cfg, 3000);
            assertArrayEquals(framed, got);
        }
    }
}
