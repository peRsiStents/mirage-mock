package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试变量/常量（项目级）：在测试用例里以 ${var.name} 引用。
 *
 * <p>注意：值属性取名 varValue（映射列 var_value），避免 H2 的保留字 value。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_variable")
public class TestVariable extends BaseEntity {

    private Long projectId;

    /** 变量名，引用键 */
    private String name;

    /** 变量值（可为常量，也可含 ${...} 函数，运行时再求值） */
    private String varValue;

    private String remark;

    private Integer status;
}
