package com.miragemock.core.engine;

import lombok.Data;

import java.util.Map;

/**
 * Mock 处理结果动作。
 */
@Data
public class MockResponse {

    public enum Action {
        /** 正常写入响应 */
        WRITE,
        /** 挂起不响应（模拟超时） */
        TIMEOUT,
        /** 直接断开连接 */
        RESET
    }

    private Action action;
    private int status;
    private Map<String, String> headers;
    private Object body;

    public static MockResponse write(int status, Map<String, String> headers, Object body) {
        MockResponse r = new MockResponse();
        r.action = Action.WRITE;
        r.status = status;
        r.headers = headers;
        r.body = body;
        return r;
    }

    public static MockResponse timeout() {
        MockResponse r = new MockResponse();
        r.action = Action.TIMEOUT;
        return r;
    }

    public static MockResponse reset() {
        MockResponse r = new MockResponse();
        r.action = Action.RESET;
        return r;
    }

    public boolean isWrite() {
        return action == Action.WRITE;
    }
}
