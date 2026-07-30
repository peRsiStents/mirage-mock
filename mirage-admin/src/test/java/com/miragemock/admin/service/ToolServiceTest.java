package com.miragemock.admin.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具市场核心逻辑单测：把最脆、最易回归的部分钉死——
 * 手写 SQL 格式化器、JSON/XML 格式化与非法输入、哈希/SM3 已知向量、编码往返、
 * JWT 解析、时间戳、UUID、以及 SM4/SM2/RSA 加解密签名往返。
 */
class ToolServiceTest {

    private final ToolService t = new ToolService();

    // ---------- JSON ----------
    @Test
    void jsonFormat_prettyIndents() {
        String out = t.jsonFormat("{\"a\":1,\"b\":{\"c\":2}}", "2");
        assertTrue(out.contains("\n"), "应多行输出");
        assertTrue(out.contains("\"a\""));
    }

    @Test
    void jsonMinify_singleLine() {
        assertEquals("{\"a\":1}", t.jsonMinify("{ \"a\" : 1 }"));
    }

    @Test
    void jsonValidate_badInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> t.jsonValidate("{bad json"));
    }

    @Test
    void jsonEscape_unescape_roundtrip() {
        String original = "line1\nline2 \"q\" 中文";
        assertEquals(original, t.jsonUnescape(t.jsonEscape(original)));
    }

    // ---------- XML ----------
    @Test
    void xmlFormat_indents() {
        String out = t.xmlFormat("<root><item>hi</item></root>", 2);
        assertTrue(out.contains("\n  <item>"), "应缩进子节点");
    }

    @Test
    void xmlMinify_collapsesWhitespace() {
        String min = t.xmlMinify(t.xmlFormat("<root><item>hi</item></root>", 2));
        assertTrue(min.contains("<root><item>"));
        assertFalse(min.contains("> <"));
    }

    @Test
    void xmlValidate_badInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> t.xmlValidate("<root><unclosed>"));
    }

    // ---------- SQL（手写格式化器，精确断言）----------
    @Test
    void sqlFormat_keywordsClausesAndIndent() {
        String sql = "select id,name,count(*) from t_user u left join t_order o on u.id=o.uid "
                + "where u.status=1 and o.amount>100 group by u.id order by u.id desc limit 10";
        String expected = "SELECT id,name,count(*)\n"
                + "FROM t_user u\n"
                + "LEFT JOIN t_order o\n"
                + "ON u.id=o.uid\n"
                + "WHERE u.status=1\n"
                + "  AND o.amount>100\n"
                + "GROUP BY u.id\n"
                + "ORDER BY u.id desc\n"
                + "LIMIT 10";
        assertEquals(expected, t.sqlFormat(sql));
    }

    // ---------- 哈希 / SM3（已知向量）----------
    @Test
    void hash_knownVectors() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", t.hash("abc", "MD5"));
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", t.hash("abc", "SHA1"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", t.hash("abc", "SHA256"));
    }

    @Test
    void sm3_knownVector() {
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
                t.sm3("abc", "hex"));
    }

    @Test
    void hash_unknownAlgoThrows() {
        assertThrows(IllegalArgumentException.class, () -> t.hash("abc", "ROT13"));
    }

    // ---------- 编码往返 ----------
    @Test
    void base64_hex_url_roundtrips() {
        String s = "hello 蜃楼 a+b&c=测试";
        assertEquals(s, t.base64Decode(t.base64Encode(s)));
        assertEquals(s, t.hexDecode(t.hexEncode(s)));
        // url：空格会被编码成 +，解码还原
        String enc = t.urlEncode(s);
        assertEquals(s, t.urlDecode(enc));
    }

    @Test
    void base64Decode_badInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> t.base64Decode("@@@ not base64 @@@"));
    }

    // ---------- JWT ----------
    @Test
    void jwtDecode_parsesHeaderPayloadSignature() {
        // header {"alg":"HS256","typ":"JWT"} . payload {"user":"admin","role":"SUPER"} . sig
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiYWRtaW4iLCJyb2xlIjoiU1VQRVIifQ.sigpart";
        Map<String, String> r = t.jwtDecode(jwt);
        assertTrue(r.get("header").contains("HS256"));
        assertTrue(r.get("payload").contains("admin"));
        assertEquals("sigpart", r.get("signature"));
    }

    @Test
    void jwtDecode_malformedThrows() {
        assertThrows(IllegalArgumentException.class, () -> t.jwtDecode("not-a-jwt"));
    }

    // ---------- 时间戳 ----------
    @Test
    void timestamp_roundtripSameLocalDate() {
        Map<String, Object> e = t.tsToEpoch("2026-01-01 00:00:00");
        String secs = String.valueOf(e.get("seconds"));
        Map<String, Object> back = t.tsFromEpoch(secs);
        assertTrue(((String) back.get("local")).startsWith("2026-01-01"),
                "同一时区往返应回到同一本地日期");
    }

    @Test
    void timestamp_secondsAndMillisEquivalent() {
        Map<String, Object> fromSec = t.tsFromEpoch("1767225600");
        Map<String, Object> fromMs = t.tsFromEpoch("1767225600000");
        assertEquals(fromSec.get("local"), fromMs.get("local"),
                "秒与毫秒应自动识别为同一时刻");
    }

    // ---------- UUID ----------
    @Test
    void uuid_countAndFormat() {
        String out = t.uuid(3);
        String[] lines = out.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    // ---------- SM4 ----------
    @Test
    void sm4_ecb_roundtrip() {
        String key = t.sm4GenKey(); // hex 16 字节
        String cipher = t.sm4Encrypt("plain text 中文", key, null, "ECB", "hex", "base64");
        assertEquals("plain text 中文", t.sm4Decrypt(cipher, key, null, "ECB", "hex", "base64"));
    }

    @Test
    void sm4_cbc_roundtrip() {
        String key = t.sm4GenKey();
        String cipher = t.sm4Encrypt("cbc data", key, key, "CBC", "hex", "hex");
        assertEquals("cbc data", t.sm4Decrypt(cipher, key, key, "CBC", "hex", "hex"));
    }

    @Test
    void sm4_badKeyLengthThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> t.sm4Encrypt("x", "1234", null, "ECB", "utf8", "base64"));
    }

    // ---------- SM2 ----------
    @Test
    void sm2_encryptDecryptAndSignVerify() {
        Map<String, String> kp = t.sm2Gen();
        String cipher = t.sm2Encrypt("secret", kp.get("publicKey"), "base64");
        assertEquals("secret", t.sm2Decrypt(cipher, kp.get("privateKey"), "base64"));

        String sig = t.sm2Sign("message", kp.get("privateKey"), "base64");
        assertTrue(t.sm2Verify("message", sig, kp.get("publicKey"), "base64"));
        assertFalse(t.sm2Verify("tampered", sig, kp.get("publicKey"), "base64"));
    }

    // ---------- RSA ----------
    @Test
    void rsa_encryptDecryptAndSignVerify() {
        Map<String, String> kp = t.rsaGen(2048);
        String cipher = t.rsaEncrypt("rsa-ok", kp.get("publicKey"));
        assertEquals("rsa-ok", t.rsaDecrypt(cipher, kp.get("privateKey")));

        String sig = t.rsaSign("message", kp.get("privateKey"));
        assertTrue(t.rsaVerify("message", sig, kp.get("publicKey")));
        assertFalse(t.rsaVerify("wrong", sig, kp.get("publicKey")));
    }
}
