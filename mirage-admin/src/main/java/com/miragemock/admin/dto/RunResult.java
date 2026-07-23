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
}
