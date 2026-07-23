package com.miragemock.core.log;

/**
 * 请求日志下沉接口。由 mirage-admin 实现异步落库。
 */
public interface RequestLogSink {

    void append(RequestLogEntry entry);
}
