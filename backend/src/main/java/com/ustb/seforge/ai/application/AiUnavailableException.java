package com.ustb.seforge.ai.application;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;

public class AiUnavailableException extends AppException {
    public AiUnavailableException(String message) {
        super(ErrorCode.AI_UNAVAILABLE, message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(ErrorCode.AI_UNAVAILABLE, message, cause == null ? null : cause.getClass().getSimpleName());
        if (cause != null) initCause(cause);
    }
}
