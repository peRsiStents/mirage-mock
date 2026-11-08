package com.miragemock.dsl.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * 编解码工具：Base64 / Hex / UTF-8。
 */
public final class Codec {

    private Codec() {
    }

    public static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String s) {
        return Base64.getDecoder().decode(s);
    }

    public static String hex(byte[] data) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * Hex 解码：非法输入一律抛 IllegalArgumentException。
     *
     * <p>不做静默容错：奇数长度会截断半个字节、非法字符会被 Character.digit 判为 -1
     * 并拼成 0xFF 之类的垃圾字节，静默产出错误数据比报错更难排查。
     *
     * @throws IllegalArgumentException 长度为奇数或含非 hex 字符
     */
    public static byte[] hexDecode(String s) {
        if (s == null) {
            throw new IllegalArgumentException("hex 解码失败：输入为 null");
        }
        String clean = s.toLowerCase(Locale.ROOT).replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        int len = clean.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("hex 解码失败：长度为奇数（" + len + "），需为偶数："
                    + abbreviate(s));
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(clean.charAt(i), 16);
            int lo = Character.digit(clean.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("hex 解码失败：包含非法十六进制字符 '"
                        + (hi < 0 ? clean.charAt(i) : clean.charAt(i + 1)) + "'："
                        + abbreviate(s));
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** 错误消息里的输入摘要：过长时截断，避免日志/响应被超长输入撑爆 */
    private static String abbreviate(String s) {
        return s.length() <= 64 ? s : s.substring(0, 64) + "...";
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
