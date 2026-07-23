package com.miragemock.dsl.func;

import com.miragemock.dsl.spi.MockFunction;

import java.util.Arrays;
import java.util.List;

/**
 * 人员与证件类生成器函数。
 */
public final class PersonFunctions {

    private PersonFunctions() {
    }

    public static List<MockFunction> all() {
        return Arrays.asList(
                MockFns.fn("name.cn", (a, c) -> FakerData.cnName()),
                MockFns.fn("name.en", (a, c) -> FakerData.enName()),
                MockFns.fn("phone.cn_mobile", (a, c) -> FakerData.mobile()),
                MockFns.fn("idcard.cn", (a, c) -> FakerData.idcard()),
                MockFns.fn("bankcard.cn", (a, c) -> FakerData.bankcard()),
                MockFns.fn("uscc.cn", (a, c) -> FakerData.uscc()),
                MockFns.fn("address.cn", (a, c) -> FakerData.address()),
                MockFns.fn("email", (a, c) -> FakerData.email())
        );
    }
}
