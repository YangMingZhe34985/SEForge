package com.ustb.seforge.assignment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ustb.seforge.assignment.domain.TutorOperation;
import jakarta.validation.constraints.NotNull;

public record TutorRequest(
        @NotNull Long questionId,
        @NotNull TutorOperation action,
        JsonNode draftAnswer) {
}
