package com.ustb.seforge.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ustb.seforge.common.exception.AppException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssignmentTest {
    @Test
    void enforcesLifecycleTransitions() {
        Assignment assignment = assignment();
        Instant now = Instant.parse("2026-09-22T08:00:00Z");

        assignment.transitionTo(AssignmentStatus.PUBLISHED, now);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PUBLISHED);

        assignment.transitionTo(AssignmentStatus.CLOSED, now);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.CLOSED);
        assertThat(assignment.getClosedAt()).isEqualTo(now);

        assignment.transitionTo(AssignmentStatus.PUBLISHED, now.plusSeconds(1));
        assertThat(assignment.getClosedAt()).isNull();
        assignment.transitionTo(AssignmentStatus.ARCHIVED, now.plusSeconds(2));

        assertThatThrownBy(() -> assignment.transitionTo(AssignmentStatus.PUBLISHED, now.plusSeconds(3)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void publishedAssignmentCannotBeEdited() {
        Assignment assignment = assignment();
        assignment.transitionTo(AssignmentStatus.PUBLISHED, Instant.now());

        assertThatThrownBy(() -> assignment.update("Changed", null, null, null, 2, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("draft");
    }

    @Test
    void validatesDatesAndAttempts() {
        Instant due = Instant.parse("2026-09-22T08:00:00Z");
        assertThatThrownBy(() -> new Assignment(1L, null, 2L, "A", null,
                due, due.minusSeconds(1), 1, null)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> new Assignment(1L, null, 2L, "A", null,
                null, due, 0, null)).isInstanceOf(AppException.class);
    }

    private Assignment assignment() {
        return new Assignment(1L, null, 2L, "Assignment", "Description", null,
                Instant.parse("2026-10-01T08:00:00Z"), 2, null);
    }
}
