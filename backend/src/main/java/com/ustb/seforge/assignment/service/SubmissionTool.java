package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionTool {
    private final SubmissionRepository submissions;
    private final SubmissionAnswerRepository answers;
    private final AssignmentQuestionRepository questions;
    private final AssignmentAuthorizationService authorization;
    private final SubmissionCompletenessValidator completeness;

    public SubmissionTool(SubmissionRepository submissions, SubmissionAnswerRepository answers,
                          AssignmentQuestionRepository questions, AssignmentAuthorizationService authorization,
                          SubmissionCompletenessValidator completeness) {
        this.submissions = submissions;
        this.answers = answers;
        this.questions = questions;
        this.authorization = authorization;
        this.completeness = completeness;
    }

    @Transactional(readOnly = true)
    public State current(Assignment assignment, Long questionId, Long userId) {
        authorization.requireStudent(assignment, userId);
        Submission submission = submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(
                assignment.getId(), userId).orElse(null);
        if (submission == null) return new State(null, false, null);
        List<SubmissionAnswer> currentAnswers = answers.findAllBySubmissionIdOrderByIdAsc(submission.getId());
        SubmissionAnswer answer = currentAnswers.stream()
                .filter(value -> value.getQuestionId().equals(questionId)).findFirst().orElse(null);
        String value = answer == null ? null
                : answer.getAnswerText() != null ? answer.getAnswerText() : answer.getAnswerDataJson();
        boolean validSubmission = (submission.getStatus() == SubmissionStatus.SUBMITTED
                || submission.getStatus() == SubmissionStatus.GRADED)
                && completeness.isComplete(
                        questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignment.getId()), currentAnswers);
        return new State(submission.getId(), validSubmission, value);
    }

    public record State(Long submissionId, boolean validSubmission, String persistedAnswer) {
    }
}
