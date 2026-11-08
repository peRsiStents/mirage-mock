package com.miragemock.tcp.frame;

import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.crypto.Codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 按 frame_config 从 socket 精确读取**一帧**（测试客户端侧）。
 *
 * <p>与 {@link FrameDecoderFactory}（服务端切帧）语义一致，但走阻塞 Socket 而非 Netty：
 * length_field 读固定长度头 + 声明长度的正文；fixed 读定长；delimiter 读到分隔符；
 * close_end 读到 EOF（或空闲超时）。
 *
 * <p>存在的意义：测试用例若像原先那样「一直读到 EOF/超时」，
 * 对长连接监听器每次都要白等满 SO_TIMEOUT（默认 10s），N 条 TCP 用例 = 10×N 秒；
 * 而按帧精确读取可以在拿到一帧后立刻返回。
 */
public final class FrameReader {

    /** 读到部分数据后再等这么久仍无新字节 → 认为本帧结束（仅 close_end 用） */
    private static final int DEFAULT_IDLE_MS = 300;

    private FrameReader() {
    }

    /**
     * 读取一帧原始字节（含帧头/分隔符，由调用方按需 strip）。
     *
     * @param sock            已连接的 socket（读取期间会调整 SO_TIMEOUT）
     * @param frameConfigText frame_config JSON 文本，可为空（默认 length_field 4 字节大端）
     * @param timeoutMs       单次读等待上限
     * @return 一帧的原始字节；close_end 且对端立即关闭时返回已读到的全部字节
     * @throws IOException 读取失败、读取超时或数据不完整
     */
    public static byte[] readFrame(Socket sock, String frameConfigText, int timeoutMs) throws IOException {
        Map<String, Object> cfg = parse(frameConfigText);
        String type = str(cfg.get("type"), "length_field");
        switch (type) {
            case "length_field":
                return readLengthField(sock, cfg, timeoutMs);
            case "fixed":
                return readFixed(sock, intOf(cfg.get("length"), 1), timeoutMs);
            case "delimiter":
                return readDelimiter(sock, parseDelimiter(str(cfg.get("value"), "\n")), timeoutMs);
            case "close_end":
                return readUntilIdle(sock, timeoutMs, DEFAULT_IDLE_MS);
            default:
                throw new IOException("未知的帧类型: " + type);
        }
    }

    /** length_field：读 offset 前缀 + lenBytes 长度头 + 声明长度正文。 */
    private static byte[] readLengthField(Socket sock, Map<String, Object> cfg, int timeoutMs) throws IOException {
        int lenBytes = intOf(cfg.get("lenBytes"), 4);
        int offset = intOf(cfg.get("offset"), 0);
        int adjustment = intOf(cfg.get("adjustment"), 0);
        sock.setSoTimeout(timeoutMs);
        byte[] prefix = readFully(sock, offset, "长度头前缀");
        byte[] header = readFully(sock, lenBytes, "长度头");
        long declared = 0;
        for (int i = 0; i < lenBytes; i++) {
            declared = (declared << 8) | (header[i] & 0xFF);
        }
        long bodyLen = declared + adjustment;
        if (bodyLen < 0) {
            bodyLen = 0;
        }
        if (bodyLen > Integer.MAX_VALUE - 64) {
            throw new IOException("声明长度过大: " + declared);
        }
        byte[] body = readFully(sock, (int) bodyLen, "正文");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(prefix, 0, prefix.length);
        out.write(header, 0, header.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    /** fixed：读定长字节。 */
    private static byte[] readFixed(Socket sock, int length, int timeoutMs) throws IOException {
        if (length <= 0) {
            throw new IOException("fixed 帧长度非法: " + length);
        }
        sock.setSoTimeout(timeoutMs);
        return readFully(sock, length, "定长帧");
    }

    /** delimiter：一直读到分隔符出现为止。 */
    private static byte[] readDelimiter(Socket sock, byte[] delim, int timeoutMs) throws IOException {
        if (delim.length == 0) {
            throw new IOException("分隔符不能为空");
        }
        sock.setSoTimeout(timeoutMs);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int matched = 0;
        while (true) {
            int b;
            try {
                b = sock.getInputStream().read();
            } catch (SocketTimeoutException e) {
                throw new IOException("读取超时（" + timeoutMs + "ms），已收到 " + out.size()
                        + " 字节，未等到分隔符", e);
            }
            if (b < 0) {
                throw new IOException("连接已关闭，未等到分隔符（已收到 " + out.size() + " 字节）");
            }
            out.write(b);
            if ((b & 0xFF) == (delim[matched] & 0xFF)) {
                matched++;
                if (matched == delim.length) {
                    return out.toByteArray();
                }
            } else {
                // 部分匹配回退：重新对齐（分隔符首字节也可能是中途字节）
                matched = (b & 0xFF) == (delim[0] & 0xFF) ? 1 : 0;
            }
        }
    }

    /**
     * close_end：读到 EOF 为止；若对端保持连接不关，则收到首字节后按空闲窗口判定本帧结束。
     */
    private static byte[] readUntilIdle(Socket sock, int timeoutMs, int idleMs) throws IOException {
        InputStream in = sock.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        sock.setSoTimeout(timeoutMs);
        try {
            int n = in.read(tmp);
            if (n < 0) {
                return out.toByteArray();
            }
            out.write(tmp, 0, n);
        } catch (SocketTimeoutException e) {
            throw new IOException("读取超时（" + timeoutMs + "ms），未收到任何响应数据", e);
        }
        // 已收到数据：改为空闲窗口判定「本帧结束」
        sock.setSoTimeout(idleMs);
        while (true) {
            try {
                int n = in.read(tmp);
                if (n < 0) {
                    break;
                }
                out.write(tmp, 0, n);
            } catch (SocketTimeoutException e) {
                break;
            }
        }
        return out.toByteArray();
    }

    /**
     * 精确读取 n 字节；数据不完整时抛异常（区分「超时」与「对端关闭」）。
     */
    private static byte[] readFully(Socket sock, int n, String what) throws IOException {
        if (n <= 0) {
            return new byte[0];
        }
        InputStream in = sock.getInputStream();
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r;
            try {
                r = in.read(buf, read, n - read);
            } catch (SocketTimeoutException e) {
                throw new IOException("读取" + what + "超时（已读 " + read + "/" + n + " 字节）", e);
            }
            if (r < 0) {
                throw new IOException("连接已关闭，" + what + "不完整（已读 " + read + "/" + n + " 字节）");
            }
            read += r;
        }
        return buf;
    }

    private static Map<String, Object> parse(String frameConfigText) {
        if (frameConfigText == null || frameConfigText.trim().isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        Map<String, Object> m = JsonUtils.parseMap(frameConfigText);
        return m == null ? new java.util.LinkedHashMap<>() : m;
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
