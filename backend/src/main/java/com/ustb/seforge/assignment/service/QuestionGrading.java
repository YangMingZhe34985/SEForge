package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.domain.AssignmentQuestion;

/** Existing records without an explicit mode retain their original type-based behavior. */
public final class QuestionGrading {
    public enum Mode { RULE, AI_ASSISTED, MANUAL }
    private QuestionGrading() {}
    public static Mode mode(AssignmentQuestion question) {
        if(question.getQuestionType().objective())return Mode.RULE;
        String value=QuestionContent.config(question).path("gradingMode").asText();
        return value.isBlank()?Mode.AI_ASSISTED:Mode.valueOf(value);
    }
}
