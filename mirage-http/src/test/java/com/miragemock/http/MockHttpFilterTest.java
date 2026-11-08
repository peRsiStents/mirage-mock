package com.miragemock.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 路径规范化边界：路径参数、URL 编码、重复斜杠、. / ..、尾斜杠保留。
 */
class MockHttpFilterTest {

    @Test
    void plainPath() {
        assertEquals("/api/users", MockHttpFilter.normalizePath("/api/users", true));
    }

    @Test
    void stripPathParameters() {
        // 容器/网关注入的 ;jsessionid 是「偶发不命中」的常见来源
        assertEquals("/api/users", MockHttpFilter.normalizePath("/api/users;jsessionid=ABC123", true));
    }

    @Test
    void urlDecode() {
        assertEquals("/api/中文", MockHttpFilter.normalizePath("/api/%E4%B8%AD%E6%96%87", true));
    }

    @Test
    void malformedPercentKeepsOriginal() {
        // 畸形 % 序列不应抛异常
        assertEquals("/api/100%", MockHttpFilter.normalizePath("/api/100%", true));
    }

    @Test
    void mergeDuplicateSlashes() {
        assertEquals("/api/users", MockHttpFilter.normalizePath("//api//users", true));
    }

    @Test
    void resolveDotSegments() {
        assertEquals("/api/users", MockHttpFilter.normalizePath("/api/./users", true));
        assertEquals("/api/users", MockHttpFilter.normalizePath("/api/x/../users", true));
    }

    @Test
    void dotDotCannotEscapeRoot() {
        assertEquals("/users", MockHttpFilter.normalizePath("/../users", true));
    }

    @Test
    void keepTrailingSlash() {
        // 尾斜杠形态保留，交由 MockEngine 双向容错
        assertEquals("/api/users/", MockHttpFilter.normalizePath("/api/users/", true));
    }

    @Test
    void rootPath() {
        assertEquals("/", MockHttpFilter.normalizePath("/", true));
        assertEquals("/", MockHttpFilter.normalizePath("", true));
        assertEquals("/", MockHttpFilter.normalizePath(null, true));
    }

    @Test
    void pathWithQueryNotIncluded() {
        // 只处理 path 部分
        assertEquals("/api/users", MockHttpFilter.normalizePath("/api/users", true));
    }
}
