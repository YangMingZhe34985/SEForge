package com.ustb.seforge.ai.tool;

public enum ToolErrorCode {
    INVALID_ARGUMENT(false), FORBIDDEN(false), NOT_FOUND(false), BUDGET_EXCEEDED(false),
    TIMEOUT(true), DEPENDENCY_FAILURE(true);

    private final boolean retryable;
    ToolErrorCode(boolean retryable) { this.retryable = retryable; }
    public boolean retryable() { return retryable; }
}
