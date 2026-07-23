package com.miragemock.common.enums;

/**
 * 故障注入类型
 */
public enum FaultType {
    /** 正常返回 */
    NONE,
    /** 返回配置的错误状态码与错误体 */
    ERROR_STATUS,
    /** 挂起不响应（模拟超时） */
    TIMEOUT,
    /** 直接断开连接 */
    RESET
}
