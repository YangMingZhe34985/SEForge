package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.QuestionType;
import java.math.BigDecimal;
import java.util.List;

public record AssignmentQuestionView(
        Long id,
        QuestionType type,
        String prompt,
        List<String> options,
        BigDecimal points,
        int orderIndex,
        Long knowledgePointId) {
}
