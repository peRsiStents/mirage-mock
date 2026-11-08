package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.ExprException;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 正则生成器边界：量词封顶、取反字符类、倒序区间、畸形量词。
 */
class RegexGenTest {

    @Test
    void digitClass() {
        String s = RegexGen.generate("\\d{11}");
        assertTrue(Pattern.matches("\\d{11}", s), "实际: " + s);
    }

    @Test
    void rangeClass() {
        String s = RegexGen.generate("[a-c]{3}");
        assertTrue(Pattern.matches("[a-c]{3}", s), "实际: " + s);
    }

    @Test
    void quantifierRange() {
        String s = RegexGen.generate("a{2,4}");
        assertTrue(Pattern.matches("a{2,4}", s), "实际: " + s);
    }

    @Test
    void quantifierTooLarge_throws() {
        // 关键回归：{100000000} 原本会循环 1 亿次拖死线程
        assertThrows(ExprException.class, () -> RegexGen.generate("\\d{100000000}"));
    }

    @Test
    void quantifierMalformed_throws() {
        assertThrows(ExprException.class, () -> RegexGen.generate("\\d{2,x}"));
        assertThrows(ExprException.class, () -> RegexGen.generate("\\d{}"));
        assertThrows(ExprException.class, () -> RegexGen.generate("\\d{-1}"));
    }

    @Test
    void quantifierUnclosed_throws() {
        assertThrows(ExprException.class, () -> RegexGen.generate("\\d{2"));
    }

    @Test
    void negatedClass() {
        String s = RegexGen.generate("[^0-9]{3}");
        assertTrue(Pattern.matches("[^0-9]{3}", s), "实际: " + s);
    }

    @Test
    void reversedRange_throws() {
        // 倒序区间原本静默输出空格
        assertThrows(ExprException.class, () -> RegexGen.generate("[z-a]"));
    }

    @Test
    void emptyClass_throws() {
        assertThrows(ExprException.class, () -> RegexGen.generate("[]"));
    }

    @Test
    void outputLengthCapped() {
        // 输出总长上限：多段量词叠加超过 64KB 时报错
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("\\d{1000}");
        }
        assertThrows(ExprException.class, () -> RegexGen.generate(sb.toString()));
    }

    @Test
    void literalText() {
        assertEquals("ORD", RegexGen.generate("ORD"));
    }
}
