package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.SemesterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateSemesterRequest(
        @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotNull SemesterStatus status) {
}
