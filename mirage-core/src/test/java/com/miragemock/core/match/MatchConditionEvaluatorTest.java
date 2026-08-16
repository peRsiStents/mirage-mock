package com.miragemock.core.match;

import com.miragemock.common.model.MatchCondition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 匹配条件求值器单测：覆盖 header/query/path/form/body 五种 source 与全部操作符，
 * AND 语义与兜底语义。此类为规则命中正确性的核心，必须钉死。
 */
class MatchConditionEvaluatorTest {

    private final MatchConditionEvaluator matcher = new MatchConditionEvaluator();

    private MatchCondition cond(String source, String key, String op, Object value) {
        MatchCondition c = new MatchCondition();
        c.setSource(source);
        c.setKey(key);
        c.setOp(op);
        c.setValue(value);
        return c;
    }

    private RequestSnapshot snapshot() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-env", "gray");
        headers.put("authorization", "Bearer abc");
        Map<String, String> query = new HashMap<>();
        query.put("type", "1");
        query.put("page", "2");
        Map<String, String> form = new HashMap<>();
        form.put("username", "alice");
        Map<String, String> pathVars = new HashMap<>();
        pathVars.put("userId", "10086");
        return RequestSnapshot.builder()
                .method("GET")
                .path("/api/user/10086")
                .headers(headers)
                .query(query)
                .form(form)
                .bodyRaw("{\"user\":{\"age\":25},\"orderNo\":\"ORD1234567890\"}")
                .build();
    }

    // ============ source 提取 ============

    @Test
    void header_eq() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("header", "X-Env", "eq", "gray")), snapshot(), new HashMap<>()));
        assertFalse(matcher.match(Collections.singletonList(
                cond("header", "X-Env", "eq", "blue")), snapshot(), new HashMap<>()));
    }

    @Test
    void header_keyCaseInsensitive() {
        // 快照内 key 已转小写；条件里写原大小写也应命中
        assertTrue(matcher.match(Collections.singletonList(
                cond("header", "Authorization", "eq", "Bearer abc")), snapshot(), new HashMap<>()));
    }

    @Test
    void query_eq() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("query", "type", "eq", "1")), snapshot(), new HashMap<>()));
    }

    @Test
    void path_var_exists() {
        Map<String, String> pathVars = new HashMap<>();
        pathVars.put("userId", "10086");
        assertTrue(matcher.match(Collections.singletonList(
                cond("path", "userId", "exists", null)), snapshot(), pathVars));
    }

    @Test
    void form_eq() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("form", "username", "eq", "alice")), snapshot(), new HashMap<>()));
    }

    @Test
    void body_jsonPath() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("body", "$.user.age", "gte", 18)), snapshot(), new HashMap<>()));
        assertFalse(matcher.match(Collections.singletonList(
                cond("body", "$.user.age", "lt", 18)), snapshot(), new HashMap<>()));
    }

    @Test
    void body_jsonPath_notFound() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("body", "$.missing", "not_exists", null)), snapshot(), new HashMap<>()));
    }

    // ============ 操作符 ============

    @Test
    void op_in_array() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("query", "type", "in", Arrays.asList("0", "1"))), snapshot(), new HashMap<>()));
        assertFalse(matcher.match(Collections.singletonList(
                cond("query", "type", "in", Arrays.asList("0", "9"))), snapshot(), new HashMap<>()));
    }

    @Test
    void op_ne() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("query", "type", "ne", "2")), snapshot(), new HashMap<>()));
        assertFalse(matcher.match(Collections.singletonList(
                cond("query", "type", "ne", "1")), snapshot(), new HashMap<>()));
    }

    @Test
    void op_numeric_compare() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("body", "$.user.age", "gt", 20)), snapshot(), new HashMap<>()));
        assertTrue(matcher.match(Collections.singletonList(
                cond("body", "$.user.age", "lte", 25)), snapshot(), new HashMap<>()));
        assertFalse(matcher.match(Collections.singletonList(
                cond("body", "$.user.age", "lt", 25)), snapshot(), new HashMap<>()));
        // 非数值比较：一律不命中
        assertFalse(matcher.match(Collections.singletonList(
                cond("header", "X-Env", "gt", 0)), snapshot(), new HashMap<>()));
    }

    @Test
    void op_regex_fullMatch() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("body", "$.orderNo", "regex", "^ORD\\d{10}$")), snapshot(), new HashMap<>()));
        assertFalse(matcher.match(Collections.singletonList(
                cond("body", "$.orderNo", "regex", "^ORD\\d{5}$")), snapshot(), new HashMap<>()));
    }

    @Test
    void op_contains() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("query", "type", "contains", "")), snapshot(), new HashMap<>()));
        assertTrue(matcher.match(Collections.singletonList(
                cond("body", "$.orderNo", "contains", "ORD")), snapshot(), new HashMap<>()));
    }

    @Test
    void op_exists_notExists() {
        assertTrue(matcher.match(Collections.singletonList(
                cond("query", "type", "exists", null)), snapshot(), new HashMap<>()));
        assertFalse(matcher.match(Collections.singletonList(
                cond("query", "nope", "exists", null)), snapshot(), new HashMap<>()));
    }

    // ============ 组合与兜底 ============

    @Test
    void allConditions_and() {
        List<MatchCondition> conds = Arrays.asList(
                cond("header", "X-Env", "eq", "gray"),
                cond("query", "type", "eq", "1"),
                cond("body", "$.user.age", "gte", 18));
        assertTrue(matcher.match(conds, snapshot(), new HashMap<>()));

        List<MatchCondition> conds2 = Arrays.asList(
                cond("header", "X-Env", "eq", "gray"),
                cond("query", "type", "eq", "9")); // 第二条不满足
        assertFalse(matcher.match(conds2, snapshot(), new HashMap<>()));
    }

    @Test
    void emptyConditions_isFallback() {
        assertTrue(matcher.match(null, snapshot(), new HashMap<>()));
        assertTrue(matcher.match(Collections.emptyList(), snapshot(), new HashMap<>()));
    }

    @Test
    void unknownOp_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> matcher.match(Collections.singletonList(
                        cond("header", "X-Env", "no_such_op", "gray")), snapshot(), new HashMap<>()));
    }
}
