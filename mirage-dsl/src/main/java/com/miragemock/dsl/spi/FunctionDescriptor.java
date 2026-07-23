package com.miragemock.dsl.spi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 函数市场条目：供管理端「函数库」页面展示。
 */
@Data
@AllArgsConstructor
public class FunctionDescriptor {

    /** 函数名，如 name.cn */
    private String name;

    /** 分类 */
    private String category;

    /** 说明 */
    private String description;

    /** 示例调用 */
    private String example;

    /** 预期返回类型 */
    private String returnType;
}
