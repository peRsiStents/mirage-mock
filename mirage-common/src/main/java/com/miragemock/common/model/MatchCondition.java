package com.miragemock.common.model;

import lombok.Data;

/**
 * 匹配条件 DSL 元素。多条之间为 AND 关系。
 *
 * <pre>
 * {"source":"header","key":"X-Env","op":"eq","value":"gray"}
 * </pre>
 */
@Data
public class MatchCondition {

    /** header / query / body(JSONPath) / path / form / field */
    private String source;

    /** header 名 / query 名 / JSONPath / path 变量名 / 字段名 */
    private String key;

    /** eq ne in gt gte lt lte regex contains exists not_exists */
    private String op;

    /** 比较值；in 时为数组 */
    private Object value;
}
