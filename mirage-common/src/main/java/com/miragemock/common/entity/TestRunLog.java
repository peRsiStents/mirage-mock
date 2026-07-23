package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试案例运行记录（proxy 模式）。createTime 即运行时间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_run_log")
public class TestRunLog extends BaseEntity {

    private Long projectId;

    private Long caseId;

    /** proxy / direct */
    private String mode;

    private Integer httpStatus;

    private Long costMs;

    /** 1 通过 / 0 失败 */
    private Integer passed;

    /** JSON: 各断言结果 */
    private String assertionResult;

    private String error;
}
