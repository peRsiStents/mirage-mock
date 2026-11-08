package com.miragemock.dsl.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式词法分析器。输入为 ${...} 内部的表达式文本（不含外层定界符），
 * 支持嵌套 ${...}（产生 NESTED token，内部文本递归解析）。
 */
public final class Lexer {

    public enum Type {
        STRING, NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, PERCENT,
        LPAREN, RPAREN, COMMA, NESTED, FUNC_RAW, EOF
    }

    public static final class Token {
        public final Type type;
        public final Object value;
        public final int start;

        public Token(Type type, Object value, int start) {
            this.type = type;
            this.value = value;
            this.start = start;
        }

        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }

    private final String src;
    private int pos = 0;
    private final List<Token> tokens = new ArrayList<>();

    public Lexer(String src) {
        this.src = src == null ? "" : src;
    }

    public List<Token> tokenize() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (isSpace(c)) {
                pos++;
                continue;
            }
            switch (c) {
                case '+':
                    add(Type.PLUS, "+");
                    break;
                case '-':
                    add(Type.MINUS, "-");
                    break;
                case '*':
                    add(Type.STAR, "*");
                    break;
                case '/':
                    add(Type.SLASH, "/");
                    break;
                case '%':
                    add(Type.PERCENT, "%");
                    break;
                case '(':
                    add(Type.LPAREN, "(");
                    break;
                case ')':
                    add(Type.RPAREN, ")");
                    break;
                case ',':
                    add(Type.COMMA, ",");
                    break;
                case '\'':
                case '"':
                    readString(c);
                    break;
                case '$':
                    if (pos + 1 < src.length() && src.charAt(pos + 1) == '{') {
                        readNested();
                    } else {
                        throw new ExprException("非法字符 '$' 于位置 " + pos);
                    }
                    break;
                default:
                    if (c == '.' || isDigit(c)) {
                        readNumber();
                    } else if (isIdentStart(c)) {
                        readIdentOrDateFunc();
                    } else {
                        throw new ExprException("非法字符 '" + c + "' 于位置 " + pos);
                    }
            }
        }
        tokens.add(new Token(Type.EOF, null, pos));
        return tokens;
    }

    private void add(Type type, Object value) {
        tokens.add(new Token(type, value, pos));
        pos += value.toString().length();
    }

    private void readString(char quote) {
        int start = pos;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < src.length()) {
                char n = src.charAt(pos + 1);
                switch (n) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '\'':
                        sb.append('\'');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case 'u': {
                        // unicode 转义：反斜杠 + u + 4 位十六进制，不足或非法直接报错
                        if (pos + 6 > src.length()) {
                            throw new ExprException("unicode 转义需要 4 位十六进制字符，起始于位置 " + pos);
                        }
                        String hex = src.substring(pos + 2, pos + 6);
                        int code = 0;
                        for (int k = 0; k < 4; k++) {
                            char h = hex.charAt(k);
                            int d = Character.digit(h, 16);
                            if (d < 0) {
                                throw new ExprException("unicode 转义包含非法十六进制字符 '" + h + "': " + hex);
                            }
                            code = (code << 4) | d;
                        }
                        sb.append((char) code);
                        pos += 6;
                        continue;
                    }
                    default:
                        // 未知转义保留反斜杠（如 '\d' 仍是反斜杠 + d），供 regex 等函数使用
                        sb.append('\\').append(n);
                }
                pos += 2;
                continue;
            }
            if (c == quote) {
                pos++;
                tokens.add(new Token(Type.STRING, sb.toString(), start));
                return;
            }
            sb.append(c);
            pos++;
        }
        throw new ExprException("字符串字面量未闭合，起始于位置 " + start);
    }

    /**
     * 数字字面量：整数（long）或小数/科学计数法（double）。
     * 支持形如 123、.5、1.5、1e5、1.5e-2；畸形数字（1.2.3、1.、1e 等）抛 ExprException。
     */
    private void readNumber() {
        int start = pos;
        boolean decimal = false;
        if (src.charAt(pos) == '.') {
            decimal = true;
            pos++;
            requireDigit(start);
            readDigits();
        } else {
            readDigits();
            if (pos < src.length() && src.charAt(pos) == '.') {
                decimal = true;
                pos++;
                requireDigit(start);
                readDigits();
            }
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            decimal = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            requireDigit(start);
            readDigits();
        }
        if (pos < src.length() && src.charAt(pos) == '.') {
            // 如 1.2.3：小数点后跟小数点，畸形
            throw new ExprException("畸形数字字面量: " + src.substring(start, pos + 1) + "（位置 " + start + "）");
        }
        String text = src.substring(start, pos);
        Object value;
        try {
            value = decimal ? (Object) Double.parseDouble(text) : (Object) Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new ExprException("畸形数字字面量: " + text + "（位置 " + start + "）", e);
        }
        tokens.add(new Token(Type.NUMBER, value, start));
    }

    private void readDigits() {
        while (pos < src.length() && isDigit(src.charAt(pos))) {
            pos++;
        }
    }

    private void requireDigit(int start) {
        if (pos >= src.length() || !isDigit(src.charAt(pos))) {
            throw new ExprException("畸形数字字面量: " + src.substring(start, Math.min(pos + 1, src.length()))
                    + "（位置 " + start + "）");
        }
    }

    private void readIdentOrDateFunc() {
        int start = pos;
        while (pos < src.length() && isIdentPart(src.charAt(pos))) {
            pos++;
        }
        String name = src.substring(start, pos);
        // date/datetime 的参数含 ':' '-' 空格等非法表达式字符，整体捕获为原始文本
        if (isDateFunc(name) && pos < src.length() && src.charAt(pos) == '(') {
            String raw = captureBalancedParens();
            tokens.add(new Token(Type.FUNC_RAW, new String[]{name, raw}, start));
        } else {
            tokens.add(new Token(Type.IDENT, name, start));
        }
    }

    private static boolean isDateFunc(String name) {
        return "date".equals(name) || "datetime".equals(name);
    }

    /**
     * 当前 pos 位于 '('，读取平衡括号组，返回括号内原始文本（不含外层括号），pos 停在 ')' 之后。
     */
    private String captureBalancedParens() {
        pos++; // 跳过 '('
        int depth = 1;
        int innerStart = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\'' || c == '"') {
                char q = c;
                pos++;
                while (pos < src.length()) {
                    char cc = src.charAt(pos);
                    pos++;
                    if (cc == '\\' && pos < src.length()) {
                        pos++;
                    } else if (cc == q) {
                        break;
                    }
                }
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    String inner = src.substring(innerStart, pos);
                    pos++; // 跳过 ')'
                    return inner;
                }
            }
            pos++;
        }
        throw new ExprException("括号未闭合");
    }

    private void readNested() {
        // 当前位于 '$' '{'
        int start = pos;
        pos += 2; // 跳过 ${
        int depth = 1;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\'' || c == '"') {
                // 跳过字符串，避免字符串内的括号干扰
                char q = c;
                sb.append(c);
                pos++;
                while (pos < src.length()) {
                    char cc = src.charAt(pos);
                    sb.append(cc);
                    pos++;
                    if (cc == '\\' && pos < src.length()) {
                        sb.append(src.charAt(pos));
                        pos++;
                    } else if (cc == q) {
                        break;
                    }
                }
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    pos++;
                    tokens.add(new Token(Type.NESTED, sb.toString(), start));
                    return;
                }
            }
            sb.append(c);
            pos++;
        }
        throw new ExprException("${...} 未闭合，起始于位置 " + start);
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c) || c == '.';
    }
}
