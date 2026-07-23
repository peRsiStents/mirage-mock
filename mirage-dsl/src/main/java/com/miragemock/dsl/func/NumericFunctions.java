package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.spi.MockFunction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 数值与序列类函数。
 */
public final class NumericFunctions {

    private NumericFunctions() {
    }

    public static List<MockFunction> all() {
        return Arrays.asList(
                MockFns.fn("int", (a, c) -> {
                    long min = FuncArgs.asLong(a, 0, 0);
                    long max = FuncArgs.asLong(a, 1, 9999);
                    return (long) FakerData.randomInt((int) min, (int) max);
                }),
                MockFns.fn("decimal", (a, c) -> {
                    BigDecimal min = FuncArgs.asDecimal(a, 0, BigDecimal.ZERO);
                    BigDecimal max = FuncArgs.asDecimal(a, 1, new BigDecimal("9999"));
                    int scale = FuncArgs.asInt(a, 2, 2);
                    BigDecimal r = min.add(max.subtract(min).multiply(BigDecimal.valueOf(Math.random())));
                    return r.setScale(scale, RoundingMode.HALF_UP).toPlainString();
                }),
                MockFns.fn("seq", (a, c) -> {
                    String name;
                    long start;
                    if (a.size() >= 2) {
                        name = FuncArgs.asString(a, 0, "_default");
                        start = FuncArgs.asLong(a, 1, 1);
                    } else if (a.size() == 1) {
                        Object v = a.get(0);
                        if (v instanceof Number) {
                            name = "_default";
                            start = ((Number) v).longValue();
                        } else {
                            name = String.valueOf(v);
                            start = 1;
                        }
                    } else {
                        name = "_default";
                        start = 1;
                    }
                    if (c.getSeqProvider() == null || c.getProjectId() == null) {
                        throw new ExprException("seq 需要 SeqProvider 与 projectId 上下文");
                    }
                    return c.getSeqProvider().next(c.getProjectId(), name, start);
                }),
                MockFns.fn("uuid", (a, c) -> {
                    String u = UUID.randomUUID().toString();
                    String mode = FuncArgs.asString(a, 0, "");
                    return "nodash".equals(mode) ? u.replace("-", "") : u;
                })
        );
    }
}
