package com.ustb.seforge.common.exception;

public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object details;

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AppException(ErrorCode errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
