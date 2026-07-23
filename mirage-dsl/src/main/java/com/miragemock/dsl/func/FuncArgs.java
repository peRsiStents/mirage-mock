package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.ExpressionEvaluator;

import java.math.BigDecimal;
import java.util.List;

/**
 * 函数参数读取工具。
 */
public final class FuncArgs {

    private FuncArgs() {
    }

    public static Object get(List<Object> args, int i, Object def) {
        return (args != null && i >= 0 && i < args.size() && args.get(i) != null) ? args.get(i) : def;
    }

    public static String asString(List<Object> args, int i, String def) {
        Object v = get(args, i, null);
        return v == null ? def : ExpressionEvaluator.stringify(v);
    }

    public static long asLong(List<Object> args, int i, long def) {
        Object v = get(args, i, null);
        if (v == null) {
            return def;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(ExpressionEvaluator.stringify(v).trim());
    }

    public static int asInt(List<Object> args, int i, int def) {
        return (int) asLong(args, i, def);
    }

    public static double asDouble(List<Object> args, int i, double def) {
        Object v = get(args, i, null);
        if (v == null) {
            return def;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return Double.parseDouble(ExpressionEvaluator.stringify(v).trim());
    }

    public static BigDecimal asDecimal(List<Object> args, int i, BigDecimal def) {
        Object v = get(args, i, null);
        if (v == null) {
            return def;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        return new BigDecimal(ExpressionEvaluator.stringify(v).trim());
    }
}
