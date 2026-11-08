package com.miragemock.dsl.eval;

import com.miragemock.dsl.func.BuiltinFunctions;
import com.miragemock.dsl.func.FunctionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 表达式运算符用例：四则运算、优先级、括号、负数、除零、溢出、深度限制。
 */
class OperatorTest {

    private static ExpressionEvaluator ev;

    @BeforeAll
    static void setup() {
        FunctionRegistry reg = new FunctionRegistry();
        BuiltinFunctions.all().forEach(reg::register);
        ev = new ExpressionEvaluator(reg);
    }

    private EvalContext ctx() {
        return new EvalContext(new HashMap<>(), null);
    }

    @Test
    void subtraction() {
        assertEquals(2L, ev.evalExpression("5 - 3", ctx()));
    }

    @Test
    void multiplicationAndPrecedence() {
        // 100 - 20 * 3 = 40（乘法优先）
        assertEquals(40L, ev.evalExpression("100 - 20 * 3", ctx()));
    }

    @Test
    void parenthesesGrouping() {
        assertEquals(240L, ev.evalExpression("(100 - 20) * 3", ctx()));
    }

    @Test
    void divisionAndModulo() {
        assertEquals(5L, ev.evalExpression("10 / 2", ctx()));
        assertEquals(1L, ev.evalExpression("10 % 3", ctx()));
    }

    @Test
    void negativeLiteral() {
        assertEquals(-5L, ev.evalExpression("-5", ctx()));
        assertEquals(-2L, ev.evalExpression("3 + -5", ctx()));
    }

    @Test
    void divisionByZero_throws() {
        assertThrows(ExprException.class, () -> ev.evalExpression("10 / 0", ctx()));
    }

    @Test
    void overflow_throws() {
        assertThrows(ExprException.class,
                () -> ev.evalExpression("9223372036854775807 + 1", ctx()));
    }

    @Test
    void scientificNotation() {
        assertEquals(1500.0, (Double) ev.evalExpression("1.5e3", ctx()), 1e-9);
    }

    @Test
    void deepNesting_throws() {
        // 超过最大嵌套深度应报错，而不是 StackOverflowError
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("${");
        }
        sb.append("1");
        for (int i = 0; i < 200; i++) {
            sb.append("}");
        }
        assertThrows(ExprException.class, () -> ev.evalTemplate(sb.toString(), ctx()));
    }

    @Test
    void unknownPrefixedVariable_returnsEmptyString() {
        // field./path./var. 前缀未命中 → 空串（占位符语义），而非把名字当字面量输出
        assertEquals("", ev.evalExpression("field.notExist", ctx()));
        assertEquals("", ev.evalExpression("path.notExist", ctx()));
        assertEquals("", ev.evalExpression("var.notExist", ctx()));
    }

    @Test
    void unknownBareIdentifier_returnsLiteral() {
        // 裸标识符仍按字面量处理（兼容 alpha_num 等枚举 token）
        assertEquals("alpha_num", ev.evalExpression("alpha_num", ctx()));
    }

    @Test
    void knownVariable_resolves() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("field.serialNo", "S001");
        assertEquals("S001", ev.evalExpression("field.serialNo", new EvalContext(vars, null)));
    }
}
