package com.miragemock.tcp.frame;

import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.crypto.Codec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * 帧编解码：encode 给请求 body 加帧（{@link FrameDecoderFactory} 服务端解码的逆运算，供测试客户端构造请求）；
 * strip 为响应去帧（剥离 length_field 长度头 / 尾部分隔符），供测试客户端解析响应前先剥掉帧头，
 * 避免「报文前面多出帧头字节」（如长度头 0x74 被当成内容里的 't'）。
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
            default: {
                int lenBytes = intOf(cfg.get("lenBytes"), 4);
                int offset = intOf(cfg.get("offset"), 0);
                int adjustment = intOf(cfg.get("adjustment"), 0);
                // offset>0：长度头前面还有 offset 字节（前缀）。原实现直接写头部，
                // 生成的帧与服务端解码器期望的布局不一致 → 客户端与服务端互相读错位。
                byte[] prefix = buildPrefix(cfg, offset);
                return concat(prefix, withLengthHeader(payload, lenBytes, adjustment));
            }
        }
    }

    /**
     * 构造长度头之前的前缀字节。
     *
     * <p>优先用 frame_config 的 {@code prefix_hex}（如 "0A0B" 表示魔数），缺省补 0x00 占位。
     * 长度以字节计（不是 hex 字符数）。
     */
    private static byte[] buildPrefix(Map<String, Object> cfg, int offset) {
        if (offset <= 0) {
            return new byte[0];
        }
        String hex = str(cfg.get("prefix_hex"), "");
        if (hex != null && !hex.trim().isEmpty()) {
            String cleaned = hex.trim();
            if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
                cleaned = cleaned.substring(2);
            }
            byte[] decoded = Codec.hexDecode(cleaned);
            if (decoded.length > offset) {
                // 前缀比 offset 长：只保留前 offset 字节，保证布局与服务端解码器一致
                return Arrays.copyOf(decoded, offset);
            }
            byte[] padded = new byte[offset];
            System.arraycopy(decoded, 0, padded, 0, decoded.length);
            return padded;
        }
        return new byte[offset];
    }

    /**
     * 响应去帧：按 frame_config 剥离 length_field 长度头 / 尾部分隔符，返回纯 payload，供 parser 解析。
     * fixed / close_end 无帧头，原样返回。与 encode 互逆，与 FrameDecoderFactory 的切分语义一致。
     */
    public static byte[] strip(byte[] framed, String frameConfigText) {
        if (framed == null) {
            return new byte[0];
        }
        if (frameConfigText == null || frameConfigText.trim().isEmpty()) {
            return stripLengthField(framed, 4, 0, 0, 4);
        }
        Map<String, Object> cfg = JsonUtils.parseMap(frameConfigText);
        String type = str(cfg.get("type"), "length_field");
        switch (type) {
            case "delimiter":
                return stripTrailing(framed, parseDelimiter(str(cfg.get("value"), "\n")));
            case "fixed":
            case "close_end":
                return framed;
            case "length_field":
            default: {
                int lenBytes = intOf(cfg.get("lenBytes"), 4);
                int offset = intOf(cfg.get("offset"), 0);
                int adjustment = intOf(cfg.get("adjustment"), 0);
                // 与 FrameDecoderFactory 保持一致：offset>0 时默认剥离 offset+lenBytes
                int initialStrip = intOf(cfg.get("initialStrip"), offset + lenBytes);
                return stripLengthField(framed, lenBytes, offset, adjustment, initialStrip);
            }
        }
    }

    /** length_field 去帧：读 [offset, offset+lenBytes) 大端长度 declared，总帧长 = offset+lenBytes+declared+adjustment，再跳过 initialStrip 字节。 */
    private static byte[] stripLengthField(byte[] framed, int lenBytes, int offset, int adjustment, int initialStrip) {
        if (lenBytes <= 0 || framed.length < offset + lenBytes) {
            return framed;
        }
        long declared = 0;
        for (int i = 0; i < lenBytes; i++) {
            declared = (declared << 8) | (framed[offset + i] & 0xFF);
        }
        long total = (long) offset + lenBytes + declared + adjustment;
        int end = (int) Math.min(total, framed.length);
        int start = (int) Math.min(initialStrip, end);
        if (start >= end) {
            return new byte[0];
        }
        return Arrays.copyOfRange(framed, start, end);
    }

    private static byte[] stripTrailing(byte[] framed, byte[] delim) {
        if (delim.length == 0 || framed.length < delim.length) {
            return framed;
        }
        for (int i = 0; i < delim.length; i++) {
            if (framed[framed.length - delim.length + i] != delim[i]) {
                return framed;
            }
        }
        return Arrays.copyOfRange(framed, 0, framed.length - delim.length);
    }

    private static byte[] withLengthHeader(byte[] payload, int lenBytes) {
        return withLengthHeader(payload, lenBytes, 0);
    }

    /**
     * 前置大端长度头。
     *
     * @param adjustment 与 Netty {@code LengthFieldBasedFrameDecoder} 语义一致的长度修正值：
     *                   帧总长 = offset + lenBytes + 声明长度 + adjustment。
     *                   因此声明长度 = payload 长度 - adjustment（adjustment 通常是
     *                   「长度字段未计入、但属于本帧」的尾部字节数，如 CRC）。
     */
    private static byte[] withLengthHeader(byte[] payload, int lenBytes, int adjustment) {
        if (lenBytes <= 0) {
            return payload;
        }
        byte[] header = new byte[lenBytes];
        long len = payload.length - adjustment;
        if (len < 0) {
            len = 0;
        }
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
