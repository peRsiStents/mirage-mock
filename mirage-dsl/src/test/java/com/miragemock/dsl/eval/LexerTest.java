package com.miragemock.dsl.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 词法器边界用例：转义、unicode、科学计数法、畸形数字、未闭合字符串。
 */
class LexerTest {

    private static Lexer lexer(String src) {
        return new Lexer(src);
    }

    @Test
    void unknownEscape_keepsBackslash() {
        // 关键回归：'\d' 必须保留为反斜杠+d，否则 regex('\\d{11}') 会静默生成 "ddddddddddd"
        Lexer l = lexer("'\\d'");
        assertEquals(2, l.tokenize().size(), "应产出 1 个 token + EOF");
        assertEquals("\\d", l.tokenize().get(0).value, "反斜杠不应被吞掉");
    }

    @Test
    void unknownEscape_keepsBackslashInTemplate() {
        // 更贴近真实用法：表达式字符串里的正则
        Lexer l = lexer("'\\d{11}'");
        assertEquals("\\d{11}", l.tokenize().get(0).value);
    }

    @Test
    void knownEscapes() {
        assertEquals("\n", lexer("'\\n'").tokenize().get(0).value);
        assertEquals("\t", lexer("'\\t'").tokenize().get(0).value);
        assertEquals("\\", lexer("'\\\\'").tokenize().get(0).value);
        assertEquals("'", lexer("'\\''").tokenize().get(0).value);
    }

    @Test
    void unicodeEscape() {
        assertEquals("中", lexer("'\\u4e2d'").tokenize().get(0).value);
    }

    @Test
    void unicodeEscape_invalid() {
        assertThrows(ExprException.class, () -> lexer("'\\uZZZZ'").tokenize());
        assertThrows(ExprException.class, () -> lexer("'\\u12'").tokenize());
    }

    @Test
    void unclosedString() {
        assertThrows(ExprException.class, () -> lexer("'abc").tokenize());
    }

    @Test
    void scientificNotation() {
        Lexer l = lexer("1.5e3");
        assertEquals(1500.0, (Double) l.tokenize().get(0).value, 1e-9);
    }

    @Test
    void scientificNotation_negativeExponent() {
        Lexer l = lexer("1.5e-3");
        assertEquals(0.0015, (Double) l.tokenize().get(0).value, 1e-12);
    }

    @Test
    void malformedNumber_throws() {
        // 畸形数字应报 ExprException，而非裸 NumberFormatException
        assertThrows(ExprException.class, () -> lexer("1.2.3").tokenize());
        assertThrows(ExprException.class, () -> lexer("1e").tokenize());
    }

    @Test
    void longIntegerOverflow_throws() {
        // 超过 long 范围：应报 ExprException
        assertThrows(ExprException.class, () -> lexer("99999999999999999999").tokenize());
    }

    @Test
    void minusTokenExists() {
        // 减号必须有 token（否则表达式里根本无法写负数/减法）
        Lexer l = lexer("5 - 3");
        assertTrue(l.tokenize().stream().anyMatch(t -> t.type == Lexer.Type.MINUS), "应产出 MINUS token");
    }
}
