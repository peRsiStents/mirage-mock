package com.miragemock.tcp.codec;

import java.util.Map;

/**
 * 路由 / 流水号提取：从解析后的字段 Map 按表达式取值。
 *
 * <p>支持语法：
 * <ul>
 *   <li>JSON：$.transCode、$.a.b（按嵌套路径导航）</li>
 *   <li>键值：kv:trxnType</li>
 *   <li>定长字段名：field:orgNo</li>
 * </ul>
 */
public final class RouteExtractor {

    private RouteExtractor() {
    }

    public static String extract(Map<String, Object> fields, String expr) {
        if (fields == null || expr == null || expr.isEmpty()) {
            return null;
        }
        if (expr.startsWith("$.")) {
            String[] parts = expr.substring(2).split("\\.");
            Object cur = fields;
            for (String p : parts) {
                if (cur instanceof Map) {
                    cur = ((Map<?, ?>) cur).get(p);
                } else {
                    cur = null;
                }
                if (cur == null) {
                    return null;
                }
            }
            return cur.toString();
        }
        if (expr.startsWith("field:") || expr.startsWith("kv:")) {
            Object v = fields.get(expr.substring(expr.indexOf(':') + 1));
            return v == null ? null : v.toString();
        }
        Object v = fields.get(expr);
        return v == null ? null : v.toString();
    }
}
