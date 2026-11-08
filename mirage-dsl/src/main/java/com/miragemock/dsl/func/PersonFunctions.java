package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.spi.MockFunction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 人员与证件类生成器函数。
 *
 * <p>身份证派生：同一求值上下文内，{@code idcard.cn} 会把生成的 18 位号暂存
 * （存放在 EvalContext 的内部暂存区，不污染用户变量表），
 * 紧随其后的 {@code idcard.birthdate} / {@code idcard.gender} 复用同一张证（行内左到右求值时一致）。
 */
public final class PersonFunctions {

    /** 上下文暂存最近一次 idcard.cn 的键 */
    private static final String STASH_KEY = "__last_idcard_cn";

    private PersonFunctions() {
    }

    public static List<MockFunction> all() {
        return Arrays.asList(
                MockFns.fn("name.cn", (a, c) -> FakerData.cnName()),
                MockFns.fn("name.en", (a, c) -> FakerData.enName()),
                MockFns.fn("phone.cn_mobile", (a, c) -> FakerData.mobile()),
                MockFns.fn("idcard.cn", (a, c) -> {
                    String id = FakerData.idcard();
                    stash(c, id);
                    return id;
                }),
                MockFns.fn("idcard.birthdate", (a, c) -> formatBirth(resolveIdCard(c), FuncArgs.asString(a, 0, "yyyy-MM-dd"))),
                MockFns.fn("idcard.gender", (a, c) -> genderOf(resolveIdCard(c))),
                MockFns.fn("bankcard.cn", (a, c) -> FakerData.bankcard()),
                MockFns.fn("uscc.cn", (a, c) -> FakerData.uscc()),
                MockFns.fn("address.cn", (a, c) -> FakerData.address()),
                MockFns.fn("email", (a, c) -> FakerData.email())
        );
    }

    /** 取上下文暂存的身份证；没有则生成一张并暂存（保证 birthdate/gender 自洽） */
    private static String resolveIdCard(EvalContext c) {
        if (c != null) {
            Object v = c.getInternal(STASH_KEY);
            if (v instanceof String && ((String) v).length() == 18) {
                return (String) v;
            }
        }
        String id = FakerData.idcard();
        stash(c, id);
        return id;
    }

    private static void stash(EvalContext c, String id) {
        if (c != null) {
            c.putInternal(STASH_KEY, id);
        }
    }

    /**
     * 从 18 位身份证取出生日期并按给定 pattern 格式化：下标 6-13 = YYYYMMDD。
     * 用 DateTimeFormatter 而非手工拼分隔符，支持 yyyy-MM-dd / yyyy/MM/dd / yyyyMMdd / yyyy年MM月dd日 等。
     */
    private static String formatBirth(String id, String fmt) {
        String y = id.substring(6, 10);
        String m = id.substring(10, 12);
        String d = id.substring(12, 14);
        String pattern = (fmt == null || fmt.trim().isEmpty()) ? "yyyy-MM-dd" : fmt.trim();
        try {
            LocalDate birth = LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
            return birth.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
            throw new ExprException("出生日期格式化失败: pattern='" + pattern + "'，身份证='" + id + "'");
        }
    }

    /** 第 17 位(下标 16)奇=男 偶=女 */
    private static String genderOf(String id) {
        int g = id.charAt(16) - '0';
        return (g % 2 == 1) ? "男" : "女";
    }
}
