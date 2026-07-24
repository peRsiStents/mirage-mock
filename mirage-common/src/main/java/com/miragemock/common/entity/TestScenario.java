package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试场景：有序步骤组成业务链路，步骤间可传递提取变量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_scenario")
public class TestScenario extends BaseEntity {

    private Long projectId;

    private String name;

    private String remark;

    /** STOP(默认,失败即停) / CONTINUE */
    private String onFail;

    /** 默认运行环境（可运行时覆盖） */
    private Long envId;

    private Integer status;
}
