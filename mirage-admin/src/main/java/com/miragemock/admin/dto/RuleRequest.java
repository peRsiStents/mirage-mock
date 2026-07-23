package com.miragemock.admin.dto;

import lombok.Data;

/**
 * 规则创建/更新请求。matchCondition / responseTemplate / faultConfig 以 JSON 对象传入，
 * 服务端序列化为文本落库。
 */
@Data
public class RuleRequest {

    private String name;
    private Integer priority;
    private Object matchCondition;
    private Object responseTemplate;
    private String delayType;
    private Integer delayMs;
    private Integer delayMinMs;
    private Integer delayMaxMs;
    private String faultType;
    private Object faultConfig;
    private Integer status;
}
