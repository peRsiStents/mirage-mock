package com.miragemock.tcp.frame;

import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.crypto.Codec;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 帧切分工厂：按 frame_config 构造 Netty 解码器。
 *
 * <p>支持 4 种模式：length_field / delimiter / fixed / close_end（close_end 返回 null，由处理器按字节累积处理）。
 */
public final class FrameDecoderFactory {

    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;

    private FrameDecoderFactory() {
    }

    /**
     * @param frameConfigText frame_config JSON 文本
     * @return 解码器；close_end 返回 null
     */
    public static ChannelHandler create(String frameConfigText) {
        if (frameConfigText == null || frameConfigText.trim().isEmpty()) {
            // 默认按长度头 4 字节大端
            return new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4);
        }
        Map<String, Object> cfg = JsonUtils.parseMap(frameConfigText);
        String type = str(cfg.get("type"), "length_field");
        switch (type) {
            case "length_field": {
                int lenBytes = intOf(cfg.get("lenBytes"), 4);
                int offset = intOf(cfg.get("offset"), 0);
                int adjustment = intOf(cfg.get("adjustment"), 0);
                int strip = intOf(cfg.get("initialStrip"), lenBytes);
                // 大端（网络字节序）；小端可在此切换为带 ByteOrder 的重载
                return new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, offset, lenBytes, adjustment, strip);
            }
            case "delimiter": {
                byte[] delim = parseDelimiter(str(cfg.get("value"), "\n"));
                return new DelimiterBasedFrameDecoder(MAX_FRAME_LENGTH, true, Unpooled.wrappedBuffer(delim));
            }
            case "fixed": {
                int length = intOf(cfg.get("length"), 1);
                return new FixedLengthFrameDecoder(length);
            }
            case "close_end":
                return null;
            default:
                throw new IllegalArgumentException("未知帧切分类型: " + type);
        }
    }

    private static byte[] parseDelimiter(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return Codec.hexDecode(value.substring(2));
        }
        String unescaped = value.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
        return unescaped.getBytes(StandardCharsets.UTF_8);
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static int intOf(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
