package com.miragemock.dsl.eval;

import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.func.FunctionRegistry;
import com.miragemock.dsl.spi.MockFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 *
 * <p>表达式语法支持四则运算（+ - * / %）、括号分组与一元负号；整数按 long 运算
 * （溢出/除零抛 {@link ExprException}），小数走 double/BigDecimal 路径。</p>
 *
 * <p><b>未命中变量行为</b>：以 {@code field.} / {@code path.} / {@code var.} 为前缀的变量
 * 引用若在上下文中不存在，求值为空串 ""（并记 DEBUG 日志）；未知的裸标识符（无上述前缀）
 * 仍按其名字字符串返回，以兼容 {@code ${alpha_num}} / {@code ${uuid('nodash')}} 之外的
 * 枚举 token 机制（如 charset 名）。</p>
 *
 * <p>解析结果（AST）按表达式文本缓存在实例内 LRU（上限 {@link #AST_CACHE_MAX} 条）。
 * AST 为纯数据结构、求值只依赖 {@link EvalContext}，跨上下文复用安全；
 * 注意：若运行期向注册表动态注册新函数，旧表达式缓存的「无括号函数调用」判定不会更新。</p>
 */
public class ExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ExpressionEvaluator.class);

    /** AST 缓存上限（条数） */
    static final int AST_CACHE_MAX = 512;

    private final FunctionRegistry registry;

    /** 表达式文本 → 解析后的 AST（synchronized LRU） */
    private final Map<String, Ast.Node> astCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Ast.Node>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Ast.Node> eldest) {
                    return size() > AST_CACHE_MAX;
                }
            });

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
        return evalNode(parseCached(expr), ctx);
    }

    /** 解析并缓存：AST 只含纯数据节点，求值仅依赖 EvalContext，可跨上下文复用 */
    private Ast.Node parseCached(String expr) {
        Ast.Node cached = astCache.get(expr);
        if (cached != null) {
            return cached;
        }
        Ast.Node node = new Parser(expr, registry.nameSet()).parse();
        astCache.put(expr, node);
        return node;
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
            // 带上下文前缀的变量未命中：按空串处理（占位符语义），不再把前缀名当字面量输出
            if (name.startsWith("field.") || name.startsWith("path.") || name.startsWith("var.")) {
                log.debug("表达式变量未命中，返回空串: {}", name);
                return "";
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
        if (node instanceof Ast.Minus) {
            Ast.Minus m = (Ast.Minus) node;
            return arith('-', evalNode(m.left, ctx), evalNode(m.right, ctx));
        }
        if (node instanceof Ast.Mul) {
            Ast.Mul m = (Ast.Mul) node;
            return arith('*', evalNode(m.left, ctx), evalNode(m.right, ctx));
        }
        if (node instanceof Ast.Div) {
            Ast.Div d = (Ast.Div) node;
            return arith('/', evalNode(d.left, ctx), evalNode(d.right, ctx));
        }
        if (node instanceof Ast.Mod) {
            Ast.Mod m = (Ast.Mod) node;
            return arith('%', evalNode(m.left, ctx), evalNode(m.right, ctx));
        }
        if (node instanceof Ast.Neg) {
            return negate(evalNode(((Ast.Neg) node).inner, ctx));
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
            return arith('+', l, r);
        }
        return stringify(l) + stringify(r);
    }

    /** 四则运算：整数 long（addExact 等防溢出），BigInteger 任意精度，小数 BigDecimal */
    private static Object arith(char op, Object l, Object r) {
        if (!isNumber(l) || !isNumber(r)) {
            throw new ExprException("运算 '" + op + "' 需要数值操作数: "
                    + stringify(l) + " " + op + " " + stringify(r));
        }
        if (isIntegral(l) && isIntegral(r)) {
            if (l instanceof BigInteger || r instanceof BigInteger) {
                return bigIntegerArith(op, toBigInteger(l), toBigInteger(r));
            }
            return longArith(op, ((Number) l).longValue(), ((Number) r).longValue());
        }
        return decimalArith(op, l, r);
    }

    private static Object longArith(char op, long a, long b) {
        try {
            switch (op) {
                case '+':
                    return Math.addExact(a, b);
                case '-':
                    return Math.subtractExact(a, b);
                case '*':
                    return Math.multiplyExact(a, b);
                case '/':
                    if (b == 0) {
                        throw new ExprException("除数为零: " + a + " / 0");
                    }
                    if (a == Long.MIN_VALUE && b == -1) {
                        throw new ExprException("整数除法溢出: " + a + " / " + b);
                    }
                    return a / b;
                case '%':
                    if (b == 0) {
                        throw new ExprException("模数为零: " + a + " % 0");
                    }
                    return a % b;
                default:
                    throw new IllegalArgumentException("未知运算符: " + op);
            }
        } catch (ArithmeticException e) {
            throw new ExprException("整数运算溢出: " + a + " " + op + " " + b);
        }
    }

    private static Object bigIntegerArith(char op, BigInteger a, BigInteger b) {
        switch (op) {
            case '+':
                return a.add(b);
            case '-':
                return a.subtract(b);
            case '*':
                return a.multiply(b);
            case '/':
                if (b.signum() == 0) {
                    throw new ExprException("除数为零: " + a + " / 0");
                }
                return a.divide(b);
            case '%':
                if (b.signum() == 0) {
                    throw new ExprException("模数为零: " + a + " % 0");
                }
                return a.remainder(b);
            default:
                throw new IllegalArgumentException("未知运算符: " + op);
        }
    }

    private static Object decimalArith(char op, Object l, Object r) {
        BigDecimal a = toBigDecimal(l);
        BigDecimal b = toBigDecimal(r);
        switch (op) {
            case '+':
                return a.add(b);
            case '-':
                return a.subtract(b);
            case '*':
                return a.multiply(b);
            case '/':
                if (b.signum() == 0) {
                    throw new ExprException("除数为零: " + stringify(l) + " / " + stringify(r));
                }
                return a.divide(b, MathContext.DECIMAL64);
            case '%':
                if (b.signum() == 0) {
                    throw new ExprException("模数为零: " + stringify(l) + " % " + stringify(r));
                }
                return a.remainder(b, MathContext.DECIMAL64);
            default:
                throw new IllegalArgumentException("未知运算符: " + op);
        }
    }

    private static Object negate(Object v) {
        if (v instanceof BigInteger) {
            return ((BigInteger) v).negate();
        }
        if (isSmallIntegral(v)) {
            long x = ((Number) v).longValue();
            if (x == Long.MIN_VALUE) {
                throw new ExprException("整数取反溢出: " + x);
            }
            return -x;
        }
        if (isNumber(v)) {
            return toBigDecimal(v).negate();
        }
        throw new ExprException("一元负号需要数值操作数: " + stringify(v));
    }

    public static boolean isNumber(Object o) {
        return isIntegral(o) || o instanceof Double || o instanceof Float || o instanceof BigDecimal;
    }

    private static boolean isSmallIntegral(Object o) {
        return o instanceof Long || o instanceof Integer || o instanceof Short || o instanceof Byte;
    }

    private static boolean isIntegral(Object o) {
        return isSmallIntegral(o) || o instanceof BigInteger;
    }

    private static BigInteger toBigInteger(Object o) {
        if (o instanceof BigInteger) {
            return (BigInteger) o;
        }
        if (isSmallIntegral(o)) {
            return BigInteger.valueOf(((Number) o).longValue());
        }
        return toBigDecimal(o).toBigInteger();
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
