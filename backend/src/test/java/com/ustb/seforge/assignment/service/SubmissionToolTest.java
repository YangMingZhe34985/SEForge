package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubmissionToolTest {
    @Test
    void aCurrentDraftDoesNotInheritFullSolutionAccessFromAnOlderAttempt() {
        Fixture fixture = fixture(SubmissionStatus.DRAFT, true);

        SubmissionTool.State state = fixture.tool.current(fixture.assignment, 3L, 7L);

        assertThat(state.validSubmission()).isFalse();
        verify(fixture.submissions, never())
                .countByAssignmentIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyCollection());
    }

    @Test
    void aLegacyEmptySubmittedAttemptIsNotConsideredValid() {
        Fixture fixture = fixture(SubmissionStatus.SUBMITTED, false);

        assertThat(fixture.tool.current(fixture.assignment, 3L, 7L).validSubmission()).isFalse();
    }

    @Test
    void onlyTheCurrentCompleteSubmittedAttemptUnlocksPostSubmitPolicy() {
        Fixture fixture = fixture(SubmissionStatus.GRADED, true);

        assertThat(fixture.tool.current(fixture.assignment, 3L, 7L).validSubmission()).isTrue();
    }

    private Fixture fixture(SubmissionStatus status, boolean complete) {
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionAnswerRepository answers = mock(SubmissionAnswerRepository.class);
        AssignmentQuestionRepository questions = mock(AssignmentQuestionRepository.class);
        AssignmentAuthorizationService authorization = mock(AssignmentAuthorizationService.class);
        SubmissionCompletenessValidator completeness = mock(SubmissionCompletenessValidator.class);
        SubmissionTool tool = new SubmissionTool(submissions, answers, questions, authorization, completeness);
        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(2L);
        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(11L);
        when(submission.getStatus()).thenReturn(status);
        when(submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(2L, 7L))
                .thenReturn(Optional.of(submission));
        SubmissionAnswer answer = new SubmissionAnswer(11L, 3L, "draft", null, null);
        List<SubmissionAnswer> currentAnswers = List.of(answer);
        List<AssignmentQuestion> assignmentQuestions = List.of(mock(AssignmentQuestion.class));
        when(answers.findAllBySubmissionIdOrderByIdAsc(11L)).thenReturn(currentAnswers);
        when(questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(2L)).thenReturn(assignmentQuestions);
        when(completeness.isComplete(assignmentQuestions, currentAnswers)).thenReturn(complete);
        return new Fixture(tool, submissions, assignment);
    }

    private record Fixture(SubmissionTool tool, SubmissionRepository submissions, Assignment assignment) {
    }
}
