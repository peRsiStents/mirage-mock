package com.miragemock.tcp.frame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 帧编解码矩阵测试：encode / strip 互逆性 + 各帧类型行为。
 */
class FrameEncoderTest {

    private static final String LEN4 = "{\"type\":\"length_field\",\"lenBytes\":4}";

    @Test
    @DisplayName("length_field：encode 加 4 字节大端长度头，strip 还原 payload")
    void lengthFieldRoundTrip() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] framed = FrameEncoder.encode(payload, LEN4);
        assertEquals(9, framed.length);
        assertEquals('h', framed[4]);
        assertArrayEquals(payload, FrameEncoder.strip(framed, LEN4));
    }

    @Test
    @DisplayName("length_field：lenBytes=2 时长度头为 2 字节大端")
    void lengthFieldTwoBytes() {
        byte[] payload = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] framed = FrameEncoder.encode(payload, "{\"type\":\"length_field\",\"lenBytes\":2}");
        assertEquals(5, framed.length);
        assertEquals(0, framed[0] & 0xFF);
        assertEquals(3, framed[1] & 0xFF);
        assertArrayEquals(payload, FrameEncoder.strip(framed, "{\"type\":\"length_field\",\"lenBytes\":2}"));
    }

    @Test
    @DisplayName("length_field：offset>0 时 encode 必须补出前缀字节，否则与服务端解码器布局不一致")
    void lengthFieldWithOffset() {
        String cfg = "{\"type\":\"length_field\",\"lenBytes\":4,\"offset\":2}";
        byte[] payload = "body".getBytes(StandardCharsets.UTF_8);
        byte[] framed = FrameEncoder.encode(payload, cfg);
        // 2 字节前缀 + 4 字节长度头 + 4 字节正文
        assertEquals(10, framed.length);
        // 长度头位于 [offset, offset+lenBytes)，大端存放
        assertEquals(4, framed[5] & 0xFF);
        assertArrayEquals(payload, FrameEncoder.strip(framed, cfg));
    }

    @Test
    @DisplayName("length_field：prefix_hex 指定前缀内容（魔数）")
    void lengthFieldWithPrefixHex() {
        String cfg = "{\"type\":\"length_field\",\"lenBytes\":4,\"offset\":2,\"prefix_hex\":\"AABB\"}";
        byte[] framed = FrameEncoder.encode("xy".getBytes(StandardCharsets.UTF_8), cfg);
        assertEquals((byte) 0xAA, framed[0]);
        assertEquals((byte) 0xBB, framed[1]);
        // 长度头紧跟前缀，声明长度为 2
        assertEquals(2, framed[5] & 0xFF);
    }

    @Test
    @DisplayName("length_field：adjustment 参与声明长度计算（Netty 语义：声明长度 = 正文长度 - adjustment）")
    void lengthFieldWithAdjustment() {
        String cfg = "{\"type\":\"length_field\",\"lenBytes\":4,\"adjustment\":2}";
        byte[] framed = FrameEncoder.encode("abc".getBytes(StandardCharsets.UTF_8), cfg);
        // 声明长度 = 3 - 2 = 1
        assertEquals(1, framed[3] & 0xFF);
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8),
                FrameEncoder.strip(framed, cfg));
    }

    @Test
    @DisplayName("delimiter：encode 追加分隔符，strip 去掉尾部分隔符")
    void delimiterRoundTrip() {
        String cfg = "{\"type\":\"delimiter\",\"value\":\"\\n\"}";
        byte[] framed = FrameEncoder.encode("data".getBytes(StandardCharsets.UTF_8), cfg);
        assertEquals(5, framed.length);
        assertEquals('\n', framed[4]);
        assertArrayEquals("data".getBytes(StandardCharsets.UTF_8), FrameEncoder.strip(framed, cfg));
    }

    @Test
    @DisplayName("delimiter：hex 分隔符 0x0D0A")
    void delimiterHex() {
        String cfg = "{\"type\":\"delimiter\",\"value\":\"0x0D0A\"}";
        byte[] framed = FrameEncoder.encode("z".getBytes(StandardCharsets.UTF_8), cfg);
        assertEquals(3, framed.length);
        assertEquals((byte) 0x0D, framed[1]);
        assertEquals((byte) 0x0A, framed[2]);
    }

    @Test
    @DisplayName("fixed：定长补 0x00，strip 原样返回")
    void fixedFrame() {
        String cfg = "{\"type\":\"fixed\",\"length\":6}";
        byte[] framed = FrameEncoder.encode("ab".getBytes(StandardCharsets.UTF_8), cfg);
        assertEquals(6, framed.length);
        assertEquals(0, framed[2]);
        assertArrayEquals(framed, FrameEncoder.strip(framed, cfg));
    }

    @Test
    @DisplayName("close_end：原样透传")
    void closeEnd() {
        String cfg = "{\"type\":\"close_end\"}";
        byte[] payload = "raw".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(payload, FrameEncoder.encode(payload, cfg));
        assertArrayEquals(payload, FrameEncoder.strip(payload, cfg));
    }

    @Test
    @DisplayName("空 frame_config：默认 4 字节大端长度头")
    void defaultConfig() {
        byte[] payload = "q".getBytes(StandardCharsets.UTF_8);
        byte[] framed = FrameEncoder.encode(payload, null);
        assertEquals(5, framed.length);
        assertArrayEquals(payload, FrameEncoder.strip(framed, ""));
    }
}
