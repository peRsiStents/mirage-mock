package com.miragemock.admin.dto;

import lombok.Data;

import java.util.Map;

/**
 * 模板试算请求。
 */
@Data
public class EvaluateRequest {

    /** 响应模板（JSON 对象/数组），或纯字符串模板 */
    private Object template;

    /** 模拟上下文变量，如 { "path": {"userId":"U1"} } 或扁平 { "path.userId":"U1" } */
    private Map<String, Object> context;

    /** 项目 id（用于密钥/序列解析），可选 */
    private Long projectId;
}
