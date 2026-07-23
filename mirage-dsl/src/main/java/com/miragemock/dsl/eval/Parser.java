package com.miragemock.dsl.eval;

import com.miragemock.dsl.eval.Lexer.Token;
import com.miragemock.dsl.eval.Lexer.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 递归下降解析器：additive := primary ('+' primary)*
 * primary := STRING | NUMBER | NESTED | FUNC_RAW | funcCall | IDENT
 */
public final class Parser {

    private final List<Token> tokens;
    private final Set<String> functionNames;
    private int i = 0;

    public Parser(String source, Set<String> functionNames) {
        this.tokens = new Lexer(source).tokenize();
        this.functionNames = functionNames;
    }

    public Ast.Node parse() {
        Ast.Node node = parseAdditive();
        if (peek().type != Type.EOF) {
            throw new ExprException("表达式末尾存在未消费 token: " + peek());
        }
        return node;
    }

    private Ast.Node parseAdditive() {
        Ast.Node left = parsePrimary();
        while (peek().type == Type.PLUS) {
            next();
            Ast.Node right = parsePrimary();
            left = new Ast.Plus(left, right);
        }
        return left;
    }

    private Ast.Node parsePrimary() {
        Token t = peek();
        switch (t.type) {
            case STRING:
                next();
                return new Ast.Str((String) t.value);
            case NUMBER:
                next();
                return new Ast.Num(t.value);
            case NESTED:
                next();
                return new Ast.Nested(new Parser((String) t.value, functionNames).parse());
            case FUNC_RAW: {
                next();
                String[] nameAndRaw = (String[]) t.value;
                List<String> rawArgs = splitTopLevelComma(nameAndRaw[1]);
                return new Ast.FuncCall(nameAndRaw[0], new ArrayList<>(rawArgs));
            }
            case IDENT: {
                next();
                String name = (String) t.value;
                if (peek().type == Type.LPAREN) {
                    return parseFuncArgs(name);
                }
                // 无括号：注册函数视为无参调用，否则作为变量引用
                if (functionNames != null && functionNames.contains(name)) {
                    return new Ast.FuncCall(name, Collections.emptyList());
                }
                return new Ast.Var(name);
            }
            default:
                throw new ExprException("意外的 token: " + t);
        }
    }

    private Ast.FuncCall parseFuncArgs(String name) {
        next(); // consume LPAREN
        List<Object> specs = new ArrayList<>();
        if (peek().type != Type.RPAREN) {
            specs.add(parseAdditive());
            while (peek().type == Type.COMMA) {
                next();
                specs.add(parseAdditive());
            }
        }
        expect(Type.RPAREN);
        return new Ast.FuncCall(name, specs);
    }

    private Token peek() {
        return tokens.get(i);
    }

    private Token next() {
        return tokens.get(i++);
    }

    private void expect(Type type) {
        if (peek().type != type) {
            throw new ExprException("期望 " + type + "，实际 " + peek().type);
        }
        i++;
    }

    /**
     * 按顶层逗号切分（忽略括号/引号内的逗号），用于 date/datetime 原始参数。
     */
    public static List<String> splitTopLevelComma(String s) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        int p = 0;
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == '\'' || c == '"') {
                char q = c;
                cur.append(c);
                p++;
                while (p < s.length()) {
                    char cc = s.charAt(p);
                    cur.append(cc);
                    p++;
                    if (cc == '\\' && p < s.length()) {
                        cur.append(s.charAt(p));
                        p++;
                    } else if (cc == q) {
                        break;
                    }
                }
                continue;
            }
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
            p++;
        }
        if (cur.length() > 0 || !result.isEmpty()) {
            result.add(cur.toString().trim());
        }
        return result;
    }
}
