package com.miragemock.dsl.eval;

import java.util.List;

/**
 * 表达式 AST 节点。
 *
 * <p>设计要点：函数调用的参数列表 {@link FuncCall#argSpecs} 中，元素可能为 {@link Node}（需进一步求值）
 * 或 {@link String}（原始文本，仅 date/datetime 等特殊函数使用，不参与表达式求值）。
 */
public final class Ast {

    private Ast() {
    }

    public interface Node {
    }

    /** 字符串字面量 */
    public static final class Str implements Node {
        public final String value;

        public Str(String value) {
            this.value = value;
        }
    }

    /** 数字字面量：Long / Double / BigDecimal */
    public static final class Num implements Node {
        public final Object value;

        public Num(Object value) {
            this.value = value;
        }
    }

    /** 变量 / 裸标识符引用 */
    public static final class Var implements Node {
        public final String name;

        public Var(String name) {
            this.name = name;
        }
    }

    /** 嵌套 ${...}：内部为一个完整表达式节点 */
    public static final class Nested implements Node {
        public final Node inner;

        public Nested(Node inner) {
            this.inner = inner;
        }
    }

    /** 加法 / 字符串拼接 */
    public static final class Plus implements Node {
        public final Node left;
        public final Node right;

        public Plus(Node left, Node right) {
            this.left = left;
            this.right = right;
        }
    }

    /** 函数调用 */
    public static final class FuncCall implements Node {
        public final String name;
        /** 每个元素为 Node（求值）或 String（原始文本） */
        public final List<Object> argSpecs;

        public FuncCall(String name, List<Object> argSpecs) {
            this.name = name;
            this.argSpecs = argSpecs;
        }
    }
}
