package com.miragemock.http.proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 录制响应匹配键单测：相同请求（含 query 顺序、body）键一致；不同请求键不同。
 */
class RecordKeyTest {

    @Test
    void sameRequest_sameKey() {
        String k1 = RecordKey.of("GET", "/api/user/1", "a=1&b=2", "");
        String k2 = RecordKey.of("GET", "/api/user/1", "a=1&b=2", "");
        assertEquals(k1, k2);
    }

    @Test
    void queryOrderInsensitive() {
        String k1 = RecordKey.of("GET", "/api/user/1", "a=1&b=2", "");
        String k2 = RecordKey.of("GET", "/api/user/1", "b=2&a=1", "");
        assertEquals(k1, k2);
    }

    @Test
    void differentBody_differentKey() {
        String k1 = RecordKey.of("POST", "/api/order", "", "{\"a\":1}");
        String k2 = RecordKey.of("POST", "/api/order", "", "{\"a\":2}");
        assertNotEquals(k1, k2);
    }

    @Test
    void differentMethod_differentKey() {
        String k1 = RecordKey.of("GET", "/api/user/1", "", "");
        String k2 = RecordKey.of("POST", "/api/user/1", "", "");
        assertNotEquals(k1, k2);
    }

    @Test
    void nullInputsHandled() {
        String k1 = RecordKey.of(null, null, null, null);
        String k2 = RecordKey.of("", "", "", "");
        assertEquals(k1, k2);
        assertNotEquals("", k1);
    }

    @Test
    void queryAffectsKey() {
        String k1 = RecordKey.of("GET", "/api/user/1", "a=1", "");
        String k2 = RecordKey.of("GET", "/api/user/1", "a=2", "");
        assertNotEquals(k1, k2);
    }

    @Test
    void keyIsStableLength() {
        String k = RecordKey.of("GET", "/api/user/1", "a=1&b=2", "{\"x\":1}");
        assertEquals(32, k.length());
    }
}
