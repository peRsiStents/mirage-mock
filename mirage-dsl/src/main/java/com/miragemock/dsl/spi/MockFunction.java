package com.miragemock.dsl.spi;

import com.miragemock.dsl.eval.EvalContext;

import java.util.List;

/**
 * 生成器/工具函数 SPI。实现类注册进 {@link com.miragemock.dsl.func.FunctionRegistry} 后，
 * 即可在模板中以 ${name(args)} 形式调用。
 *
 * <p>参数列表 args 已被表达式引擎求值为 Java 对象（String / Long / Double / List 等）。
 */
public interface MockFunction {

    /** 函数名，可含点号，如 "name.cn"、"decimal"、"sm2_sign" */
    String name();

    /**
     * 求值。
     *
     * @param args 已求值的参数
     * @param ctx  求值上下文（变量、密钥解析器等）
     */
    Object eval(List<Object> args, EvalContext ctx);
}
