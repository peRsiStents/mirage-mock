package com.miragemock.tcp.codec;

import com.miragemock.dsl.eval.ExpressionEvaluator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定长字段切分：config {fields:[{name,len}, ...]}。按 UTF-8 解码。
 *
 * <p>长度语义：{@code len} 为**字节数**（与线路上占用的字节一致）。
 * 原实现按字符数补齐，中文（1 汉字 = 3 字节）会导致后续字段整体错位。
 *
 * <p>解析侧默认 trim（去首尾空白），可用 formatConfig 的 {@code trimFields=false} 关闭
 * ——空格本身是有效数据时不能去。
 */
public class FixedFieldsMessageParser implements MessageParser {

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

    /** 是否对解析结果 trim（默认 true，保持历史行为） */
    private boolean trimFields(Map<String, Object> cfg) {
        if (cfg == null) {
            return true;
        }
        Object v = cfg.get("trimFields");
        if (v == null) {
            return true;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return !"false".equalsIgnoreCase(v.toString().trim());
    }

    @Override
    public String code() {
        return "fixed_fields";
    }

    @Override
    public Map<String, Object> parse(byte[] frame, Map<String, Object> formatConfig) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean trim = trimFields(formatConfig);
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
            String value = new String(seg, StandardCharsets.UTF_8);
            result.put(name, trim ? value.trim() : value);
            pos = end;
            // 数据不足时后续字段补空串（而不是缺键），
            // 保证 encode/parse 的字段集合稳定，模板取值不会 NPE
        }
        return result;
    }

    @Override
    public byte[] encode(Map<String, Object> fields, Map<String, Object> formatConfig) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (Map<String, Object> f : fieldsOf(formatConfig)) {
            String name = String.valueOf(f.get("name"));
            int len = toInt(f.get("len"), 0);
            String val = ExpressionEvaluator.stringify(fields.get(name));
            byte[] bytes = (val == null ? "" : val).getBytes(StandardCharsets.UTF_8);
            if (len > 0) {
                // 按字节补齐/截断（保证定长字段在线路上的字节数稳定）
                byte[] fixed = new byte[len];
                if (bytes.length >= len) {
                    System.arraycopy(bytes, 0, fixed, 0, len);
                } else {
                    System.arraycopy(bytes, 0, fixed, 0, bytes.length);
                    // 剩余部分补空格（ASCII 单字节，不影响后续字段对齐）
                    for (int i = bytes.length; i < len; i++) {
                        fixed[i] = ' ';
                    }
                }
                bytes = fixed;
            }
            out.write(bytes, 0, bytes.length);
        }
        return out.toByteArray();
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
