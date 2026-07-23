package com.miragemock.dsl.func;

import com.miragemock.dsl.spi.MockFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置生成器函数集合。
 */
public final class BuiltinFunctions {

    private BuiltinFunctions() {
    }

    public static List<MockFunction> all() {
        List<MockFunction> list = new ArrayList<>();
        list.addAll(PersonFunctions.all());
        list.addAll(NumericFunctions.all());
        list.addAll(StringFunctions.all());
        list.addAll(TimeFunctions.all());
        list.addAll(DigestFunctions.all());
        list.addAll(CryptoFunctions.all());
        return list;
    }
}
