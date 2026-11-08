package com.miragemock.core.match;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.miragemock.common.model.MatchCondition;
import com.miragemock.core.cache.CompiledRule;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 匹配条件求值器。条件数组内为 AND 关系，全部满足才命中。空条件数组视为兜底（恒真）。
 *
 * <p>性能约定：走 {@link #match(CompiledRule, RequestSnapshot, Map)} 时使用 CompiledRule
 * 的条件级编译缓存（regex 加载期预编译、JSONPath 懒编译一次复用）；请求 body 对同一
 * RequestSnapshot 只 parse 一次（DocumentContext 缓存在快照上）。
 */
@Component
public class MatchConditionEvaluator {

    /** regex 匹配前对匹配值截断的上限，防止超长值放大回溯开销 */
    static final int REGEX_MATCH_LIMIT = 4096;

    /**
     * 主入口：使用规则编译缓存。
     *
     * @return 全部条件满足返回 true；空列表返回 true（兜底）
     */
    public boolean match(CompiledRule rule, RequestSnapshot snapshot, Map<String, String> pathVars) {
        if (rule == null) {
            return false;
        }
        return match(rule.getConditions(), rule, snapshot, pathVars);
    }

    /**
     * 兼容入口：无编译缓存（正则/JSONPath 每次编译），body 仍请求级只 parse 一次。
     */
    public boolean match(List<MatchCondition> conditions, RequestSnapshot snapshot, Map<String, String> pathVars) {
        return match(conditions, null, snapshot, pathVars);
    }

    private boolean match(List<MatchCondition> conditions, CompiledRule rule,
                          RequestSnapshot snapshot, Map<String, String> pathVars) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (int i = 0; i < conditions.size(); i++) {
            MatchCondition c = conditions.get(i);
            Object actual = extract(c.getSource(), c.getKey(), snapshot, pathVars, rule, i);
            Pattern regex = rule == null ? null : rule.regexOf(i);
            if (!applyOp(c.getOp(), actual, c.getValue(), regex)) {
                return false;
            }
        }
        return true;
    }

    private Object extract(String source, String key, RequestSnapshot s, Map<String, String> pathVars,
                           CompiledRule rule, int conditionIndex) {
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
            case "field":
                // field 与 body 一致：直接对 bodyRaw 做 JSONPath 读取，不依赖 Content-Type；
                // TCP 下 bodyRaw 即报文字段树的 JSON 序列化，行为不变
                return readJsonPath(s, key, rule, conditionIndex);
            default:
                return null;
        }
    }

    /**
     * body JSONPath 读取：请求级 DocumentContext 只 parse 一次；
     * 有 CompiledRule 时优先使用其懒编译缓存的路径。
     */
    private Object readJsonPath(RequestSnapshot s, String pathExpr, CompiledRule rule, int conditionIndex) {
        DocumentContext ctx = bodyContext(s);
        if (ctx == null) {
            return null;
        }
        try {
            if (rule != null) {
                JsonPath compiled = rule.jsonPathOf(conditionIndex, pathExpr);
                if (compiled == null) {
                    // 非法路径表达式：视为路径不存在
                    return null;
                }
                return ctx.read(compiled);
            }
            return ctx.read(pathExpr);
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 请求级 body 懒解析：parse 失败/为空时置标记，后续条件不再重复尝试 */
    private DocumentContext bodyContext(RequestSnapshot s) {
        if (s.isJsonPathParseFailed()) {
            return null;
        }
        DocumentContext ctx = s.getJsonPathContext();
        if (ctx == null) {
            String raw = s.getBodyRaw();
            if (raw == null || raw.isEmpty()) {
                s.setJsonPathParseFailed(true);
                return null;
            }
            try {
                ctx = JsonPath.parse(raw);
            } catch (Exception e) {
                s.setJsonPathParseFailed(true);
                return null;
            }
            s.setJsonPathContext(ctx);
        }
        return ctx;
    }

    private boolean applyOp(String op, Object actual, Object expected, Pattern compiledRegex) {
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
            case "regex": {
                if (a == null) {
                    return false;
                }
                // 匹配值截断后再匹配，防止超长值放大回溯开销
                String v = a.length() > REGEX_MATCH_LIMIT ? a.substring(0, REGEX_MATCH_LIMIT) : a;
                Pattern p = compiledRegex != null ? compiledRegex : Pattern.compile(ExpressionEvaluator.stringify(expected));
                return p.matcher(v).matches();
            }
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
