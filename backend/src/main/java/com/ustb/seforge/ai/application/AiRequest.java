package com.ustb.seforge.ai.application;

import java.util.List;

public record AiRequest(
        ModelCapability capability,
        Long userId,
        Long courseId,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        List<AiToolCall> toolCalls,
        AiMemoryContext memoryContext) {

    public AiRequest(ModelCapability capability, Long userId, Long courseId, String promptVersion,
                     String systemPrompt, String userPrompt) {
        this(capability, userId, courseId, promptVersion, systemPrompt, userPrompt, List.of(), null);
    }

    public AiRequest(ModelCapability capability, Long userId, Long courseId, String promptVersion,
                     String systemPrompt, String userPrompt, List<AiToolCall> toolCalls) {
        this(capability, userId, courseId, promptVersion, systemPrompt, userPrompt, toolCalls, null);
    }

    public AiRequest {
        if (capability == null) throw new IllegalArgumentException("capability is required");
        if (promptVersion == null || promptVersion.isBlank()) throw new IllegalArgumentException("promptVersion is required");
        if (userPrompt == null || userPrompt.isBlank()) throw new IllegalArgumentException("userPrompt is required");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        if (toolCalls.size() > 64 || toolCalls.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("toolCalls must contain at most 64 entries");
        }
    }
}
