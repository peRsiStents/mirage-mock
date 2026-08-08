package com.miragemock.admin.service;

import com.miragemock.common.entity.MockRequestLog;
import com.miragemock.common.entity.TestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求日志 → 测试用例 解析器单测：覆盖 GET(带 query/头丢弃)、POST JSON / x-www / multipart、
 * 以及截断标记。解析器为手写、易回归，需钉死。parseHttpRequest 包级可见，构造 LogService 时
 * 传 null mapper（解析不依赖 DB）。
 */
class LogServiceTest {

    // 第 5 个参数 ProjectAuthz 仅 get()/query() 鉴权用，解析器不依赖，传 null
    private final LogService svc = new LogService(null, null, null, null, null);

    private MockRequestLog logOf(String raw) {
        MockRequestLog l = new MockRequestLog();
        l.setId(100L);
        l.setProjectId(1L);
        l.setProtocol("HTTP");
        l.setRequestRaw(raw);
        return l;
    }

    @Test
    void getWithQueryAndHeaderDrop() {
        String raw = "GET /api/user/123?fields=basic&x=1\n"
                + "Authorization: Bearer xyz\n"
                + "Accept: */*\n"
                + "Host: example.com\n"
                + "Content-Length: 0\n"
                + "Accept-Encoding: gzip\n";
        TestCase tc = svc.parseHttpRequest(logOf(raw));

        assertEquals("GET", tc.getMethod());
        assertEquals("/api/user/123", tc.getUrl());
        // query 解析
        assertTrue(tc.getQuery().contains("\"k\":\"fields\""));
        assertTrue(tc.getQuery().contains("\"v\":\"basic\""));
        assertTrue(tc.getQuery().contains("\"k\":\"x\""));
        // 保留的头
        String h = tc.getHeaders();
        assertTrue(h.contains("Authorization"));
        assertTrue(h.contains("Accept"));
        // 丢弃的头（hop-by-hop / 自动重算 / 由 body 决定）
        assertFalse(h.contains("Host"));
        assertFalse(h.contains("Content-Length"));
        assertFalse(h.contains("Accept-Encoding"));
        assertFalse(h.contains("Content-Type"));
        // GET 无 body
        assertEquals("none", tc.getBodyType());
        assertEquals("", tc.getBody());
        // 默认 status 断言
        assertTrue(tc.getAssertions().contains("\"type\":\"status\""));
        assertTrue(tc.getAssertions().contains("\"expected\":\"200\""));
        assertEquals("proxy", tc.getMode());
        assertEquals(Integer.valueOf(1), tc.getStatus());
    }

    @Test
    void postJson_classifiedAsRaw() {
        String raw = "POST /api/echo\n"
                + "Content-Type: application/json\n"
                + "X-Trace: t1\n"
                + "\n"
                + "{\"orderId\":\"ORD1\",\"amount\":99.9}";
        TestCase tc = svc.parseHttpRequest(logOf(raw));

        assertEquals("POST", tc.getMethod());
        assertEquals("/api/echo", tc.getUrl());
        assertEquals("raw", tc.getBodyType());
        assertEquals("application/json", tc.getBodyContentType());
        assertEquals("{\"orderId\":\"ORD1\",\"amount\":99.9}", tc.getBody());
        // content-type 不进 headers（由 bodyContentType 决定）
        assertFalse(tc.getHeaders().contains("Content-Type"));
        assertTrue(tc.getHeaders().contains("X-Trace"));
    }

    @Test
    void postUrlencoded_classifiedAsRows() {
        String raw = "POST /api/submit\n"
                + "Content-Type: application/x-www-form-urlencoded\n"
                + "\n"
                + "name=mirage&city=%E5%8C%97%E4%BA%AC&ok=1";
        TestCase tc = svc.parseHttpRequest(logOf(raw));

        assertEquals("x-www-form-urlencoded", tc.getBodyType());
        // 转为 [{k,v}]，URL 解码（中文应还原）
        assertTrue(tc.getBody().contains("\"k\":\"name\""));
        assertTrue(tc.getBody().contains("\"v\":\"mirage\""));
        assertTrue(tc.getBody().contains("\"k\":\"city\""));
        assertTrue(tc.getBody().contains("北京"), "URL 解码应还原中文");
    }

    @Test
    void multipart_notRebuilt() {
        String raw = "POST /api/upload\n"
                + "Content-Type: multipart/form-data; boundary=----xyz\n"
                + "\n"
                + "------xyz\nContent-Disposition: form-data; name=\"f\"\n\nv\n------xyz--";
        TestCase tc = svc.parseHttpRequest(logOf(raw));

        assertEquals("none", tc.getBodyType(), "multipart 无法从文本重建");
        assertTrue(tc.getRemark().contains("multipart"), "备注应提示手动补充");
    }

    @Test
    void truncatedMarkerStrippedFromBody() {
        // 截断标记出现在末尾，不应被当作 body 一行
        String raw = "GET /api/x?a=1&b=2\nX-K: v\n...(truncated)";
        TestCase tc = svc.parseHttpRequest(logOf(raw));

        assertEquals("/api/x", tc.getUrl());
        assertTrue(tc.getQuery().contains("\"k\":\"a\""));
        assertTrue(tc.getQuery().contains("\"k\":\"b\""));
        assertFalse(tc.getBody().contains("truncated"), "截断标记不应残留");
    }
}
