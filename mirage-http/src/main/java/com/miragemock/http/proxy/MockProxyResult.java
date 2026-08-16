package com.miragemock.http.proxy;

import java.util.Map;

/**
 * 代理转发/回放的响应结果（HTTP 层直接回写）。
 */
public final class MockProxyResult {

    private final int status;
    private final Map<String, String> headers;
    private final String body;

    public MockProxyResult(int status, Map<String, String> headers, String body) {
        this.status = status;
        this.headers = headers == null ? java.util.Collections.emptyMap() : headers;
        this.body = body == null ? "" : body;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
