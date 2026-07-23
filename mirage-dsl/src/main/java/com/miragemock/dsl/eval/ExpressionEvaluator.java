package com.miragemock.dsl.eval;

import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.func.FunctionRegistry;
import com.miragemock.dsl.spi.MockFunction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表达式 / 模板求值器。
 *
 * <ul>
 *   <li>{@link #evalTemplate(String, EvalContext)} 处理含 ${...} 的叶子字符串：
 *       若整串恰好是一个 ${...}，返回其原生求值结果；否则把各段拼成字符串。</li>
 *   <li>{@link #evalExpression(String, EvalContext)} 求值单个表达式（无外层 ${}）。</li>
 * </ul>
 */
public class ExpressionEvaluator {

    private final FunctionRegistry registry;

    public ExpressionEvaluator(FunctionRegistry registry) {
        this.registry = registry;
    }

    public FunctionRegistry getRegistry() {
        return registry;
    }

    // ============ 叶子模板求值 ============

    public Object evalTemplate(String leaf, EvalContext ctx) {
        if (leaf == null) {
            return null;
        }
        List<Part> parts = scanParts(leaf);
        if (parts.isEmpty()) {
            return "";
        }
        int spanCount = 0;
        long literalChars = 0;
        for (Part p : parts) {
            if (p.span) {
                spanCount++;
            } else {
                literalChars += p.text.length();
            }
        }
        if (spanCount == 0) {
            return leaf;
        }
        // 单段且无字面量文本：保留原生类型
        if (spanCount == 1 && literalChars == 0) {
            for (Part p : parts) {
                if (p.span) {
                    return evalExpression(p.text, ctx);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Part p : parts) {
            if (p.span) {
                sb.append(stringify(evalExpression(p.text, ctx)));
            } else {
                sb.append(p.text);
            }
        }
        return sb.toString();
    }

    public Object evalExpression(String expr, EvalContext ctx) {
        Ast.Node node = new Parser(expr, registry.nameSet()).parse();
        return evalNode(node, ctx);
    }

    // ============ AST 求值 ============

    private Object evalNode(Ast.Node node, EvalContext ctx) {
        if (node instanceof Ast.Str) {
            return ((Ast.Str) node).value;
        }
        if (node instanceof Ast.Num) {
            return ((Ast.Num) node).value;
        }
        if (node instanceof Ast.Var) {
            String name = ((Ast.Var) node).name;
            if (ctx.hasVariable(name)) {
                return ctx.getVariable(name);
            }
            // 未知裸标识符按其名字字符串处理（支持 alpha_num/nodash/charset 等枚举 token）
            return name;
        }
        if (node instanceof Ast.Nested) {
            return evalNode(((Ast.Nested) node).inner, ctx);
        }
        if (node instanceof Ast.Plus) {
            Object l = evalNode(((Ast.Plus) node).left, ctx);
            Object r = evalNode(((Ast.Plus) node).right, ctx);
            return plus(l, r);
        }
        if (node instanceof Ast.FuncCall) {
            return evalFuncCall((Ast.FuncCall) node, ctx);
        }
        throw new ExprException("未知 AST 节点: " + node);
    }

    private Object evalFuncCall(Ast.FuncCall fc, EvalContext ctx) {
        MockFunction fn = registry.get(fc.name);
        if (fn == null) {
            throw new ExprException("未知函数: " + fc.name);
        }
        List<Object> args = new ArrayList<>(fc.argSpecs.size());
        for (Object spec : fc.argSpecs) {
            if (spec instanceof Ast.Node) {
                args.add(evalNode((Ast.Node) spec, ctx));
            } else {
                // 原始字符串参数（date/datetime）
                args.add(spec);
            }
        }
        return fn.eval(args, ctx);
    }

    // ============ 运算与类型 ============

    private Object plus(Object l, Object r) {
        if (isNumber(l) && isNumber(r)) {
            return numericAdd(l, r);
        }
        return stringify(l) + stringify(r);
    }

    public static boolean isNumber(Object o) {
        return o instanceof BigDecimal || o instanceof BigInteger
                || o instanceof Long || o instanceof Integer
                || o instanceof Short || o instanceof Byte
                || o instanceof Double || o instanceof Float;
    }

    private static Object numericAdd(Object l, Object r) {
        boolean integral = (l instanceof Long || l instanceof Integer || l instanceof Short
                || l instanceof Byte || l instanceof BigInteger)
                && (r instanceof Long || r instanceof Integer || r instanceof Short
                || r instanceof Byte || r instanceof BigInteger);
        if (integral) {
            return ((Number) l).longValue() + ((Number) r).longValue();
        }
        return toBigDecimal(l).add(toBigDecimal(r));
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof Double || o instanceof Float) {
            return BigDecimal.valueOf(((Number) o).doubleValue());
        }
        return new BigDecimal(o.toString());
    }

    public static String stringify(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof BigDecimal) {
            return ((BigDecimal) o).toPlainString();
        }
        if (o instanceof Double || o instanceof Float) {
            return BigDecimal.valueOf(((Number) o).doubleValue()).toPlainString();
        }
        if (o instanceof Map || o instanceof List) {
            return JsonUtils.toJson(o);
        }
        return o.toString();
    }

    // ============ 叶子 ${...} 切分 ============

    private static final class Part {
        final String text;
        final boolean span;

        Part(String text, boolean span) {
            this.text = text;
            this.span = span;
        }
    }

    private List<Part> scanParts(String s) {
        List<Part> parts = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '$' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                if (lit.length() > 0) {
                    parts.add(new Part(lit.toString(), false));
                    lit.setLength(0);
                }
                int contentStart = i + 2;
                int j = contentStart;
                int depth = 1;
                while (j < s.length()) {
                    char cj = s.charAt(j);
                    if (cj == '\'' || cj == '"') {
                        char q = cj;
                        j++;
                        while (j < s.length()) {
                            char cc = s.charAt(j);
                            j++;
                            if (cc == '\\' && j < s.length()) {
                                j++;
                            } else if (cc == q) {
                                break;
                            }
                        }
                        continue;
                    }
                    if (cj == '{') {
                        depth++;
                    } else if (cj == '}') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    }
                    j++;
                }
                if (depth != 0) {
                    throw new ExprException("${...} 未闭合: " + s);
                }
                parts.add(new Part(s.substring(contentStart, j), true));
                i = j + 1;
            } else {
                lit.append(c);
                i++;
            }
        }
        if (lit.length() > 0) {
            parts.add(new Part(lit.toString(), false));
        }
        return parts;
    }
}
