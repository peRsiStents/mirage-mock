package com.miragemock.dsl.func;

import com.miragemock.dsl.spi.MockFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 函数注册表。线程安全。
 */
public class FunctionRegistry {

    private final Map<String, MockFunction> functions = new ConcurrentHashMap<>();

    public void register(MockFunction function) {
        functions.put(function.name(), function);
    }

    public MockFunction get(String name) {
        return functions.get(name);
    }

    public boolean contains(String name) {
        return functions.containsKey(name);
    }

    public Set<String> nameSet() {
        return functions.keySet();
    }

    public Collection<MockFunction> all() {
        return functions.values();
    }

    public Map<String, MockFunction> snapshot() {
        return Collections.unmodifiableMap(functions);
    }
}
