package com.miragemock.dsl.func;

import com.miragemock.dsl.eval.ExprException;

import java.security.SecureRandom;

/**
 * 轻量正则 → 字符串生成器。支持常用子集：字面量、\d \w \s、[a-z] 字符类、{n} {n,m} 量词、+ *。
 * 不保证覆盖全部正则语法，面向 Mock 测试数据。
 *
 * <p>安全约束：量词与生成长度有上限，避免「一条规则配置打满 CPU/内存」——
 * 正则由规则作者书写，虽非直接面向公网输入，仍属可被滥用资源。
 */
public final class RegexGen {

    /** 单个量词的最大重复次数 */
    public static final int MAX_QUANTIFIER = 1024;

    /** 单次生成的字符串最大长度（字符） */
    public static final int MAX_OUTPUT_LENGTH = 64 * 1024;

    /** 取反字符类时的取值范围（可打印 ASCII） */
    private static final char NEGATION_RANGE_START = 0x20;
    private static final char NEGATION_RANGE_END = 0x7E;

    private static final SecureRandom RNG = new SecureRandom();

    private RegexGen() {
    }

    public static String generate(String pattern) {
        if (pattern == null) {
            throw new ExprException("regex() 参数不能为空");
        }
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
            if (i < n && pattern.charAt(i) == '{') {
                int close = pattern.indexOf('}', i);
                if (close < 0) {
                    throw new ExprException("正则量词未闭合：'{' 位置 " + i);
                }
                String q = pattern.substring(i + 1, close);
                repeat = resolveQuantifier(q);
                i = close + 1;
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
            if (repeat > MAX_QUANTIFIER) {
                throw new ExprException("正则量词超出上限（单次最多 " + MAX_QUANTIFIER + " 次）：" + pattern);
            }
            for (int r = 0; r < repeat; r++) {
                if (out.length() >= MAX_OUTPUT_LENGTH) {
                    throw new ExprException("正则生成结果超出长度上限（" + MAX_OUTPUT_LENGTH + " 字符）：" + pattern);
                }
                out.append(unit.sample());
            }
        }
        return out.toString();
    }

    /**
     * 解析量词：{@code {n}} / {@code {n,m}} / {@code {n,}}。
     * 非法格式（{@code {2,x}}、空、负数）抛 ExprException，不静默取默认值。
     */
    private static int resolveQuantifier(String q) {
        String spec = q == null ? "" : q.trim();
        if (spec.isEmpty()) {
            throw new ExprException("正则量词不能为空：'{}'");
        }
        int comma = spec.indexOf(',');
        try {
            if (comma < 0) {
                int only = Integer.parseInt(spec);
                if (only < 0) {
                    throw new ExprException("正则量词不能为负数：'{" + spec + "}'");
                }
                return only;
            }
            String lo = spec.substring(0, comma).trim();
            String hi = spec.substring(comma + 1).trim();
            if (lo.isEmpty()) {
                throw new ExprException("正则量词下限不能为空：'{" + spec + "}'");
            }
            int l = Integer.parseInt(lo);
            if (l < 0) {
                throw new ExprException("正则量词下限不能为负数：'{" + spec + "}'");
            }
            if (hi.isEmpty()) {
                // {n,} 视为 n..n+3
                return l;
            }
            int h = Integer.parseInt(hi);
            if (h < l) {
                throw new ExprException("正则量词区间非法（上界小于下界）：'{" + spec + "}'");
            }
            if (h <= l) {
                return l;
            }
            return l + RNG.nextInt(h - l + 1);
        } catch (NumberFormatException e) {
            throw new ExprException("正则量词格式非法：'{" + spec + "}'");
        }
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

    /**
     * 展开字符类：支持 {@code [a-z]} 区间、{@code \d} 转义、以及前导 {@code ^} 取反。
     * 取反按可打印 ASCII（0x20-0x7E）做补集；区间倒序视为非法。
     */
    private static String expandClass(String body) {
        if (body.isEmpty()) {
            throw new ExprException("正则字符类不能为空：'[]'");
        }
        boolean negated = body.charAt(0) == '^';
        String content = negated ? body.substring(1) : body;
        String expanded = expandRanges(content);
        if (!negated) {
            return expanded;
        }
        // 补集：可打印 ASCII 中不在 expanded 里的字符
        StringBuilder sb = new StringBuilder();
        for (char x = NEGATION_RANGE_START; x <= NEGATION_RANGE_END; x++) {
            if (expanded.indexOf(x) < 0) {
                sb.append(x);
            }
        }
        if (sb.length() == 0) {
            throw new ExprException("正则字符类取反后为空：'[\\x00-\\xFF]' 之类不支持");
        }
        return sb.toString();
    }

    private static String expandRanges(String content) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < content.length(); k++) {
            char ch = content.charAt(k);
            if (k + 2 < content.length() && content.charAt(k + 1) == '-') {
                char from = ch;
                char to = content.charAt(k + 2);
                if (from > to) {
                    throw new ExprException("正则字符类区间倒序：'[" + from + "-" + to + "]'");
                }
                for (char x = from; x <= to; x++) {
                    sb.append(x);
                }
                k += 2;
            } else if (ch == '\\' && k + 1 < content.length()) {
                sb.append(escapeClass(content.charAt(k + 1)));
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
