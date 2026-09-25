package com.ustb.seforge.assignment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import java.util.List;

public record SaveSubmissionRequest(
        @NotNull @Size(max = 500) List<@Valid SubmissionAnswerRequest> answers,
        @Size(max = 64) String submissionKey,
        @Min(0) Integer expectedAttempt,
        Boolean startNextAttempt) {
    public SaveSubmissionRequest(List<SubmissionAnswerRequest> answers) {
        this(answers, null, null, null);
    }
}
