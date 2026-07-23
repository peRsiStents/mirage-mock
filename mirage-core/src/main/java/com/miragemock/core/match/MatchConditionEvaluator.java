package com.miragemock.core.match;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.miragemock.common.model.MatchCondition;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 匹配条件求值器。条件数组内为 AND 关系，全部满足才命中。空条件数组视为兜底（恒真）。
 */
@Component
public class MatchConditionEvaluator {

    /**
     * @return 全部条件满足返回 true；空列表返回 true（兜底）
     */
    public boolean match(List<MatchCondition> conditions, RequestSnapshot snapshot, Map<String, String> pathVars) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (MatchCondition c : conditions) {
            Object actual = extract(c.getSource(), c.getKey(), snapshot, pathVars);
            if (!applyOp(c.getOp(), actual, c.getValue())) {
                return false;
            }
        }
        return true;
    }

    private Object extract(String source, String key, RequestSnapshot s, Map<String, String> pathVars) {
        if (source == null || key == null) {
            return null;
        }
        switch (source) {
            case "header":
                return s.getHeaders() == null ? null : s.getHeaders().get(key.toLowerCase());
            case "query":
                return s.getQuery() == null ? null : s.getQuery().get(key);
            case "path":
                return pathVars == null ? null : pathVars.get(key);
            case "form":
                return s.getForm() == null ? null : s.getForm().get(key);
            case "body":
                return readJsonPath(s.getBodyRaw(), key);
            case "field":
                // HTTP 下与 body 同义；TCP 下读字段树
                Object fromField = s.getFields() == null ? null : readJsonPath(s.getBodyRaw(), key);
                return fromField;
            default:
                return null;
        }
    }

    private Object readJsonPath(String json, String jsonPath) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JsonPath.read(json, jsonPath);
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean applyOp(String op, Object actual, Object expected) {
        String a = actual == null ? null : ExpressionEvaluator.stringify(actual);
        if (op == null) {
            op = "eq";
        }
        switch (op) {
            case "eq":
                return a != null && a.equals(ExpressionEvaluator.stringify(expected));
            case "ne":
                return a == null || !a.equals(ExpressionEvaluator.stringify(expected));
            case "in":
                if (expected instanceof Collection) {
                    for (Object e : (Collection<Object>) expected) {
                        if (a != null && a.equals(ExpressionEvaluator.stringify(e))) {
                            return true;
                        }
                    }
                }
                return false;
            case "gt":
            case "gte":
            case "lt":
            case "lte": {
                Double c = cmp(a, expected);
                if (c == null) {
                    return false;
                }
                switch (op) {
                    case "gt":
                        return c > 0;
                    case "gte":
                        return c >= 0;
                    case "lt":
                        return c < 0;
                    default:
                        return c <= 0;
                }
            }
            case "regex":
                return a != null && Pattern.matches(ExpressionEvaluator.stringify(expected), a);
            case "contains":
                return a != null && a.contains(ExpressionEvaluator.stringify(expected));
            case "exists":
                return actual != null;
            case "not_exists":
                return actual == null;
            default:
                throw new IllegalArgumentException("未知匹配操作符: " + op);
        }
    }

    /** 数值比较；无法解析为数值时返回 null（令所有比较均为假） */
    private Double cmp(String a, Object expected) {
        Double da = toDouble(a);
        Double db = toDouble(expected == null ? null : ExpressionEvaluator.stringify(expected));
        if (da == null || db == null) {
            return null;
        }
        return (double) Double.compare(da, db);
    }

    private Double toDouble(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
