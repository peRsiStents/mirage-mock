package com.miragemock.tcp.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 报文解析器单测：正常 JSON 解析与回编，非 JSON 兜底 _raw，编码往返一致。
 */
class JsonMessageParserTest {

    private final JsonMessageParser parser = new JsonMessageParser();

    @Test
    void parse_validJson() {
        byte[] frame = "{\"transCode\":\"0200\",\"amount\":\"1000\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> fields = parser.parse(frame, Collections.emptyMap());
        assertEquals("0200", fields.get("transCode"));
        assertEquals("1000", fields.get("amount"));
    }

    @Test
    void parse_nonJson_fallsBackToRaw() {
        byte[] frame = "just-a-plain-text".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> fields = parser.parse(frame, Collections.emptyMap());
        assertTrue(fields.containsKey("_raw"));
        assertEquals("just-a-plain-text", fields.get("_raw"));
    }

    @Test
    void encode_roundTrip() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("transCode", "0210");
        fields.put("respCode", "00");
        fields.put("amount", "1000");
        byte[] encoded = parser.encode(fields, Collections.emptyMap());
        Map<String, Object> decoded = parser.parse(encoded, Collections.emptyMap());
        assertEquals(fields, decoded);
    }

    @Test
    void parse_emptyFrame_returnsEmptyMap() {
        Map<String, Object> fields = parser.parse(new byte[0], Collections.emptyMap());
        assertNotNull(fields);
        assertTrue(fields.isEmpty());
        assertNotNull(parser.parse(null, Collections.emptyMap()));
    }
}
