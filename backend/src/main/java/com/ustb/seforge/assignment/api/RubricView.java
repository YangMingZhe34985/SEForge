package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.RubricStatus;
import java.math.BigDecimal;
import java.util.List;

public record RubricView(Long id, Long assignmentId, String title, BigDecimal totalScore,
                         RubricStatus status, List<RubricItemView> items) {
}
