package com.miragemock.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据驱动运行结果：按数据行逐行执行的汇总（每行一个 RunResult）。
 */
@Data
public class CaseRunResult {

    private Long recordId;
    private Boolean passed;
    private Integer total;
    private Integer passedCount;
    private List<RunResult> results;
}
