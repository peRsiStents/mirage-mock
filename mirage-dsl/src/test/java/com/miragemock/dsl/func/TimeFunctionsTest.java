package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.spi.MockFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时间函数边界：引号参数、显式日期解析失败、非法 pattern、身份证同证性。
 */
class TimeFunctionsTest {

    private static ExpressionEvaluator ev;

    @BeforeAll
    static void setup() {
        com.miragemock.dsl.func.FunctionRegistry reg = new com.miragemock.dsl.func.FunctionRegistry();
        BuiltinFunctions.all().forEach(reg::register);
        ev = new ExpressionEvaluator(reg);
    }

    private EvalContext ctx() {
        return new EvalContext(new HashMap<>(), null);
    }

    @Test
    void date_withQuotedArgs() {
        // 关键回归：date('2020-01-01','2020-12-31') 原本静默回退到近 30 天
        Object r = ev.evalTemplate("${date('2020-01-01', '2020-12-31')}", ctx());
        String s = String.valueOf(r);
        assertTrue(s.startsWith("2020-"), "实际: " + s);
    }

    @Test
    void date_withUnquotedArgs() {
        Object r = ev.evalTemplate("${date(2020-01-01, 2020-12-31)}", ctx());
        assertTrue(String.valueOf(r).startsWith("2020-"), "实际: " + r);
    }

    @Test
    void date_explicitInvalidDate_throws() {
        // 显式给了非法日期 → 报错，不再静默回退
        assertThrows(ExprException.class, () -> ev.evalTemplate("${date('not-a-date')}", ctx()));
    }

    @Test
    void date_invalidPattern_throws() {
        assertThrows(ExprException.class,
                () -> ev.evalTemplate("${date(now-1d, now, 'yyyy-QQQQQQ')}", ctx()));
    }

    @Test
    void date_relativeRange() {
        Object r = ev.evalTemplate("${date(now-30d, now)}", ctx());
        assertTrue(String.valueOf(r).matches("\\d{4}-\\d{2}-\\d{2}"), "实际: " + r);
    }

    @Test
    void idcard_birthdate_sameCertificate() {
        // idcard.cn 与 idcard.birthdate 必须来自同一张证（走内部暂存区）
        EvalContext c = ctx();
        Object id = ev.evalTemplate("${idcard.cn}", c);
        Object birth = ev.evalTemplate("${idcard.birthdate}", c);
        String idStr = String.valueOf(id);
        String birthStr = String.valueOf(birth);
        String raw = idStr.substring(6, 10) + "-" + idStr.substring(10, 12) + "-" + idStr.substring(12, 14);
        assertEquals(raw, birthStr, "出生日期应与身份证一致");
    }

    @Test
    void idcard_stashDoesNotPolluteVariables() {
        // 内部暂存键不应出现在用户变量表里
        EvalContext c = ctx();
        ev.evalTemplate("${idcard.cn}", c);
        assertTrue(c.getVariables().isEmpty(), "变量表被内部键污染: " + c.getVariables().keySet());
    }

    @Test
    void idcard_birthdate_customPattern() {
        EvalContext c = ctx();
        ev.evalTemplate("${idcard.cn}", c);
        Object birth = ev.evalTemplate("${idcard.birthdate('yyyy/MM/dd')}", c);
        assertTrue(String.valueOf(birth).matches("\\d{4}/\\d{2}/\\d{2}"), "实际: " + birth);
    }

    @Test
    void int_boundsValidation() {
        // int() 参数非法应报 ExprException，而非裸异常
        assertThrows(ExprException.class, () -> ev.evalTemplate("${int(100, 1)}", ctx()));
        assertThrows(ExprException.class, () -> ev.evalTemplate("${int('abc', 5)}", ctx()));
    }

    @Test
    void decimal_scaleValidation() {
        assertThrows(ExprException.class, () -> ev.evalTemplate("${decimal(1, 10, 99)}", ctx()));
    }
}
