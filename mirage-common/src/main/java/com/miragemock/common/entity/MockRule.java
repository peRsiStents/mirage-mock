package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Mock 规则：匹配条件 + 响应模板 + 故障注入
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_rule")
public class MockRule extends BaseEntity {

    private Long interfaceId;

    private String name;

    /** 优先级，数值小者先匹配，默认 100 */
    private Integer priority;

    /** 匹配条件 DSL（JSON 数组，AND 关系） */
    private String matchCondition;

    /** 响应模板 DSL（JSON 文本） */
    private String responseTemplate;

    /** NONE / FIXED / RANDOM */
    private String delayType;

    private Integer delayMs;

    private Integer delayMinMs;

    private Integer delayMaxMs;

    /** NONE / ERROR_STATUS / RESET / TIMEOUT */
    private String faultType;

    /** JSON，如 {"httpStatus":500,"body":{...}} */
    private String faultConfig;

    /** 1 启用 / 0 停用 */
    private Integer status;
}
