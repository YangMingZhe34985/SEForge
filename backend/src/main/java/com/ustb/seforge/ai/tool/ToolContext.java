package com.ustb.seforge.ai.tool;

import java.util.Set;

/** Server-created scope; never deserialize this record from model or HTTP input. */
public record ToolContext(Long userId, Long courseId, Long assignmentId, Long questionId,
                          Long submissionId, Set<String> allowedTools, String requestId, String traceId) {
    public ToolContext {
        if (userId == null || userId <= 0 || courseId == null || courseId <= 0
                || requestId == null || traceId == null) throw new IllegalArgumentException("Trusted scope required");
        allowedTools = Set.copyOf(allowedTools);
    }
}
