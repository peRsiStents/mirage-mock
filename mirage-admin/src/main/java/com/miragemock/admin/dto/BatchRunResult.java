package com.miragemock.admin.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 批量运行结果：每个用例一条摘要（不含完整响应体，避免大批量回传过大），
 * 外加汇总。完整响应可点单条「运行」/「历史」查看。
 */
@Data
public class BatchRunResult {

    /** 每条用例的运行摘要：{id, name, passed, httpStatus, costMs, error} */
    private List<Map<String, Object>> results;

    private int total;

    private int passedCount;

    private int failedCount;

    private long costMs;

    private boolean passed;
}
