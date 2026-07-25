package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务：按 cron 精确调度场景运行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_schedule")
public class TestSchedule extends BaseEntity {

    private Long projectId;

    private Long scenarioId;

    private String name;

    /** Spring CronTrigger 表达式（秒 分 时 日 月 周） */
    private String cron;

    private Long envId;

    private Integer enabled;

    private LocalDateTime lastRunTime;

    private Integer lastPassed;

    private Long lastCostMs;

    private String remark;
}
