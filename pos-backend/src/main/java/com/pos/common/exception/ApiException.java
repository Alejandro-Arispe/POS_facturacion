package com.pos.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Object detail;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.detail = message;
    }

    public ApiException(HttpStatus status, Object detail, String message) {
        super(message);
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getDetail() {
        return detail;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(Object detail, String message) {
        return new ApiException(HttpStatus.CONFLICT, detail, message);
    }
}
