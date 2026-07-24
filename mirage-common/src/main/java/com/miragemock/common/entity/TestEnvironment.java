package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试环境：baseUrl + 变量集（变量并入 ${var.name}，相对URL 拼接 baseUrl）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_environment")
public class TestEnvironment extends BaseEntity {

    private Long projectId;

    private String name;

    private String baseUrl;

    /** JSON: [{name, value}] */
    private String variables;

    private Integer status;
}
