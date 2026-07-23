package com.miragemock.dsl.func;

import com.miragemock.dsl.spi.MockFunction;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 时间类函数：date / datetime。参数为原始字符串（now / now-30d / yyyy-MM-dd 等）。
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
        long min = parseDateExpr(FuncArgs.asString(args, 0, "now-30d"), System.currentTimeMillis() - 30L * 86400000L);
        long max = parseDateExpr(FuncArgs.asString(args, 1, "now"), System.currentTimeMillis());
        if (max < min) {
            long t = min;
            min = max;
            max = t;
        }
        String pattern = FuncArgs.asString(args, 2, defaultPattern);
        long t = min + (long) (Math.random() * (max - min + 1));
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZONE);
        return DateTimeFormatter.ofPattern(pattern).format(ldt);
    }

    static long parseDateExpr(String expr, long fallback) {
        if (expr == null) {
            return fallback;
        }
        String e = expr.trim();
        if (e.isEmpty() || "now".equals(e)) {
            return System.currentTimeMillis();
        }
        long base = System.currentTimeMillis();
        if (e.startsWith("now")) {
            String rest = e.substring(3).trim();
            if (!rest.isEmpty() && (rest.charAt(0) == '+' || rest.charAt(0) == '-')) {
                char sign = rest.charAt(0);
                String body = rest.substring(1);
                char unit = body.charAt(body.length() - 1);
                long amount;
                try {
                    amount = Long.parseLong(body.substring(0, body.length() - 1));
                } catch (NumberFormatException ex) {
                    return fallback;
                }
                long delta = amount * unitMillis(unit);
                return base + (sign == '-' ? -delta : delta);
            }
            return base;
        }
        // 绝对时间解析
        try {
            String pat = e.length() <= 10 ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
            return LocalDateTime.parse(e, DateTimeFormatter.ofPattern(pat))
                    .atZone(ZONE).toInstant().toEpochMilli();
        } catch (Exception ex) {
            return fallback;
        }
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
