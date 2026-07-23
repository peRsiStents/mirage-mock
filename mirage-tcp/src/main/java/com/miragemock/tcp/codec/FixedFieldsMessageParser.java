package com.miragemock.tcp.codec;

import com.miragemock.dsl.eval.ExpressionEvaluator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定长字段切分：config {fields:[{name,len}, ...]}。按 UTF-8 解码。
 */
public class FixedFieldsMessageParser implements MessageParser {

    @Override
    public String code() {
        return "fixed_fields";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fieldsOf(Map<String, Object> cfg) {
        if (cfg == null) {
            return new ArrayList<>();
        }
        Object f = cfg.get("fields");
        if (f instanceof List) {
            return (List<Map<String, Object>>) f;
        }
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> parse(byte[] frame, Map<String, Object> formatConfig) {
        Map<String, Object> result = new LinkedHashMap<>();
        int pos = 0;
        for (Map<String, Object> f : fieldsOf(formatConfig)) {
            String name = String.valueOf(f.get("name"));
            int len = toInt(f.get("len"), 0);
            if (len <= 0) {
                continue;
            }
            int end = Math.min(pos + len, frame.length);
            byte[] seg = new byte[end - pos];
            System.arraycopy(frame, pos, seg, 0, seg.length);
            result.put(name, new String(seg, StandardCharsets.UTF_8).trim());
            pos = end;
            if (pos >= frame.length) {
                break;
            }
        }
        return result;
    }

    @Override
    public byte[] encode(Map<String, Object> fields, Map<String, Object> formatConfig) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> f : fieldsOf(formatConfig)) {
            String name = String.valueOf(f.get("name"));
            int len = toInt(f.get("len"), 0);
            String val = ExpressionEvaluator.stringify(fields.get(name));
            if (len > 0) {
                if (val.length() > len) {
                    val = val.substring(0, len);
                } else {
                    while (val.length() < len) {
                        val = val + " ";
                    }
                }
            }
            sb.append(val);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private int toInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
