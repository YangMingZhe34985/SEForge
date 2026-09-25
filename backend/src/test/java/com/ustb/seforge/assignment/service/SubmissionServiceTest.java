package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ustb.seforge.assignment.api.SaveSubmissionRequest;
import com.ustb.seforge.assignment.api.SubmissionAnswerRequest;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.AssignmentStatus;
import com.ustb.seforge.assignment.domain.QuestionType;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.common.exception.AppException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubmissionServiceTest {
    @Test
    void savingAnswerTextPreservesServerManagedAttachmentKey() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentQuestionRepository questions = mock(AssignmentQuestionRepository.class);
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionAnswerRepository answers = mock(SubmissionAnswerRepository.class);
        AssignmentAuthorizationService authorization = mock(AssignmentAuthorizationService.class);
        TutorPolicyCodec policies = mock(TutorPolicyCodec.class);
        SubmissionService service = new SubmissionService(assignments, questions, submissions,
                answers, authorization, policies, new ObjectMapper(),
                new SubmissionCompletenessValidator(new ObjectMapper()));

        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(1L);
        when(assignment.getStatus()).thenReturn(AssignmentStatus.PUBLISHED);
        when(assignment.isAvailableAt(any(Instant.class))).thenReturn(true);
        when(assignments.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(authorization.requireStudent(assignment, 7L)).thenReturn(mock(CourseMember.class));
        when(policies.read(null)).thenReturn(TutorPolicy.defaults());

        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(30L);
        when(submission.getAssignmentId()).thenReturn(1L);
        when(submission.getStatus()).thenReturn(SubmissionStatus.DRAFT);
        when(submission.getAttemptNo()).thenReturn(1);
        when(submissions.findDraftForUpdate(1L, 7L, SubmissionStatus.DRAFT))
                .thenReturn(List.of(submission));

        AssignmentQuestion question = mock(AssignmentQuestion.class);
        when(question.getId()).thenReturn(2L);
        AssignmentQuestion unansweredQuestion = mock(AssignmentQuestion.class);
        when(unansweredQuestion.getId()).thenReturn(3L);
        when(questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(question, unansweredQuestion));
        SubmissionAnswer answer = new SubmissionAnswer(
                30L, 2L, "old", null, "courses/10/submissions/30/questions/2/id/source.zip");
        when(answers.findBySubmissionIdAndQuestionId(30L, 2L)).thenReturn(Optional.of(answer));
        when(answers.findAllBySubmissionIdOrderByIdAsc(30L)).thenReturn(List.of(answer));

        var result = service.saveDraft(1L, 7L, new SaveSubmissionRequest(List.of(
                new SubmissionAnswerRequest(2L, TextNode.valueOf("new answer")))));

        assertThat(answer.getAnswerText()).isEqualTo("new answer");
        assertThat(answer.getAttachmentObjectKey())
                .isEqualTo("courses/10/submissions/30/questions/2/id/source.zip");
        assertThat(result.answers().getFirst().attachmentObjectKey())
                .isEqualTo(answer.getAttachmentObjectKey());
        assertThat(result.answers().getFirst().attachmentFileName()).isEqualTo("source.zip");
    }

    @Test
    void submitRejectsMissingAnswersWithoutChangingDraftStatus() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentQuestionRepository questions = mock(AssignmentQuestionRepository.class);
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionAnswerRepository answers = mock(SubmissionAnswerRepository.class);
        AssignmentAuthorizationService authorization = mock(AssignmentAuthorizationService.class);
        TutorPolicyCodec policies = mock(TutorPolicyCodec.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SubmissionService service = new SubmissionService(assignments, questions, submissions,
                answers, authorization, policies, objectMapper,
                new SubmissionCompletenessValidator(objectMapper));

        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(1L);
        when(assignment.getStatus()).thenReturn(AssignmentStatus.PUBLISHED);
        when(assignment.isAvailableAt(any(Instant.class))).thenReturn(true);
        when(assignments.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(authorization.requireStudent(assignment, 7L)).thenReturn(mock(CourseMember.class));
        when(policies.read(null)).thenReturn(TutorPolicy.defaults());

        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(30L);
        when(submissions.findDraftForUpdate(1L, 7L, SubmissionStatus.DRAFT))
                .thenReturn(List.of(submission));
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        when(question.getId()).thenReturn(2L);
        when(question.getQuestionType()).thenReturn(QuestionType.SHORT_ANSWER);
        when(questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(question));
        when(answers.findAllBySubmissionIdOrderByIdAsc(30L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(1L, 7L, new SaveSubmissionRequest(List.of())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Every assignment question");
        verify(submission, never()).submit(any(Instant.class), anyBoolean());
    }

    @Test
    void delayedAttachmentRequestCannotOpenAnotherAttemptAfterSubmission() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        AssignmentAuthorizationService authorization = mock(AssignmentAuthorizationService.class);
        TutorPolicyCodec policies = mock(TutorPolicyCodec.class);
        ObjectMapper mapper = new ObjectMapper();
        SubmissionService service = new SubmissionService(assignments, mock(AssignmentQuestionRepository.class),
                submissions, mock(SubmissionAnswerRepository.class), authorization, policies, mapper,
                new SubmissionCompletenessValidator(mapper));
        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(1L);
        when(assignment.getStatus()).thenReturn(AssignmentStatus.PUBLISHED);
        when(assignment.isAvailableAt(any(Instant.class))).thenReturn(true);
        when(assignments.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(authorization.requireStudent(assignment, 7L)).thenReturn(mock(CourseMember.class));
        when(policies.read(null)).thenReturn(TutorPolicy.defaults());
        Submission submitted = mock(Submission.class);
        when(submitted.getAttemptNo()).thenReturn(1);
        when(submitted.getStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        when(submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(1L, 7L))
                .thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service.requireCurrentDraftForAttachment(1L, 7L, 1, false))
                .isInstanceOf(AppException.class).hasMessageContaining("start a new attempt");
        verify(submissions, never()).save(any(Submission.class));
    }
}
