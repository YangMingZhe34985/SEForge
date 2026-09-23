package com.ustb.seforge.assignment.api;

import java.math.BigDecimal;
import java.util.Map;

public record RubricItemView(Long id, Long questionId, String title, String description,
                             BigDecimal maxScore, Map<String, Object> criteria, int orderIndex) {
}
