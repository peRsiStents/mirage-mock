package com.miragemock.tcp.codec;

import com.miragemock.dsl.eval.ExpressionEvaluator;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * K=V 键值对文本：config {pairSep:"&", kvSep:"="}。
 */
public class KeyValueMessageParser implements MessageParser {

    @Override
    public String code() {
        return "key_value";
    }

    @Override
    public Map<String, Object> parse(byte[] frame, Map<String, Object> formatConfig) {
        String pairSep = getStr(formatConfig, "pairSep", "&");
        String kvSep = getStr(formatConfig, "kvSep", "=");
        String text = new String(frame, StandardCharsets.UTF_8);
        Map<String, Object> map = new LinkedHashMap<>();
        if (text.isEmpty()) {
            return map;
        }
        for (String pair : text.split(java.util.regex.Pattern.quote(pairSep))) {
            int idx = pair.indexOf(kvSep);
            if (idx < 0) {
                map.put(pair, "");
            } else {
                map.put(pair.substring(0, idx), pair.substring(idx + kvSep.length()));
            }
        }
        return map;
    }

    @Override
    public byte[] encode(Map<String, Object> fields, Map<String, Object> formatConfig) {
        String pairSep = getStr(formatConfig, "pairSep", "&");
        String kvSep = getStr(formatConfig, "kvSep", "=");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) {
                sb.append(pairSep);
            }
            sb.append(e.getKey()).append(kvSep).append(ExpressionEvaluator.stringify(e.getValue()));
            first = false;
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String getStr(Map<String, Object> cfg, String key, String def) {
        if (cfg == null) {
            return def;
        }
        Object v = cfg.get(key);
        return v == null ? def : v.toString();
    }
}
