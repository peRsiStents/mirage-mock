package com.miragemock.admin.controller;

import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一转 Result 包装。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        log.warn("业务异常: {} - {}", e.getResultCode(), e.getMessage());
        return Result.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> validation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getDefaultMessage();
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> illegalArg(IllegalArgumentException e) {
        return Result.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(com.miragemock.dsl.eval.ExprException.class)
    public Result<Void> expr(com.miragemock.dsl.eval.ExprException e) {
        return Result.fail(ResultCode.EXPRESSION_ERROR, e.getMessage());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> other(Throwable e) {
        log.error("未处理异常", e);
        // 不把原始 message（可能含表名/SQL/堆栈）回前端，避免信息泄露
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.SERVER_ERROR, "服务器内部错误，请查看服务端日志"));
    }
}
