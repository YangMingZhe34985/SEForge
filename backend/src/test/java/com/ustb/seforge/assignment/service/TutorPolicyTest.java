package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ustb.seforge.assignment.domain.TutorOperation;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TutorPolicyTest {
    @Test
    void defaultPolicyIsProgressive() {
        TutorPolicy policy = TutorPolicy.defaults();

        assertThat(policy.permits(TutorOperation.HINT, false, false)).isTrue();
        assertThat(policy.permits(TutorOperation.FULL_SOLUTION, false, false)).isFalse();
        assertThat(policy.permits(TutorOperation.FULL_SOLUTION, true, false)).isTrue();
        assertThat(policy.permits(TutorOperation.FULL_SOLUTION, false, true)).isTrue();
    }

    @Test
    void individualExtensionOverridesAssignmentDeadline() {
        Instant regular = Instant.parse("2026-09-22T08:00:00Z");
        Instant extension = regular.plusSeconds(3600);
        TutorPolicy policy = new TutorPolicy(false, true, true, false, null,
                Map.of("42", extension));

        assertThat(policy.effectiveDueAt(42L, regular)).isEqualTo(extension);
        assertThat(policy.effectiveDueAt(43L, regular)).isEqualTo(regular);
    }
}
