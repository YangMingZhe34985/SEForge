package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.AssignmentStatus;
import jakarta.validation.constraints.NotNull;

public record AssignmentTransitionRequest(@NotNull AssignmentStatus status) {
}
