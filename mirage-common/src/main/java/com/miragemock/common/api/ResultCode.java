package com.miragemock.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    OK(0, "success"),

    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源冲突"),

    LOGIN_FAILED(40101, "用户名或密码错误"),
    PROJECT_NOT_FOUND(40401, "项目不存在"),
    INTERFACE_NOT_FOUND(40402, "接口不存在"),
    RULE_NOT_FOUND(40403, "规则不存在"),
    KEY_NOT_FOUND(40404, "密钥不存在"),
    KEY_ALIAS_EXISTS(40901, "密钥别名已存在"),

    TEMPLATE_RENDER_ERROR(50001, "模板渲染失败"),
    TEMPLATE_CYCLE(50002, "模板字段存在循环依赖"),
    EXPRESSION_ERROR(50003, "表达式求值错误"),
    CRYPTO_ERROR(50004, "加解密/签名失败"),
    KEY_ALIAS_NOT_FOUND(50005, "引用的密钥别名不存在"),

    SERVER_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;
}
