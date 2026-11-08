package com.miragemock.tcp.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 定长字段切分测试：重点验证中文（多字节）场景下的字段对齐。
 */
class FixedFieldsMessageParserTest {

    private final FixedFieldsMessageParser parser = new FixedFieldsMessageParser();

    private static Map<String, Object> config(int... lens) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> fields = new java.util.ArrayList<>();
        for (int i = 0; i < lens.length; i++) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", "f" + i);
            f.put("len", lens[i]);
            fields.add(f);
        }
        cfg.put("fields", fields);
        return cfg;
    }

    @Test
    @DisplayName("ASCII：定长解析正确")
    void asciiParse() {
        Map<String, Object> cfg = config(4, 6);
        byte[] frame = "AB12CD3456".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> r = parser.parse(frame, cfg);
        assertEquals("AB12", r.get("f0"));
        assertEquals("CD3456", r.get("f1"));
    }

    @Test
    @DisplayName("中文：按字节切分（1 汉字 = 3 字节，2 汉字字段需 6 字节长度）")
    void chineseParse() {
        Map<String, Object> cfg = config(6, 4);
        byte[] frame = "张三ABCD".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> r = parser.parse(frame, cfg);
        assertEquals("张三", r.get("f0"));
        assertEquals("ABCD", r.get("f1"));
    }

    @Test
    @DisplayName("中文：encode 按字节补齐，后续字段不错位")
    void chineseEncodeKeepsAlignment() {
        Map<String, Object> cfg = config(6, 4);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("f0", "张三");
        fields.put("f1", "ABCD");
        byte[] out = parser.encode(fields, cfg);
        assertEquals(10, out.length);
        // 重新解析应还原同样的字段（round-trip 不错位）
        Map<String, Object> r = parser.parse(out, cfg);
        assertEquals("张三", r.get("f0"));
        assertEquals("ABCD", r.get("f1"));
    }

    @Test
    @DisplayName("中文：字符数补齐（按字符数算会少 2 字节）会导致后续字段整体错位")
    void chineseCharCountPaddingWouldMisalign() {
        Map<String, Object> cfg = config(6, 4);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("f0", "张三");  // 2 字符 / 6 字节
        fields.put("f1", "ABCD");
        byte[] out = parser.encode(fields, cfg);
        // 第一个字段必须占满 6 字节
        assertEquals("ABCD", new String(out, 6, 4, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("中文超长：按字节截断，总长仍是定长之和")
    void chineseTruncate() {
        Map<String, Object> cfg = config(6, 4);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("f0", "张三丰");   // 9 字节 > 6
        fields.put("f1", "ABCD");
        byte[] out = parser.encode(fields, cfg);
        assertEquals(10, out.length);
        Map<String, Object> r = parser.parse(out, cfg);
        assertEquals(6, ((String) r.get("f0")).getBytes(StandardCharsets.UTF_8).length);
        assertEquals("ABCD", r.get("f1"));
    }

    @Test
    @DisplayName("parse 默认 trim；trimFields=false 时保留首尾空格")
    void trimToggle() {
        Map<String, Object> cfg = config(6);
        byte[] frame = "  ab  ".getBytes(StandardCharsets.UTF_8);
        assertEquals("ab", parser.parse(frame, cfg).get("f0"));

        cfg.put("trimFields", false);
        assertEquals("  ab  ", parser.parse(frame, cfg).get("f0"));
    }

    @Test
    @DisplayName("字节不足：截断到实际长度，不抛异常")
    void shortFrame() {
        Map<String, Object> cfg = config(4, 6);
        byte[] frame = "AB".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> r = parser.parse(frame, cfg);
        assertEquals("AB", r.get("f0"));
        assertEquals("", r.get("f1"));
    }

    @Test
    @DisplayName("空配置：返回空 Map，不抛异常")
    void emptyConfig() {
        assertEquals(0, parser.parse("xx".getBytes(StandardCharsets.UTF_8), null).size());
        assertArrayEquals(new byte[0], parser.encode(new LinkedHashMap<>(), null));
    }
}
