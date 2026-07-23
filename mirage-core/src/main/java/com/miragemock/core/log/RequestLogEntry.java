package com.miragemock.core.log;

import lombok.Builder;
import lombok.Data;

/**
 * 请求日志条目：由运行时内核产出，交由持久层（mirage-admin）异步落库。
 */
@Data
@Builder
public class RequestLogEntry {

    private Long projectId;

    private Long interfaceId;

    private Long ruleId;

    private String protocol;

    private String clientAddr;

    private String requestRaw;

    private String requestParsed;

    private String responseRaw;

    private boolean matched;

    private int costMs;
}
