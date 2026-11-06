package com.ambtransfer.exception;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound() {
        return new ApiException(404, "NOT_FOUND", "记录不存在或无权访问");
    }
}
