package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ustb.seforge.assignment.api.SaveSubmissionRequest;
import com.ustb.seforge.assignment.api.SubmissionAnswerRequest;
import com.ustb.seforge.assignment.api.SubmissionAnswerView;
import com.ustb.seforge.assignment.api.SubmissionView;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.AssignmentStatus;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.domain.CourseMember;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {
    private static final EnumSet<SubmissionStatus> COUNTED_ATTEMPTS =
            EnumSet.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED);

    private final AssignmentRepository assignments;
    private final AssignmentQuestionRepository questions;
    private final SubmissionRepository submissions;
    private final SubmissionAnswerRepository answers;
    private final AssignmentAuthorizationService authorization;
    private final TutorPolicyCodec policies;
    private final ObjectMapper objectMapper;
    private final SubmissionCompletenessValidator completeness;

    public SubmissionService(AssignmentRepository assignments, AssignmentQuestionRepository questions,
                             SubmissionRepository submissions, SubmissionAnswerRepository answers,
                             AssignmentAuthorizationService authorization, TutorPolicyCodec policies,
                             ObjectMapper objectMapper, SubmissionCompletenessValidator completeness) {
        this.assignments = assignments;
        this.questions = questions;
        this.submissions = submissions;
        this.answers = answers;
        this.authorization = authorization;
        this.policies = policies;
        this.objectMapper = objectMapper;
        this.completeness = completeness;
    }

    @Transactional(readOnly = true)
    public SubmissionView current(Long assignmentId, Long userId) {
        Assignment assignment = requireAssignment(assignmentId);
        authorization.requireStudent(assignment, userId);
        return submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(assignmentId, userId)
                .map(this::view).orElse(null);
    }

    @Transactional
    public SubmissionView saveDraft(Long assignmentId, Long userId, SaveSubmissionRequest request) {
        Assignment assignment = lockAssignment(assignmentId);
        CourseMember member = authorization.requireStudent(assignment, userId);
        Instant now = Instant.now();
        Deadline deadline = requireAccepting(assignment, userId, now);
        requireExpectedAttempt(assignmentId, userId, request.expectedAttempt());
        Submission latest = submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(assignmentId, userId)
                .orElse(null);
        if (latest != null && latest.getStatus() != SubmissionStatus.DRAFT
                && !Boolean.TRUE.equals(request.startNextAttempt())) {
            throw new AppException(ErrorCode.CONFLICT,
                    "The previous attempt is submitted; start a new attempt explicitly");
        }
        Submission draft = requireOrCreateDraft(assignment, member, userId);
        upsertAnswers(assignment, draft, request.answers());
        return view(draft);
    }

    @Transactional
    public SubmissionView submit(Long assignmentId, Long userId, SaveSubmissionRequest request) {
        Assignment assignment = lockAssignment(assignmentId);
        CourseMember member = authorization.requireStudent(assignment, userId);
        if (request.submissionKey() != null) {
            Submission previous = submissions.findByAssignmentIdAndUserIdAndSubmissionKey(
                    assignmentId, userId, request.submissionKey()).orElse(null);
            if (previous != null) {
                for (SubmissionAnswerRequest requested : request.answers()) {
                    SubmissionAnswer stored = answers.findBySubmissionIdAndQuestionId(previous.getId(), requested.questionId())
                            .orElseThrow(() -> new AppException(ErrorCode.CONFLICT,
                                    "Submission key was used with different answers"));
                    JsonNode supplied = requested.answer() == null ? NullNode.getInstance() : requested.answer();
                    if (!decode(stored).equals(supplied)) {
                        throw new AppException(ErrorCode.CONFLICT,
                                "Submission key was used with different answers");
                    }
                }
                return view(previous);
            }
        }
        Instant now = Instant.now();
        Deadline deadline = requireAccepting(assignment, userId, now);
        requireExpectedAttempt(assignmentId, userId, request.expectedAttempt());
        Submission draft = requireOrCreateDraft(assignment, member, userId);
        upsertAnswers(assignment, draft, request.answers());
        completeness.requireComplete(
                questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignment.getId()),
                answers.findAllBySubmissionIdOrderByIdAsc(draft.getId()));
        if (request.submissionKey() != null) draft.bindSubmissionKey(request.submissionKey());
        draft.submit(now, deadline.late());
        return view(draft);
    }

    @Transactional(readOnly = true)
    public Submission requireOwned(Long submissionId, Long assignmentId, Long userId) {
        return submissions.findByIdAndAssignmentIdAndUserId(submissionId, assignmentId, userId)
                .orElseThrow(() -> notFound("Submission not found"));
    }

    @Transactional
    public Submission requireCurrentDraftForAttachment(Long assignmentId, Long userId) {
        return requireCurrentDraftForAttachment(assignmentId, userId, null, false);
    }

    @Transactional
    public Submission requireCurrentDraftForAttachment(Long assignmentId, Long userId,
                                                       Integer expectedAttempt, boolean startNextAttempt) {
        Assignment assignment = lockAssignment(assignmentId);
        CourseMember member = authorization.requireStudent(assignment, userId);
        requireAccepting(assignment, userId, Instant.now());
        requireExpectedAttempt(assignmentId, userId, expectedAttempt);
        Submission latest = submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(assignmentId, userId)
                .orElse(null);
        if (latest != null && latest.getStatus() != SubmissionStatus.DRAFT && !startNextAttempt) {
            throw new AppException(ErrorCode.CONFLICT,
                    "The previous attempt is submitted; start a new attempt explicitly");
        }
        return requireOrCreateDraft(assignment, member, userId);
    }

    Submission requireOrCreateDraft(Assignment assignment, CourseMember member, Long userId) {
        List<Submission> drafts = submissions.findDraftForUpdate(
                assignment.getId(), userId, SubmissionStatus.DRAFT);
        if (!drafts.isEmpty()) return drafts.getFirst();
        long used = submissions.countByAssignmentIdAndUserIdAndStatusIn(
                assignment.getId(), userId, COUNTED_ATTEMPTS);
        if (used >= assignment.getMaxSubmissions()) {
            throw new AppException(ErrorCode.CONFLICT, "Maximum submission attempts reached");
        }
        int attempt = submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(assignment.getId(), userId)
                .map(value -> value.getAttemptNo() + 1).orElse(1);
        return submissions.save(new Submission(assignment.getId(), assignment.getCourseId(), member.getClassId(),
                userId, attempt));
    }

    private void upsertAnswers(Assignment assignment, Submission submission,
                               List<SubmissionAnswerRequest> requestedAnswers) {
        submission.requireDraft();
        List<AssignmentQuestion> assignmentQuestions =
                questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignment.getId());
        Set<Long> validQuestionIds = assignmentQuestions.stream().map(AssignmentQuestion::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (SubmissionAnswerRequest requested : requestedAnswers) {
            if (!seen.add(requested.questionId())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Duplicate answer for question " + requested.questionId());
            }
            if (!validQuestionIds.contains(requested.questionId())) {
                throw notFound("Question does not belong to this assignment");
            }
            EncodedAnswer encoded = encode(requested.answer());
            SubmissionAnswer answer = answers.findBySubmissionIdAndQuestionId(submission.getId(), requested.questionId())
                    .orElseGet(() -> new SubmissionAnswer(submission.getId(), requested.questionId(),
                            null, null, null));
            answer.update(encoded.text(), encoded.json(), answer.getAttachmentObjectKey());
            answers.save(answer);
        }
    }

    private Deadline requireAccepting(Assignment assignment, Long userId, Instant now) {
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED || !assignment.isAvailableAt(now)) {
            throw new AppException(ErrorCode.CONFLICT, "Assignment is not accepting submissions");
        }
        TutorPolicy policy = policies.read(assignment.getTutorPolicyJson());
        Instant effectiveDue = policy.effectiveDueAt(userId, assignment.getDueAt());
        boolean late = effectiveDue != null && now.isAfter(effectiveDue);
        if (late && !policy.isLateSubmissionAllowed()) {
            throw new AppException(ErrorCode.CONFLICT, "Assignment deadline has passed");
        }
        return new Deadline(late, effectiveDue);
    }

    private Assignment lockAssignment(Long assignmentId) {
        return assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found"));
    }

    private void requireExpectedAttempt(Long assignmentId, Long userId, Integer expectedAttempt) {
        if (expectedAttempt == null) return;
        int latestAttempt = submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(assignmentId, userId)
                .map(Submission::getAttemptNo).orElse(0);
        if (latestAttempt != expectedAttempt) {
            throw new AppException(ErrorCode.CONFLICT, "Submission attempt changed; reload before saving");
        }
    }

    private Assignment requireAssignment(Long assignmentId) {
        return assignments.findById(assignmentId).orElseThrow(() -> notFound("Assignment not found"));
    }

    SubmissionView view(Submission submission) {
        List<SubmissionAnswerView> answerViews = answers.findAllBySubmissionIdOrderByIdAsc(submission.getId())
                .stream().map(answer -> new SubmissionAnswerView(answer.getQuestionId(), decode(answer),
                        answer.getAttachmentObjectKey(), attachmentFileName(answer.getAttachmentObjectKey())))
                .toList();
        return new SubmissionView(submission.getId(), submission.getAssignmentId(), submission.getStatus(),
                answerViews, submission.getAttemptNo(), submission.getUpdatedAt(), submission.getSubmittedAt(),
                submission.isLate());
    }

    private EncodedAnswer encode(JsonNode value) {
        if (value == null || value.isNull()) return new EncodedAnswer(null, null);
        if (value.isTextual()) return new EncodedAnswer(value.textValue(), null);
        return new EncodedAnswer(null, value.toString());
    }

    private JsonNode decode(SubmissionAnswer answer) {
        if (answer.getAnswerDataJson() != null) {
            try {
                return objectMapper.readTree(answer.getAnswerDataJson());
            } catch (JsonProcessingException ignored) {
                return NullNode.getInstance();
            }
        }
        return answer.getAnswerText() == null ? NullNode.getInstance() : TextNode.valueOf(answer.getAnswerText());
    }

    private String attachmentFileName(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        int separator = objectKey.lastIndexOf('/');
        return separator >= 0 ? objectKey.substring(separator + 1) : objectKey;
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private record EncodedAnswer(String text, String json) {
    }

    private record Deadline(boolean late, Instant effectiveDueAt) {
    }
}
