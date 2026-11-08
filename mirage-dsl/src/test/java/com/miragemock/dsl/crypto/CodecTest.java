package com.miragemock.dsl.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编解码边界：hex 非法输入、base64 往返。
 */
class CodecTest {

    @Test
    void hexRoundTrip() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, Codec.hexDecode(Codec.hex(data)));
    }

    @Test
    void hexDecode_oddLength_throws() {
        // 关键回归：奇数长度原本抛 StringIndexOutOfBoundsException（裸 500）
        assertThrows(IllegalArgumentException.class, () -> Codec.hexDecode("abc"));
    }

    @Test
    void hexDecode_illegalChar_throws() {
        // 关键回归：非法字符原本静默产出 0xFF 垃圾字节
        assertThrows(IllegalArgumentException.class, () -> Codec.hexDecode("zz"));
    }

    @Test
    void hexDecode_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> Codec.hexDecode(null));
    }

    @Test
    void base64RoundTrip() {
        byte[] data = "中文 payload".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, Codec.base64Decode(Codec.base64(data)));
    }

    @Test
    void hexDecode_ignoresWhitespace() {
        assertArrayEquals(new byte[]{0x12, 0x34}, Codec.hexDecode("12 34"));
    }

    @Test
    void utf8RoundTrip() {
        assertEquals("中文", Codec.utf8(Codec.utf8("中文")));
    }
}
