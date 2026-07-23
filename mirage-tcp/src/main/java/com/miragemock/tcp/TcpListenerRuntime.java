package com.miragemock.tcp;

import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.tcp.codec.MessageParser;
import com.miragemock.tcp.codec.MessageParserRegistry;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 监听器运行时配置：解析后的解析器、格式配置、是否分帧、推送配置。
 */
@Getter
public class TcpListenerRuntime {

    private final TcpListener listener;
    private final MessageParser parser;
    private final Map<String, Object> formatConfig;
    private final boolean framed;
    private final List<Map<String, Object>> onConnect;
    private final List<Map<String, Object>> schedule;

    @SuppressWarnings("unchecked")
    public TcpListenerRuntime(TcpListener listener, MessageParserRegistry registry) {
        this.listener = listener;
        this.parser = registry.resolve(listener.getMessageFormat());
        this.formatConfig = parseMap(listener.getMessageFormatConfig());
        this.framed = !"close_end".equalsIgnoreCase(typeOf(listener.getFrameConfig()));
        this.onConnect = parseList(readPush(listener).get("onConnect"));
        this.schedule = parseList(readPush(listener).get("schedule"));
    }

    private static Map<String, Object> parseMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return JsonUtils.parseMap(json);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseList(Object o) {
        if (o instanceof List) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : (List<Object>) o) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static Map<String, Object> readPush(TcpListener listener) {
        Map<String, Object> empty = new java.util.LinkedHashMap<>();
        if (listener.getPushConfig() == null || listener.getPushConfig().isEmpty()) {
            return empty;
        }
        Map<String, Object> parsed = parseMap(listener.getPushConfig());
        return parsed == null ? empty : parsed;
    }

    private static String typeOf(String frameConfig) {
        if (frameConfig == null || frameConfig.trim().isEmpty()) {
            return "length_field";
        }
        try {
            Map<String, Object> m = JsonUtils.parseMap(frameConfig);
            Object t = m == null ? null : m.get("type");
            return t == null ? "length_field" : t.toString();
        } catch (Exception e) {
            return "length_field";
        }
    }
}
