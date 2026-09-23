package com.ustb.seforge.assignment.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record SubmissionAnswerRequest(@NotNull Long questionId, JsonNode answer) {
}
