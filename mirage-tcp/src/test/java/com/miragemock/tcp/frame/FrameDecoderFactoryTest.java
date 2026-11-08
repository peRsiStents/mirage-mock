package com.miragemock.tcp.frame;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 帧切分解码器测试：验证服务端解码与 {@link FrameEncoder} 的编码互相对齐。
 */
class FrameDecoderFactoryTest {

    private static byte[] read(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    @Test
    @DisplayName("length_field：4 字节大端长度头，切出正确 payload")
    void lengthField() {
        EmbeddedChannel ch = new EmbeddedChannel(FrameDecoderFactory.create(
                "{\"type\":\"length_field\",\"lenBytes\":4}"));
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        ch.writeInbound(Unpooled.wrappedBuffer(FrameEncoder.encode(payload,
                "{\"type\":\"length_field\",\"lenBytes\":4}")));
        ByteBuf out = ch.readInbound();
        assertNotNull(out);
        assertEquals("hello", new String(read(out), StandardCharsets.UTF_8));
        out.release();
        assertFalse(ch.finish());
    }

    @Test
    @DisplayName("length_field：offset>0 + prefix_hex 时前缀被剥离，正文完整")
    void lengthFieldWithOffset() {
        String cfg = "{\"type\":\"length_field\",\"lenBytes\":4,\"offset\":2,\"prefix_hex\":\"AABB\"}";
        EmbeddedChannel ch = new EmbeddedChannel(FrameDecoderFactory.create(cfg));
        byte[] payload = "body".getBytes(StandardCharsets.UTF_8);
        ch.writeInbound(Unpooled.wrappedBuffer(FrameEncoder.encode(payload, cfg)));
        ByteBuf out = ch.readInbound();
        assertNotNull(out);
        assertEquals("body", new String(read(out), StandardCharsets.UTF_8));
        out.release();
        assertFalse(ch.finish());
    }

    @Test
    @DisplayName("length_field：adjustment 非 0 时仍能对齐")
    void lengthFieldWithAdjustment() {
        String cfg = "{\"type\":\"length_field\",\"lenBytes\":4,\"adjustment\":2}";
        EmbeddedChannel ch = new EmbeddedChannel(FrameDecoderFactory.create(cfg));
        byte[] payload = "abc".getBytes(StandardCharsets.UTF_8);
        ch.writeInbound(Unpooled.wrappedBuffer(FrameEncoder.encode(payload, cfg)));
        ByteBuf out = ch.readInbound();
        assertNotNull(out);
        assertEquals("abc", new String(read(out), StandardCharsets.UTF_8));
        out.release();
        assertFalse(ch.finish());
    }

    @Test
    @DisplayName("delimiter：按分隔符切分")
    void delimiter() {
        String cfg = "{\"type\":\"delimiter\",\"value\":\"\\n\"}";
        EmbeddedChannel ch = new EmbeddedChannel(FrameDecoderFactory.create(cfg));
        ch.writeInbound(Unpooled.wrappedBuffer("a\nb\n".getBytes(StandardCharsets.UTF_8)));
        ByteBuf first = ch.readInbound();
        assertNotNull(first);
        assertEquals("a", new String(read(first), StandardCharsets.UTF_8));
        first.release();
        ByteBuf second = ch.readInbound();
        assertNotNull(second);
        assertEquals("b", new String(read(second), StandardCharsets.UTF_8));
        second.release();
        assertFalse(ch.finish());
    }

    @Test
    @DisplayName("fixed：定长切分")
    void fixed() {
        EmbeddedChannel ch = new EmbeddedChannel(FrameDecoderFactory.create(
                "{\"type\":\"fixed\",\"length\":4}"));
        ch.writeInbound(Unpooled.wrappedBuffer("abcdefgh".getBytes(StandardCharsets.UTF_8)));
        ByteBuf first = ch.readInbound();
        assertNotNull(first);
        assertEquals(4, first.readableBytes());
        first.release();
        ByteBuf second = ch.readInbound();
        assertNotNull(second);
        assertEquals(4, second.readableBytes());
        second.release();
        assertFalse(ch.finish());
    }

    @Test
    @DisplayName("close_end：返回 null（由处理器按字节累积）")
    void closeEndHasNoDecoder() {
        assertNull(FrameDecoderFactory.create("{\"type\":\"close_end\"}"));
        // 空配置走默认 length_field 解码器
        assertNotNull(FrameDecoderFactory.create(null));
    }

    @Test
    @DisplayName("未知帧类型：直接报错，不静默回退")
    void unknownTypeThrows() {
        try {
            FrameDecoderFactory.create("{\"type\":\"bogus\"}");
            org.junit.jupiter.api.Assertions.fail("应当抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("bogus"));
        }
    }

    @Test
    @DisplayName("解码器类型可创建（不为 null）")
    void decoderCreated() {
        ChannelHandler h = FrameDecoderFactory.create("{\"type\":\"length_field\"}");
        assertNotNull(h);
    }
}
