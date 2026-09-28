package com.ustb.seforge.assignment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ustb.seforge.assignment.domain.TutorOperation;
import jakarta.validation.constraints.NotNull;

public record TutorRequest(
        @NotNull Long questionId,
        @NotNull TutorOperation action,
        JsonNode draftAnswer,
        @jakarta.validation.constraints.Pattern(regexp = "[a-fA-F0-9-]{36}") String requestKey) {
    public TutorRequest(Long questionId, TutorOperation action, JsonNode draftAnswer) {
        this(questionId, action, draftAnswer, null);
    }
}
