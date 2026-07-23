package com.miragemock.core.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.func.BuiltinFunctions;
import com.miragemock.dsl.func.FunctionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateRendererTest {

    private static TemplateRenderer renderer;
    private static ExpressionEvaluator evaluator;

    @BeforeAll
    static void setup() {
        FunctionRegistry reg = new FunctionRegistry();
        BuiltinFunctions.all().forEach(reg::register);
        evaluator = new ExpressionEvaluator(reg);
        renderer = new TemplateRenderer(evaluator);
    }

    private EvalContext ctx() {
        return new EvalContext(Collections.emptyMap(), null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> renderBody(String json) {
        JsonNode node = JsonUtils.readTree(json);
        RenderedResponse rr = renderer.render(node, Collections.emptyMap(), ctx());
        return (Map<String, Object>) rr.getBody();
    }

    @Test
    void simpleGenerators() {
        Map<String, Object> body = renderBody(
                "{\"status\":200,\"body\":{\"code\":\"0000\",\"data\":{\"phone\":\"${phone.cn_mobile}\",\"id\":\"${idcard.cn}\"}}}");
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertTrue(Pattern.matches("1\\d{10}", String.valueOf(data.get("phone"))), "phone: " + data.get("phone"));
        assertTrue(Pattern.matches("\\d{17}[\\dX]", String.valueOf(data.get("id"))), "id: " + data.get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fieldDependency_topoSort() {
        // sign 依赖 data.phone，须先求值 phone
        Map<String, Object> body = renderBody(
                "{\"body\":{\"data\":{\"phone\":\"${phone.cn_mobile}\",\"sign\":\"${md5(${data.phone})}\"}}}");
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        String phone = String.valueOf(data.get("phone"));
        String sign = String.valueOf(data.get("sign"));
        String expected = String.valueOf(evaluator.evalTemplate("${md5(" + phone + ")}", ctx()));
        assertEquals(expected, sign);
    }

    @Test
    void repeat_generatesArray() {
        Map<String, Object> body = renderBody(
                "{\"body\":{\"orders\":\"${repeat(2, {\\\"orderNo\\\":\\\"${string(numeric,6)}\\\"})}\"}}");
        Object orders = body.get("orders");
        assertTrue(orders instanceof List, "orders 应为数组: " + orders);
        List<?> list = (List<?>) orders;
        assertEquals(2, list.size());
        for (Object o : list) {
            Map<String, Object> m = (Map<String, Object>) o;
            assertTrue(Pattern.matches("\\d{6}", String.valueOf(m.get("orderNo"))), "orderNo: " + m.get("orderNo"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pathVariable_reference() {
        JsonNode node = JsonUtils.readTree("{\"body\":{\"userId\":\"${path.userId}\"}}");
        java.util.Map<String, Object> baseVars = new java.util.HashMap<>();
        baseVars.put("path.userId", "U10086");
        RenderedResponse rr = renderer.render(node, baseVars, ctx());
        assertEquals("U10086", ((Map<String, Object>) rr.getBody()).get("userId"));
    }
}
