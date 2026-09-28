package com.ustb.seforge.conversation.api;

import com.ustb.seforge.conversation.domain.ConversationGeneration;

public record GenerationView(String requestId, String status, String question, String traceId,
                             Long assistantMessageId, String errorCode, String errorMessage) {
    public static GenerationView from(ConversationGeneration value) {
        return new GenerationView(value.getRequestId(), value.getStatus().name(), value.getQuestion(),
                value.getTraceId(), value.getAssistantMessageId(), value.getErrorCode(), value.getErrorMessage());
    }
}
