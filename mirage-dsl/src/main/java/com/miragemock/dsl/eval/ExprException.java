package com.miragemock.dsl.eval;

/**
 * 表达式词法/语法/求值异常。
 */
public class ExprException extends RuntimeException {

    public ExprException(String message) {
        super(message);
    }

    public ExprException(String message, Throwable cause) {
        super(message, cause);
    }
}
