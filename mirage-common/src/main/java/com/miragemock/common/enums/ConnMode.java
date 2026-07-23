package com.miragemock.common.enums;

/**
 * TCP 连接模式
 */
public enum ConnMode {
    /** 长连接 */
    LONG,
    /** 短连接：一次问答后由 Mock 端关闭 */
    SHORT
}
