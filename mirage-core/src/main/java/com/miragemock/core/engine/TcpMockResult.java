package com.miragemock.core.engine;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TCP Mock 处理结果：携带命中信息与渲染后的响应字段（供日志与编码回写）。
 */
@Data
public class TcpMockResult {

    public enum Action {
        /** 正常回写 */
        WRITE,
        /** 挂起不响应（模拟超时） */
        TIMEOUT,
        /** 直接断开连接 */
        RESET
    }

    private Long projectId;
    private Long interfaceId;
    private Long ruleId;
    private boolean matched;
    private Action action;
    /**
     * 延迟毫秒数（可为 null 表示无延迟）。引擎不再 sleep，仅携带延迟值，
     * 由 TcpMockHandler 在回写前自行调度等待（封顶与引擎侧一致，见 MockEngine）。
     */
    private Integer delayMs;
    /** 渲染后的响应字段（WRITE 时由处理器按报文格式编码） */
    private Map<String, Object> fields;

    public static TcpMockResult write(Long projectId, Long interfaceId, Long ruleId, Map<String, Object> fields) {
        TcpMockResult r = new TcpMockResult();
        r.projectId = projectId;
        r.interfaceId = interfaceId;
        r.ruleId = ruleId;
        r.matched = true;
        r.action = Action.WRITE;
        r.fields = fields;
        return r;
    }

    public static TcpMockResult fault(Long projectId, Long interfaceId, Long ruleId, Action action) {
        TcpMockResult r = new TcpMockResult();
        r.projectId = projectId;
        r.interfaceId = interfaceId;
        r.ruleId = ruleId;
        r.matched = true;
        r.action = action;
        return r;
    }

    public static TcpMockResult notMatched(Long projectId, Long interfaceId, String routeValue) {
        TcpMockResult r = new TcpMockResult();
        r.projectId = projectId;
        r.interfaceId = interfaceId;
        r.matched = false;
        r.action = Action.WRITE;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("error", "NO_RULE_MATCHED");
        if (routeValue != null) {
            fields.put("route", routeValue);
        }
        r.fields = fields;
        return r;
    }
}
