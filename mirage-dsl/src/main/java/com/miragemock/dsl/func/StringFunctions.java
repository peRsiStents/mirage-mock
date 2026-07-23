package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.spi.MockFunction;

import java.util.Arrays;
import java.util.List;

/**
 * 字符串类函数。
 */
public final class StringFunctions {

    private StringFunctions() {
    }

    private static String resolveCharset(String name) {
        switch (name) {
            case "alpha":
                return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
            case "upper":
                return "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            case "lower":
                return "abcdefghijklmnopqrstuvwxyz";
            case "numeric":
                return "0123456789";
            case "alpha_num":
                return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            case "hex":
                return "0123456789abcdef";
            default:
                return name;
        }
    }

    public static List<MockFunction> all() {
        return Arrays.asList(
                MockFns.fn("string", (a, c) -> {
                    String charsetName = FuncArgs.asString(a, 0, "alpha_num");
                    String charset = resolveCharset(charsetName);
                    if (a.size() >= 3) {
                        int minLen = FuncArgs.asInt(a, 1, 1);
                        int maxLen = FuncArgs.asInt(a, 2, minLen);
                        return FakerData.randomString(charset, minLen, maxLen);
                    }
                    int len = FuncArgs.asInt(a, 1, 16);
                    return FakerData.randomString(charset, len, len);
                }),
                MockFns.fn("regex", (a, c) -> RegexGen.generate(FuncArgs.asString(a, 0, ""))),
                MockFns.fn("enum", (a, c) -> {
                    if (a == null || a.isEmpty()) {
                        return "";
                    }
                    Object picked = a.get(FakerData.randomInt(0, a.size() - 1));
                    return ExpressionEvaluator.stringify(picked);
                }),
                MockFns.fn("concat", (a, c) -> {
                    StringBuilder sb = new StringBuilder();
                    if (a != null) {
                        for (Object o : a) {
                            sb.append(ExpressionEvaluator.stringify(o));
                        }
                    }
                    return sb.toString();
                })
        );
    }
}
