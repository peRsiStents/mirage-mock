package com.miragemock.tcp.frame;

import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.crypto.Codec;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 帧编码：把请求 body 字节按 frame_config 加帧，是 {@link FrameDecoderFactory}（服务端解码）的逆运算，
 * 供 TCP 测试客户端构造请求帧。
 *
 * <p>length_field：前置 lenBytes 字节大端长度头（offset=0，覆盖绝大多数配置，含 demo 的 4 字节大端）；
 * delimiter：追加分隔符；fixed：定长（不足补 0x00、超长截断）；close_end：原样。
 */
public final class FrameEncoder {

    private FrameEncoder() {
    }

    public static byte[] encode(byte[] payload, String frameConfigText) {
        if (payload == null) {
            payload = new byte[0];
        }
        if (frameConfigText == null || frameConfigText.trim().isEmpty()) {
            return withLengthHeader(payload, 4);
        }
        Map<String, Object> cfg = JsonUtils.parseMap(frameConfigText);
        String type = str(cfg.get("type"), "length_field");
        switch (type) {
            case "delimiter":
                return concat(payload, parseDelimiter(str(cfg.get("value"), "\n")));
            case "fixed":
                return fixLength(payload, intOf(cfg.get("length"), payload.length));
            case "close_end":
                return payload;
            case "length_field":
            default:
                return withLengthHeader(payload, intOf(cfg.get("lenBytes"), 4));
        }
    }

    private static byte[] withLengthHeader(byte[] payload, int lenBytes) {
        if (lenBytes <= 0) {
            return payload;
        }
        byte[] header = new byte[lenBytes];
        long len = payload.length;
        for (int i = lenBytes - 1; i >= 0; i--) {
            header[i] = (byte) (len & 0xFF);
            len >>>= 8;
        }
        return concat(header, payload);
    }

    private static byte[] fixLength(byte[] payload, int length) {
        if (length <= 0) {
            return payload;
        }
        byte[] out = new byte[length];
        System.arraycopy(payload, 0, out, 0, Math.min(payload.length, length));
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] parseDelimiter(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return Codec.hexDecode(value.substring(2));
        }
        return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").getBytes(StandardCharsets.UTF_8);
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
