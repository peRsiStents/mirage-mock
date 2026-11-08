package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * CI headless 运行令牌（用于定时/流水线免 JWT 触发场景运行）。
     *
     * <p>{@code @JsonIgnore}：令牌是「等同于免密访问该项目的凭据」，
     * 绝不能随项目列表/详情接口返回给前端（否则任意项目成员都能拿到 CI 权限）。
     * 需要查看/轮换请走专门的接口（见 ProjectController#ciToken）。
     */
    @JsonIgnore
    private String ciToken;
}
