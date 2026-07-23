package com.miragemock.core.render;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 模板渲染结果：HTTP 状态码 / 响应头 / 响应体（已求值的对象树）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenderedResponse {

    private int status = 200;

    private Map<String, String> headers;

    private Object body;

    public static RenderedResponse of(int status, Map<String, String> headers, Object body) {
        return new RenderedResponse(status, headers, body);
    }
}
