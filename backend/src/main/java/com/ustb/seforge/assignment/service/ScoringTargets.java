package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.domain.*;
import java.math.BigDecimal;
import java.util.*;

/** Existing rubric rows plus question-level RULE/MANUAL scores where no rubric allocation exists. */
public final class ScoringTargets {
    private ScoringTargets() {}
    public record Target(Long rubricItemId, Long questionId, BigDecimal maximum) {
        public String key() { return keyOf(rubricItemId, questionId); }
    }
    public static String keyOf(Long rubricId, Long questionId) {
        if ((rubricId == null) == (questionId == null)) throw QuestionContent.invalid("Specify exactly one rubric item or question");
        return rubricId != null ? "r:" + rubricId : "q:" + questionId;
    }
    public static List<Target> of(List<AssignmentQuestion> questions, List<RubricItem> items) {
        var result = new ArrayList<Target>();
        items.forEach(i -> result.add(new Target(i.getId(), null, i.getMaxScore())));
        // Preserve old, published whole-assignment rubrics. New publishing requires question bindings.
        if (items.stream().anyMatch(i -> i.getQuestionId() == null)) return result;
        for (var q : questions) {
            if (QuestionGrading.mode(q) == QuestionGrading.Mode.AI_ASSISTED) continue;
            BigDecimal allocated = items.stream().filter(i -> q.getId().equals(i.getQuestionId()))
                    .map(RubricItem::getMaxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remaining = q.getMaxScore().subtract(allocated);
            if (remaining.signum() > 0) result.add(new Target(null, q.getId(), remaining));
        }
        return List.copyOf(result);
    }
}
