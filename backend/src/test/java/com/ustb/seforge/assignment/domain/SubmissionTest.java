package com.ustb.seforge.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ustb.seforge.common.exception.AppException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SubmissionTest {
    @Test
    void submittedAttemptIsImmutableAndCanBeGraded() {
        Submission submission = new Submission(1L, 2L, 3L, 4L, 1);
        Instant submittedAt = Instant.parse("2026-09-22T08:00:00Z");

        submission.submit(submittedAt, true);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.isLate()).isTrue();
        assertThatThrownBy(submission::requireDraft).isInstanceOf(AppException.class);

        submission.markGraded();
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.GRADED);
    }
}
