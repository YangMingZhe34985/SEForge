package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.SemesterStatus;
import java.time.LocalDate;

public record SemesterView(
        Long id, String code, String name, LocalDate startsOn, LocalDate endsOn, SemesterStatus status) {
}
