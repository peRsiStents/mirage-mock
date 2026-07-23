package com.miragemock.dsl.func;

import java.security.SecureRandom;

/**
 * 轻量正则 → 字符串生成器。支持常用子集：字面量、\d \w \s、[a-z] 字符类、{n} {n,m} 量词、+ *。
 * 不保证覆盖全部正则语法，面向 Mock 测试数据。
 */
public final class RegexGen {

    private static final SecureRandom RNG = new SecureRandom();

    private RegexGen() {
    }

    public static String generate(String pattern) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = pattern.length();
        while (i < n) {
            char c = pattern.charAt(i);
            if (c == '^' || c == '$') {
                i++;
                continue;
            }
            // 解析一个"单元"及其后续量词
            Unit unit = parseUnit(pattern, i);
            i = unit.next;
            // 解析量词
            int repeat = 1;
            if (i < n && (pattern.charAt(i) == '{')) {
                int close = pattern.indexOf('}', i);
                if (close > 0) {
                    String q = pattern.substring(i + 1, close);
                    repeat = resolveQuantifier(q);
                    i = close + 1;
                }
            } else if (i < n && pattern.charAt(i) == '+') {
                repeat = 1 + RNG.nextInt(5);
                i++;
            } else if (i < n && pattern.charAt(i) == '*') {
                repeat = RNG.nextInt(6);
                i++;
            } else if (i < n && pattern.charAt(i) == '?') {
                repeat = RNG.nextInt(2);
                i++;
            }
            for (int r = 0; r < repeat; r++) {
                out.append(unit.sample());
            }
        }
        return out.toString();
    }

    private static int resolveQuantifier(String q) {
        int comma = q.indexOf(',');
        if (comma < 0) {
            return Integer.parseInt(q.trim());
        }
        String lo = q.substring(0, comma).trim();
        String hi = q.substring(comma + 1).trim();
        int l = lo.isEmpty() ? 0 : Integer.parseInt(lo);
        int h = hi.isEmpty() ? l + 3 : Integer.parseInt(hi);
        if (h <= l) {
            return l;
        }
        return l + RNG.nextInt(h - l + 1);
    }

    private static Unit parseUnit(String p, int i) {
        char c = p.charAt(i);
        if (c == '\\' && i + 1 < p.length()) {
            char e = p.charAt(i + 1);
            return new Unit(escapeClass(e), i + 2);
        }
        if (c == '[') {
            int close = p.indexOf(']', i);
            if (close > 0) {
                String cls = expandClass(p.substring(i + 1, close));
                return new Unit(cls, close + 1);
            }
        }
        if (c == '.') {
            return new Unit("0123456789abcdefghijklmnopqrstuvwxyz", i + 1);
        }
        return new Unit(String.valueOf(c), i + 1);
    }

    private static String escapeClass(char e) {
        switch (e) {
            case 'd':
                return "0123456789";
            case 'w':
                return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
            case 's':
                return " \t";
            default:
                return String.valueOf(e);
        }
    }

    private static String expandClass(String body) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < body.length(); k++) {
            char ch = body.charAt(k);
            if (k + 2 < body.length() && body.charAt(k + 1) == '-') {
                char from = ch;
                char to = body.charAt(k + 2);
                for (char x = from; x <= to; x++) {
                    sb.append(x);
                }
                k += 2;
            } else if (ch == '\\' && k + 1 < body.length()) {
                sb.append(escapeClass(body.charAt(k + 1)));
                k++;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static final class Unit {
        final String charset;
        final int next;

        Unit(String charset, int next) {
            this.charset = charset;
            this.next = next;
        }

        char sample() {
            if (charset == null || charset.isEmpty()) {
                return ' ';
            }
            return charset.charAt(RNG.nextInt(charset.length()));
        }
    }
}
