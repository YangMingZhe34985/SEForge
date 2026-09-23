package com.ustb.seforge.ai.application;

import java.util.List;

/**
 * Implemented by request-scoped tool objects that can supply execution latency and
 * failure details which LangChain4j's {@code ToolExecution} does not expose.
 */
public interface AiToolCallRecorder {
    default void beginAiAttempt() {
    }

    List<AiToolCall> recordedToolCalls();
}
