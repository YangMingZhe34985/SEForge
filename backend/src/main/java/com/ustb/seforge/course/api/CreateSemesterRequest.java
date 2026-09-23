package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.SemesterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateSemesterRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotNull SemesterStatus status) {
}
