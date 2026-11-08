package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.spi.MockFunction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 时间类函数：date / datetime。参数为原始字符串（now / now-30d / yyyy-MM-dd 等）。
 *
 * <p>约定：
 * <ul>
 *   <li>参数可带引号（{@code date('2020-01-01','2020-12-31')}），解析前剥离引号</li>
 *   <li>显式给出的绝对日期解析失败 → 报错，不再静默回退到默认区间
 *       （静默回退会让用户拿到近 30 天的随机日期，极难察觉）</li>
 *   <li>可选第 4 个参数指定时区（默认 Asia/Shanghai）</li>
 * </ul>
 */
public final class TimeFunctions {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TimeFunctions() {
    }

    public static List<MockFunction> all() {
        return Arrays.asList(
                MockFns.fn("date", (a, c) -> randomTime(a, "yyyy-MM-dd")),
                MockFns.fn("datetime", (a, c) -> randomTime(a, "yyyy-MM-dd HH:mm:ss"))
        );
    }

    private static String randomTime(List<Object> args, String defaultPattern) {
        boolean hasMin = FuncArgs.get(args, 0, null) != null;
        boolean hasMax = FuncArgs.get(args, 1, null) != null;
        long min = parseDateExpr(FuncArgs.asString(args, 0, "now-30d"),
                System.currentTimeMillis() - 30L * 86400000L, hasMin);
        long max = parseDateExpr(FuncArgs.asString(args, 1, "now"), System.currentTimeMillis(), hasMax);
        if (max < min) {
            long t = min;
            min = max;
            max = t;
        }
        String pattern = stripQuotes(FuncArgs.asString(args, 2, defaultPattern));
        ZoneId zone = resolveZone(FuncArgs.asString(args, 3, ""));
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            long t = min + (long) (Math.random() * (max - min + 1));
            LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone);
            return formatter.format(ldt);
        } catch (IllegalArgumentException e) {
            throw new ExprException("日期格式化 pattern 非法: '" + pattern + "'");
        }
    }

    /** 时区参数：空则用默认 Asia/Shanghai；非法时区报错。 */
    private static ZoneId resolveZone(String zoneText) {
        String z = stripQuotes(zoneText == null ? "" : zoneText).trim();
        if (z.isEmpty()) {
            return ZONE;
        }
        try {
            return ZoneId.of(z);
        } catch (Exception e) {
            throw new ExprException("时区非法: '" + z + "'");
        }
    }

    /**
     * 解析日期表达式。
     *
     * @param explicit 用户是否显式给出了该参数：显式给出但解析失败时抛错而非静默回退
     */
    static long parseDateExpr(String expr, long fallback, boolean explicit) {
        if (expr == null) {
            if (explicit) {
                throw new ExprException("日期参数不能为空");
            }
            return fallback;
        }
        String e = stripQuotes(expr).trim();
        if (e.isEmpty()) {
            if (explicit) {
                throw new ExprException("日期参数为空");
            }
            return fallback;
        }
        if ("now".equals(e)) {
            return System.currentTimeMillis();
        }
        long base = System.currentTimeMillis();
        if (e.startsWith("now")) {
            String rest = e.substring(3).trim();
            if (!rest.isEmpty() && (rest.charAt(0) == '+' || rest.charAt(0) == '-')) {
                char sign = rest.charAt(0);
                String body = rest.substring(1);
                if (body.isEmpty()) {
                    throw new ExprException("相对时间缺少数值: '" + expr + "'");
                }
                char unit = body.charAt(body.length() - 1);
                long amount;
                try {
                    amount = Long.parseLong(body.substring(0, body.length() - 1));
                } catch (NumberFormatException ex) {
                    throw new ExprException("相对时间格式非法: '" + expr + "'");
                }
                long delta = amount * unitMillis(unit);
                return base + (sign == '-' ? -delta : delta);
            }
            return base;
        }
        // 绝对时间解析
        try {
            if (e.length() <= 10) {
                // 纯日期：LocalDateTime.parse 需要时间部分，纯日期必须走 LocalDate
                return LocalDate.parse(e, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        .atStartOfDay(ZONE).toInstant().toEpochMilli();
            }
            return LocalDateTime.parse(e, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(ZONE).toInstant().toEpochMilli();
        } catch (Exception ex) {
            if (explicit) {
                throw new ExprException("日期格式非法（支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / now±Nd）: '" + e + "'");
            }
            return fallback;
        }
    }

    /** 兼容旧签名：显式给定时解析失败同样抛错。 */
    static long parseDateExpr(String expr, long fallback) {
        return parseDateExpr(expr, fallback, expr != null && !stripQuotes(expr).trim().isEmpty());
    }

    /** 剥离首尾单/双引号（函数参数里引号不会被词法器去掉）。 */
    static String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() >= 2) {
            char first = t.charAt(0);
            char last = t.charAt(t.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return t.substring(1, t.length() - 1);
            }
        }
        return t;
    }

    private static long unitMillis(char unit) {
        switch (unit) {
            case 's':
                return 1000L;
            case 'm':
                return 60000L;
            case 'h':
                return 3600000L;
            case 'd':
                return 86400000L;
            case 'w':
                return 604800000L;
            case 'M':
                return 2592000000L; // 30d 近似
            case 'y':
                return 31536000000L; // 365d 近似
            default:
                return 86400000L;
        }
    }
}
