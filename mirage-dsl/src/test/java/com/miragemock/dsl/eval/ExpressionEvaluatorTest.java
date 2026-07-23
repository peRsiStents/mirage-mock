package com.miragemock.dsl.eval;

import com.miragemock.dsl.func.BuiltinFunctions;
import com.miragemock.dsl.func.FunctionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEvaluatorTest {

    private static ExpressionEvaluator ev;

    @BeforeAll
    static void setup() {
        FunctionRegistry reg = new FunctionRegistry();
        BuiltinFunctions.all().forEach(reg::register);
        ev = new ExpressionEvaluator(reg);
    }

    private EvalContext ctx() {
        return new EvalContext(Collections.emptyMap(), null);
    }

    private EvalContext ctx(Map<String, Object> vars) {
        return new EvalContext(vars, null);
    }

    @Test
    void md5_knownValue() {
        Object r = ev.evalTemplate("${md5(abc)}", ctx());
        assertEquals("900150983cd24fb0d6963f7d28e17f72", r);
    }

    @Test
    void sm3_knownValue() {
        Object r = ev.evalTemplate("${sm3(abc)}", ctx());
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", r);
    }

    @Test
    void plus_numeric() {
        assertEquals(3L, ev.evalExpression("1 + 2", ctx()));
    }

    @Test
    void plus_stringConcat() {
        assertEquals("ab", ev.evalExpression("'a' + 'b'", ctx()));
    }

    @Test
    void concat_withNested() {
        Object r = ev.evalTemplate("${concat('ORD', ${string(numeric,6)})}", ctx());
        String s = (String) r;
        assertTrue(Pattern.matches("ORD\\d{6}", s), "实际: " + s);
    }

    @Test
    void nestedFieldReference_md5() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("phone", "13800000000");
        Object viaRef = ev.evalExpression("md5(${phone})", ctx(vars));
        Object inline = ev.evalTemplate("${md5(13800000000)}", ctx());
        assertEquals(inline, viaRef);
    }

    @Test
    void decimal_format() {
        Object r = ev.evalTemplate("${decimal(1, 100, 2)}", ctx());
        assertTrue(Pattern.matches("\\d+\\.\\d{2}", String.valueOf(r)), "实际: " + r);
    }

    @Test
    void datetime_pattern() {
        Object r = ev.evalTemplate("${datetime(now-30d, now, yyyy-MM-dd HH:mm:ss)}", ctx());
        assertTrue(Pattern.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", String.valueOf(r)), "实际: " + r);
    }

    @Test
    void phone_idcard_format() {
        String phone = String.valueOf(ev.evalTemplate("${phone.cn_mobile}", ctx()));
        assertTrue(Pattern.matches("1\\d{10}", phone), "phone: " + phone);
        String idcard = String.valueOf(ev.evalTemplate("${idcard.cn}", ctx()));
        assertTrue(Pattern.matches("\\d{17}[\\dX]", idcard), "idcard: " + idcard);
        String uscc = String.valueOf(ev.evalTemplate("${uscc.cn}", ctx()));
        assertEquals(18, uscc.length(), "uscc: " + uscc);
    }
}
