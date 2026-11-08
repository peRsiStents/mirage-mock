package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.eval.ExpressionEvaluator;

import java.math.BigDecimal;
import java.util.List;

/**
 * 函数参数读取工具。
 *
 * <p>参数解析失败一律抛 {@link ExprException}（携带参数序号与原值），
 * 避免 NumberFormatException 这类底层异常直接冒泡成裸 500。
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
        String s = ExpressionEvaluator.stringify(v).trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ExprException("第 " + (i + 1) + " 个参数不是合法整数: '" + s + "'");
        }
    }

    public static int asInt(List<Object> args, int i, int def) {
        long v = asLong(args, i, def);
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new ExprException("第 " + (i + 1) + " 个参数超出 int 范围: " + v);
        }
        return (int) v;
    }

    public static double asDouble(List<Object> args, int i, double def) {
        Object v = get(args, i, null);
        if (v == null) {
            return def;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        String s = ExpressionEvaluator.stringify(v).trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new ExprException("第 " + (i + 1) + " 个参数不是合法数字: '" + s + "'");
        }
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
        String s = ExpressionEvaluator.stringify(v).trim();
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new ExprException("第 " + (i + 1) + " 个参数不是合法数字: '" + s + "'");
        }
    }
}
