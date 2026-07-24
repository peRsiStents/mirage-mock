package com.miragemock.admin.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 场景运行结果：整体通过/失败 + 统计 + 每步明细。
 */
@Data
public class ScenarioRunResult {

    private Long recordId;
    private Boolean passed;
    private Integer totalSteps;
    private Integer passedSteps;
    private Integer failedSteps;
    private Long costMs;
    /** 每步明细：{seq, caseId, caseName, stepName, passed, httpStatus, costMs, error, headers, body, assertions, extracts, skipped} */
    private List<Map<String, Object>> steps;
}
