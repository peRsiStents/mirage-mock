package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试运行记录：场景或单用例一次运行的汇总（含每步明细 detail JSON）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_run_record")
public class TestRunRecord extends BaseEntity {

    private Long projectId;

    /** scenario / case */
    private String targetType;

    private Long targetId;

    private Long envId;

    /** 1 通过 / 0 失败 */
    private Integer passed;

    private Integer totalSteps;

    private Integer passedSteps;

    private Integer failedSteps;

    private Long costMs;

    /** JSON: 每步明细 {seq,caseId,caseName,passed,httpStatus,costMs,error,headers,body,assertions,extracts,skipped} */
    private String detail;
}
