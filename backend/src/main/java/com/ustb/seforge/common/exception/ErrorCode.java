package com.ustb.seforge.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    COURSE_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ALREADY_COURSE_MEMBER(HttpStatus.CONFLICT),
    INVITE_INVALID(HttpStatus.BAD_REQUEST),
    INVITE_EXPIRED(HttpStatus.GONE),
    INVITE_EXHAUSTED(HttpStatus.GONE),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INFRASTRUCTURE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    VECTOR_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
