package com.miragemock.core.match;

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

    /** 原始 query 字符串（录制回放转发/签名用；未解析原始串时为 null） */
    private String queryRaw;

    /** 表单参数 */
    private Map<String, String> form;

    /** 请求体原文（JSON 文本，供 JSONPath 匹配） */
    private String bodyRaw;

    /** 请求体解析后的字段树（JSON 对象） */
    private Map<String, Object> body;

    /** TCP 报文字段（M2）；HTTP 下与 body 同义 */
    private Map<String, Object> fields;

    private String clientAddr;

    /** 路径变量（由接口路由匹配注入） */
    private Map<String, String> pathVars;
}
