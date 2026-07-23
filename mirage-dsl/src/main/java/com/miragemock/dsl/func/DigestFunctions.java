package com.miragemock.dsl.func;

import com.miragemock.dsl.crypto.Codec;
import com.miragemock.dsl.crypto.SmCrypto;
import com.miragemock.dsl.spi.MockFunction;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * 摘要与编码类函数。
 */
public final class DigestFunctions {

    private DigestFunctions() {
    }

    public static List<MockFunction> all() {
        return Arrays.asList(
                MockFns.fn("md5", (a, c) -> Codec.hex(digest("MD5", FuncArgs.asString(a, 0, "")))),
                MockFns.fn("sha1", (a, c) -> Codec.hex(digest("SHA-1", FuncArgs.asString(a, 0, "")))),
                MockFns.fn("sha256", (a, c) -> Codec.hex(digest("SHA-256", FuncArgs.asString(a, 0, "")))),
                MockFns.fn("sha512", (a, c) -> Codec.hex(digest("SHA-512", FuncArgs.asString(a, 0, "")))),
                MockFns.fn("sm3", (a, c) -> Codec.hex(SmCrypto.sm3(Codec.utf8(FuncArgs.asString(a, 0, ""))))),
                MockFns.fn("base64_encode", (a, c) -> Codec.base64(Codec.utf8(FuncArgs.asString(a, 0, "")))),
                MockFns.fn("base64_decode", (a, c) -> Codec.utf8(Codec.base64Decode(FuncArgs.asString(a, 0, "")))),
                MockFns.fn("hex_encode", (a, c) -> Codec.hex(Codec.utf8(FuncArgs.asString(a, 0, "")))),
                MockFns.fn("hex_decode", (a, c) -> Codec.utf8(Codec.hexDecode(FuncArgs.asString(a, 0, "")))),
                MockFns.fn("url_encode", (a, c) -> {
                    try {
                        return URLEncoder.encode(FuncArgs.asString(a, 0, ""), "UTF-8");
                    } catch (java.io.UnsupportedEncodingException e) {
                        throw new IllegalStateException(e);
                    }
                })
        );
    }

    private static byte[] digest(String algo, String data) {
        try {
            return MessageDigest.getInstance(algo).digest(Codec.utf8(data));
        } catch (Exception e) {
            throw new IllegalStateException(algo + " 摘要失败", e);
        }
    }
}
