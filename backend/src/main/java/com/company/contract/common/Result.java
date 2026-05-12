package com.company.contract.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok() {
        return new Result<>(0, "ok", null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data, null);
    }

    public static <T> Result<T> fail(ErrorCode error) {
        return new Result<>(error.getCode(), error.getMessage(), null, null);
    }

    public static <T> Result<T> fail(ErrorCode error, String message) {
        return new Result<>(error.getCode(), message, null, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }
}
