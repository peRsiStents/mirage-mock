package com.miragemock.admin.controller;

import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * 唯一约束冲突（并发下的「先查后插」竞态，由数据库唯一索引兜底）。
     * 之前会落进 Throwable 分支返回 500，语义上应是 409 冲突。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> duplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMostSpecificCause().getMessage());
        return Result.fail(ResultCode.CONFLICT, "数据已存在（唯一约束冲突）");
    }

    /** 其它数据完整性问题（长度超限、非空约束、引用不存在等），同样不该是 500。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> dataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性约束失败: {}", e.getMostSpecificCause().getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "数据不满足约束条件，请检查输入");
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> other(Throwable e) {
        log.error("未处理异常", e);
        // 不把原始 message（可能含表名/SQL/堆栈）回前端，避免信息泄露
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.SERVER_ERROR, "服务器内部错误，请查看服务端日志"));
    }
}
