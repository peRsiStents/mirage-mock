package com.miragemock.tcp.codec;

import com.miragemock.common.util.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JSON 报文：body 为 JSON 文本。
 */
public class JsonMessageParser implements MessageParser {

    @Override
    public String code() {
        return "json";
    }

    @Override
    public Map<String, Object> parse(byte[] frame, Map<String, Object> formatConfig) {
        String text = frame == null ? "" : new String(frame, StandardCharsets.UTF_8);
        if (text.trim().isEmpty()) {
            // 空帧：返回空字段树，避免下游（路由提取/匹配/日志）出现 null 传播
            return new java.util.LinkedHashMap<>();
        }
        try {
            return JsonUtils.parseMap(text);
        } catch (Exception e) {
            // 非 JSON 报文，整体作为 _raw
            return JsonUtils.parseMap("{\"_raw\":" + JsonUtils.toJson(text) + "}");
        }
    }

    @Override
    public byte[] encode(Map<String, Object> fields, Map<String, Object> formatConfig) {
        return JsonUtils.toJson(fields).getBytes(StandardCharsets.UTF_8);
    }
}
