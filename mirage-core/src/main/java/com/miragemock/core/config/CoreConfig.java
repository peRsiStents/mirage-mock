package com.miragemock.core.config;

import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.func.BuiltinFunctions;
import com.miragemock.dsl.func.FunctionRegistry;
import com.miragemock.dsl.spi.MockFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 内核装配：函数注册表（内置 + 自定义 SPI）+ 表达式求值器。
 */
@Configuration
public class CoreConfig {

    @Bean
    public FunctionRegistry functionRegistry(List<MockFunction> customFunctions) {
        FunctionRegistry registry = new FunctionRegistry();
        BuiltinFunctions.all().forEach(registry::register);
        if (customFunctions != null) {
            for (MockFunction custom : customFunctions) {
                registry.register(custom);
            }
        }
        return registry;
    }

    @Bean
    public ExpressionEvaluator expressionEvaluator(FunctionRegistry registry) {
        return new ExpressionEvaluator(registry);
    }
}
