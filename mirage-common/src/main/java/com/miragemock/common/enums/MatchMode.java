package com.miragemock.common.enums;

/**
 * TCP 匹配模式
 */
public enum MatchMode {
    /** 串行一问一答 */
    SYNC,
    /** 按流水号关联（并发请求） */
    ASYNC
}
