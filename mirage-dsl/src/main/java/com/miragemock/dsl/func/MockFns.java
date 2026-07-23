package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.spi.MockFunction;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 函数构造小工具。
 */
public final class MockFns {

    private MockFns() {
    }

    public static MockFunction fn(String name, BiFunction<List<Object>, EvalContext, Object> impl) {
        return new MockFunction() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Object eval(List<Object> args, EvalContext ctx) {
                return impl.apply(args, ctx);
            }
        };
    }
}
