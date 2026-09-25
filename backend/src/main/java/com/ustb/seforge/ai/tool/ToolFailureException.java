package com.ustb.seforge.ai.tool;

public class ToolFailureException extends RuntimeException {
    private final ToolErrorCode code;
    public ToolFailureException(ToolErrorCode code) {
        super(code.name());
        this.code = code;
    }
    public ToolErrorCode code() { return code; }
    public boolean retryable() { return code.retryable(); }
}
