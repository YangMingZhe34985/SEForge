package com.ustb.seforge.assignment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveSubmissionRequest(
        @NotNull @Size(max = 500) List<@Valid SubmissionAnswerRequest> answers) {
}
