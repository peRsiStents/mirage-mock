package com.miragemock.tcp;

import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.tcp.codec.MessageParser;
import com.miragemock.tcp.codec.MessageParserRegistry;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监听器运行时配置：解析后的解析器、格式配置、是否分帧、推送配置。
 *
 * <p>构造时一次性完成配置解析与校验（{@link #validate()}），
 * 避免配置错误只能在「每条新连接」上逐次暴露。
 */
@Getter
public class TcpListenerRuntime {

    private final TcpListener listener;
    private final MessageParser parser;
    private final Map<String, Object> formatConfig;
    private final boolean framed;
    private final List<Map<String, Object>> onConnect;
    private final List<Map<String, Object>> schedule;
    /** 帧配置解析结果（每连接复用，避免重复 JSON 解析） */
    private final Map<String, Object> frameConfigMap;

    @SuppressWarnings("unchecked")
    public TcpListenerRuntime(TcpListener listener, MessageParserRegistry registry) {
        this.listener = listener;
        this.parser = registry.resolve(listener.getMessageFormat());
        this.formatConfig = parseMap(listener.getMessageFormatConfig());
        Map<String, Object> push = readPush(listener);
        this.onConnect = parseList(push.get("onConnect"));
        this.schedule = parseList(push.get("schedule"));
        this.frameConfigMap = parseFrameConfig(listener.getFrameConfig());
        this.framed = !"close_end".equalsIgnoreCase(typeOf(listener.getFrameConfig()));
    }

    /**
     * 校验监听器配置；非法则抛 IllegalArgumentException（启动前拒绝，而非每连接报错）。
     */
    public void validate() {
        if (listener.getPort() == null || listener.getPort() <= 0 || listener.getPort() > 65535) {
            throw new IllegalArgumentException("监听器端口非法: " + listener.getPort());
        }
        String type = typeOf(listener.getFrameConfig());
        Map<String, Object> cfg = frameConfigMap;
        switch (type) {
            case "length_field": {
                int lenBytes = intOf(cfg.get("lenBytes"), 4);
                if (lenBytes != 1 && lenBytes != 2 && lenBytes != 3 && lenBytes != 4 && lenBytes != 8) {
                    throw new IllegalArgumentException("length_field 的 lenBytes 必须是 1/2/3/4/8，当前: " + lenBytes);
                }
                int offset = intOf(cfg.get("offset"), 0);
                if (offset < 0) {
                    throw new IllegalArgumentException("length_field 的 offset 不能为负: " + offset);
                }
                break;
            }
            case "fixed": {
                int length = intOf(cfg.get("length"), 0);
                if (length <= 0) {
                    throw new IllegalArgumentException("fixed 帧的长度必须为正数，当前: " + length);
                }
                break;
            }
            case "delimiter": {
                String value = str(cfg.get("value"), "\n");
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("delimiter 帧的分隔符不能为空");
                }
                if (value.startsWith("0x") || value.startsWith("0X")) {
                    // hex 分隔符：校验合法性（奇数长度 / 非 hex 字符）
                    com.miragemock.dsl.crypto.Codec.hexDecode(value.substring(2));
                }
                break;
            }
            case "close_end":
                break;
            default:
                throw new IllegalArgumentException("未知的帧类型: " + type
                        + "（支持 length_field / delimiter / fixed / close_end）");
        }
    }

    private static Map<String, Object> parseFrameConfig(String frameConfig) {
        if (frameConfig == null || frameConfig.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> m = JsonUtils.parseMap(frameConfig);
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception e) {
            throw new IllegalArgumentException("帧配置不是合法 JSON: " + e.getMessage(), e);
        }
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
        Map<String, Object> empty = new LinkedHashMap<>();
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

    private static int intOf(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }
}
