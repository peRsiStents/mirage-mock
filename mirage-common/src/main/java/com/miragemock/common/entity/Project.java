package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目：数据隔离单元
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseEntity {

    private String name;

    /** 项目编码，唯一 */
    private String code;

    private String remark;

    /** 项目级规则版本号，规则/接口变更时递增（热刷新依据） */
    private Integer ruleVersion;

    /** 1 启用 / 0 停用 */
    private Integer status;
}
