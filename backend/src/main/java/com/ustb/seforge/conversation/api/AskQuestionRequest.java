package com.ustb.seforge.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "requestId contains unsupported characters")
        String requestId,
        @NotBlank @Size(max = 8000) String content) {
}
