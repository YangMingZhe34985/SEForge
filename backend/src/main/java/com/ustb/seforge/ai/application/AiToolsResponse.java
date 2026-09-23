package com.ustb.seforge.ai.application;

import java.util.List;

public record AiToolsResponse(AiResponse response, List<AiToolExecutionResult> toolExecutions) {
    public AiToolsResponse {
        toolExecutions = toolExecutions == null ? List.of() : List.copyOf(toolExecutions);
    }
}
