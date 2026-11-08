package com.miragemock.core.match;

import com.jayway.jsonpath.DocumentContext;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 请求快照：规则匹配与模板渲染所需的请求数据视图。
 */
@Data
@Builder
public class RequestSnapshot {

    private String protocol;

    private String method;

    private String path;

    /** 请求头（key 已转小写，便于大小写不敏感匹配） */
    private Map<String, String> headers;

    /** query 参数（单值，取首个） */
    private Map<String, String> query;

    /** 表单参数 */
    private Map<String, String> form;

    /** 请求体原文（JSON 文本，供 JSONPath 匹配） */
    private String bodyRaw;

    /** 请求体解析后的字段树（JSON 对象） */
    private Map<String, Object> body;

    /** TCP 报文字段（M2）；HTTP 下与 body 同义 */
    private Map<String, Object> fields;

    /**
     * 请求级 JSONPath 上下文懒缓存：匹配阶段对同一 bodyRaw 只 parse 一次，
     * DocumentContext 供该请求全部条件复用（运行期临时字段，非请求数据）。
     */
    private transient volatile DocumentContext jsonPathContext;

    /** body 非法 JSON / 为空标记：置位后不再尝试 parse */
    private transient volatile boolean jsonPathParseFailed;

    private String clientAddr;

    /** 路径变量（由接口路由匹配注入） */
    private Map<String, String> pathVars;
}
