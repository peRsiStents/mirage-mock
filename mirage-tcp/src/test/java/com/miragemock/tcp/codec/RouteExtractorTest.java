package com.miragemock.tcp.codec;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 路由/流水号提取单测：$.json / field: / kv: / 裸 key / 缺失值。
 */
class RouteExtractorTest {

    private Map<String, Object> fields() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("serialNo", "S0001");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transCode", "0200");
        body.put("body", nested);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("transCode", "0200");
        root.put("body", nested);
        root.put("orgNo", "ORG01");
        root.put("trxnType", "PUR");
        return root;
    }

    @Test
    void jsonPath_topLevel() {
        assertEquals("0200", RouteExtractor.extract(fields(), "$.transCode"));
    }

    @Test
    void jsonPath_nested() {
        assertEquals("S0001", RouteExtractor.extract(fields(), "$.body.serialNo"));
    }

    @Test
    void jsonPath_missing() {
        assertNull(RouteExtractor.extract(fields(), "$.nope"));
        assertNull(RouteExtractor.extract(fields(), "$.body.missing"));
    }

    @Test
    void fieldSyntax() {
        assertEquals("ORG01", RouteExtractor.extract(fields(), "field:orgNo"));
    }

    @Test
    void kvSyntax() {
        assertEquals("PUR", RouteExtractor.extract(fields(), "kv:trxnType"));
    }

    @Test
    void bareKey() {
        assertEquals("0200", RouteExtractor.extract(fields(), "transCode"));
    }

    @Test
    void nullOrEmptyInputs() {
        assertNull(RouteExtractor.extract(null, "$.transCode"));
        assertNull(RouteExtractor.extract(fields(), null));
        assertNull(RouteExtractor.extract(fields(), ""));
        assertNull(RouteExtractor.extract(fields(), "kv:missing"));
    }
}
