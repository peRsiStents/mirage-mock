package com.miragemock.admin.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 测试案例运行结果（proxy 模式）：响应 + 断言逐条结果 + 整体通过/失败。
 */
@Data
public class RunResult {

    private Integer httpStatus;
    private Long costMs;
    private Map<String, String> headers;
    private String body;
    private Boolean passed;
    private List<Map<String, Object>> assertions;
    private String error;
    /** 非致命提示（如 GET 忽略了 body）：不影响 passed，仅提示用户 */
    private String warning;
    /**
     * 请求报文（本次实际发出的内容）。
     * HTTP：{method,url,headers,body}；TCP：{target,frame,body,raw}。
     * 用于「记录查看」——回看某次运行发出的请求。
     */
    private Map<String, Object> request;
    /** 返回报文（响应头 + 响应体），与 headers/body 等价，便于一次性展示 */
    private Map<String, Object> response;
}
