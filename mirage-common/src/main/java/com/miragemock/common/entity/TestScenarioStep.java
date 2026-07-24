package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景步骤：引用一个测试用例，可从响应提取变量供后续步骤引用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_scenario_step")
public class TestScenarioStep extends BaseEntity {

    private Long scenarioId;

    /** 顺序 */
    private Integer seq;

    private Long caseId;

    /** 步骤标签（可选） */
    private String name;

    /** JSON: [{var, source(jsonPath|header|status|body), expr}] */
    private String extract;

    /** 失败仍继续 */
    private Integer continueOnFail;

    private Integer enabled;
}
