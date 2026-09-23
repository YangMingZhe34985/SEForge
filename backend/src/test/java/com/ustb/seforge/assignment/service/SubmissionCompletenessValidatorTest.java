package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.QuestionType;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.common.exception.AppException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubmissionCompletenessValidatorTest {
    private final SubmissionCompletenessValidator validator =
            new SubmissionCompletenessValidator(new ObjectMapper());

    @Test
    void acceptsCompleteAnswersForEverySupportedQuestionType() {
        List<AssignmentQuestion> questions = List.of(
                question(1L, QuestionType.SINGLE_CHOICE, "[\"A\",\"B\"]"),
                question(2L, QuestionType.MULTIPLE_CHOICE, "[\"A\",\"B\",\"C\"]"),
                question(3L, QuestionType.TRUE_FALSE, null),
                question(4L, QuestionType.SHORT_ANSWER, null),
                question(5L, QuestionType.ANALYSIS, null),
                question(6L, QuestionType.DESIGN, null),
                question(7L, QuestionType.CODE, null));
        List<SubmissionAnswer> answers = List.of(
                text(1L, "A"),
                json(2L, "[\"A\",\"C\"]"),
                json(3L, "false"),
                text(4L, "short"),
                text(5L, "analysis"),
                text(6L, "design"),
                new SubmissionAnswer(10L, 7L, null, null, "submissions/source.zip"));

        assertThat(validator.isComplete(questions, answers)).isTrue();
    }

    @Test
    void rejectsMissingBlankAndMalformedAnswers() {
        List<AssignmentQuestion> questions = List.of(
                question(1L, QuestionType.SINGLE_CHOICE, "[\"A\",\"B\"]"),
                question(2L, QuestionType.MULTIPLE_CHOICE, "[\"A\",\"B\"]"),
                question(3L, QuestionType.TRUE_FALSE, null),
                question(4L, QuestionType.SHORT_ANSWER, null),
                question(5L, QuestionType.CODE, null),
                question(6L, QuestionType.DESIGN, null));
        List<SubmissionAnswer> answers = List.of(
                text(1L, "unknown"),
                json(2L, "[]"),
                text(3L, "false"),
                text(4L, "   "),
                new SubmissionAnswer(10L, 5L, null, null, null));

        assertThat(validator.isComplete(questions, answers)).isFalse();
        assertThatThrownBy(() -> validator.requireComplete(questions, answers))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Every assignment question");
    }

    @Test
    void rejectsAnAssignmentWithoutQuestions() {
        assertThat(validator.isComplete(List.of(), List.of())).isFalse();
    }

    private AssignmentQuestion question(Long id, QuestionType type, String options) {
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        when(question.getId()).thenReturn(id);
        when(question.getQuestionType()).thenReturn(type);
        when(question.getOptionsJson()).thenReturn(options);
        return question;
    }

    private SubmissionAnswer text(Long questionId, String value) {
        return new SubmissionAnswer(10L, questionId, value, null, null);
    }

    private SubmissionAnswer json(Long questionId, String value) {
        return new SubmissionAnswer(10L, questionId, null, value, null);
    }
}
