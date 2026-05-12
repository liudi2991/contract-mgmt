package com.company.contract.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),

    PARAM_INVALID(10001, "参数校验失败"),
    BUSINESS_INVALID(10002, "业务规则校验失败"),

    UNAUTHORIZED(20001, "未登录或登录已过期"),
    PASSWORD_INVALID(20002, "用户名或密码错误"),
    ACCOUNT_LOCKED(20003, "账号已被锁定，请稍后再试"),
    ACCOUNT_DISABLED(20004, "账号已停用"),
    MUST_CHANGE_PASSWORD(20005, "首次登录请修改密码"),

    FORBIDDEN(20101, "无权访问此资源"),

    NOT_FOUND(30001, "资源不存在"),

    CONFLICT(40001, "资源冲突"),
    STATE_INVALID(40002, "当前状态不允许此操作"),

    RATE_LIMIT(50001, "请求过于频繁，请稍后再试"),

    INTERNAL_ERROR(90001, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
