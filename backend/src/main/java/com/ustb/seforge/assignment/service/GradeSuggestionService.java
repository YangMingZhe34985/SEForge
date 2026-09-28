package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.domain.Feedback;
import com.ustb.seforge.assignment.domain.FeedbackSource;
import com.ustb.seforge.assignment.domain.Grade;
import com.ustb.seforge.assignment.domain.GradeStatus;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.FeedbackRepository;
import com.ustb.seforge.assignment.repository.GradeRepository;
import com.ustb.seforge.assignment.repository.RubricItemRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeSuggestionService {
    private final SubmissionRepository submissions;
    private final GradeRepository grades;
    private final FeedbackRepository feedback;
    private final RubricItemRepository rubricItems;
    private final com.ustb.seforge.assignment.repository.RubricRepository rubrics;
    private final ObjectMapper objectMapper;
    private final com.ustb.seforge.assignment.repository.AssignmentQuestionRepository questions;
    private final com.ustb.seforge.assignment.repository.SubmissionAnswerRepository answers;
    private final ObjectiveScorer scorer;
    private final AssignmentMediaService media;

    public GradeSuggestionService(SubmissionRepository submissions, GradeRepository grades,
                                  FeedbackRepository feedback, RubricItemRepository rubricItems,
                                  com.ustb.seforge.assignment.repository.RubricRepository rubrics,
                                  ObjectMapper objectMapper,
                                  com.ustb.seforge.assignment.repository.AssignmentQuestionRepository questions,
                                  com.ustb.seforge.assignment.repository.SubmissionAnswerRepository answers, ObjectiveScorer scorer, AssignmentMediaService media) {
        this.submissions = submissions;
        this.grades = grades;
        this.feedback = feedback;
        this.rubricItems = rubricItems;
        this.rubrics = rubrics;
        this.objectMapper = objectMapper;
        this.questions=questions; this.answers=answers; this.scorer=scorer;
        this.media=media;
    }

    @org.springframework.context.event.EventListener
    @Transactional
    public void submitted(SubmissionService.SubmissionReady event) {
        Submission submission=submissions.findById(event.submissionId()).orElseThrow(()->notFound("Submission not found"));
        Grade grade=grades.findBySubmissionId(submission.getId()).orElseGet(()->grades.save(new Grade(submission.getId(),submission.getCourseId(),submission.getUserId())));
        var all=questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(submission.getAssignmentId());
        if(all.stream().noneMatch(q->q.getQuestionType().objective()))return;
        var rules=rules(submission);
        if(rules.isEmpty())return;
        feedback.deleteAll(feedback.findAllByGradeIdAndSource(grade.getId(), FeedbackSource.RULE));
        for(var item:rules)feedback.save(new Feedback(grade.getId(),item.rubricItemId(),null,FeedbackSource.RULE,item.feedback(),item.suggestedScore(),json(item.evidence()),"[]").forQuestion(item.questionId()));
        grade.applyRuleSuggestion(rules.stream().map(AiRubricSuggestion::suggestedScore).reduce(BigDecimal.ZERO,BigDecimal::add),all.stream().allMatch(q->q.getQuestionType().objective()));
    }

    public List<AiRubricSuggestion> rules(Submission submission) {
        var all=questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(submission.getAssignmentId());
        var items = rubricItemsFor(submission);
        var stored=answers.findAllBySubmissionIdOrderByIdAsc(submission.getId());
        List<AiRubricSuggestion> result=new java.util.ArrayList<>();
        for(var target:ScoringTargets.of(all, items)) {
            Long questionId = target.questionId() != null ? target.questionId() : items.stream().filter(i -> i.getId().equals(target.rubricItemId())).findFirst().orElseThrow().getQuestionId();
            var question=all.stream().filter(q->q.getId().equals(questionId)).findFirst().orElse(null);
            if(question==null && all.stream().anyMatch(q->q.getQuestionType().objective()))throw QuestionContent.invalid("Mixed/objective grading requires each rubric item to bind one question");
            if(question==null || !question.getQuestionType().objective())continue;
            var answer=stored.stream().filter(a->a.getQuestionId().equals(question.getId())).findFirst().orElse(null);
            BigDecimal score=scorer.score(question,answer).signum()>0?target.maximum():BigDecimal.ZERO;
            result.add(new AiRubricSuggestion(target.rubricItemId(),score,"Deterministic exact-match grading (RULE); teacher confirmation required",
                    List.of("questionId="+question.getId(),"submissionId="+submission.getId(),"algorithm=objective:v1"),List.of(),target.questionId()));
        }
        return result;
    }

    /** Image-only student answers have no confirmed text semantics: leave them to the teacher. */
    public Set<Long> manualItems(Submission submission) {
        var manualQuestions = manualQuestions(submission);
        var items=rubricItemsFor(submission);
        if(!manualQuestions.isEmpty()&&items.stream().anyMatch(i->i.getQuestionId()==null))throw QuestionContent.invalid("Manual grading requires question-bound rubric items");
        return items.stream().filter(i->manualQuestions.contains(i.getQuestionId())).map(i->i.getId()).collect(java.util.stream.Collectors.toSet());
    }

    public Set<Long> manualQuestions(Submission submission) {
        var stored=answers.findAllBySubmissionIdOrderByIdAsc(submission.getId());
        Set<Long> manualQuestions=new HashSet<>();
        for(var q:questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(submission.getAssignmentId())) {
            if(q.getQuestionType().objective())continue;
            boolean manual=QuestionGrading.mode(q)==QuestionGrading.Mode.MANUAL;
            var answer=stored.stream().filter(a->a.getQuestionId().equals(q.getId())).findFirst().orElse(null);
            if(answer!=null && (answer.getAnswerText()==null||answer.getAnswerText().isBlank()) && answer.getAnswerDataJson()!=null) {
                try {
                    var data=objectMapper.readTree(answer.getAnswerDataJson());var ids=QuestionContent.ids(data.path("assetIds"));
                    if(data.path("text").asText().isBlank()&&!ids.isEmpty()&&ids.stream().allMatch(id->media.bound(id,q.getAssignmentId(),com.ustb.seforge.assignment.domain.AssignmentMedia.Purpose.ANSWER,submission.getId(),q.getId()).getMediaType().startsWith("image/")))manual=true;
                }catch(JsonProcessingException e){throw QuestionContent.invalid("Invalid stored answer");}
            }
            if(manual)manualQuestions.add(q.getId());
        }
        return manualQuestions;
    }

    private List<com.ustb.seforge.assignment.domain.RubricItem> rubricItemsFor(Submission submission) {
        return rubrics.findByAssignmentId(submission.getAssignmentId())
                .map(r -> rubricItems.findAllByRubricIdOrderBySortOrderAscIdAsc(r.getId())).orElse(List.of());
    }

    /**
     * Persists an auditable AI suggestion only. This method never confirms or publishes a final grade.
     */
    @Transactional
    public Grade applySuggestion(Long submissionId, Long courseId, Long studentId, BigDecimal total,
                                 String model, String promptVersion,
                                 List<AiRubricSuggestion> itemSuggestions) {
        Submission submission = submissions.findForGrading(submissionId)
                .orElseThrow(() -> notFound("Submission not found"));
        if (!submission.getCourseId().equals(courseId) || !submission.getUserId().equals(studentId)) {
            throw notFound("Submission not found");
        }
        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT, "Draft submissions cannot be graded");
        }
        if (total == null || total.signum() < 0 || total.stripTrailingZeros().scale() > 2) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Suggested score must be non-negative");
        }
        Grade grade = grades.findForUpdate(submissionId)
                .orElseGet(() -> new Grade(submissionId, courseId, studentId));
        if (grade.isFinal()) {
            throw new AppException(ErrorCode.CONFLICT, "A confirmed grade cannot be replaced by AI");
        }
        grades.save(grade);

        if (itemSuggestions == null || itemSuggestions.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "A complete rubric suggestion is required");
        }
        var allQuestions = questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(submission.getAssignmentId());
        if (total.compareTo(allQuestions.stream().map(q -> q.getMaxScore()).reduce(BigDecimal.ZERO, BigDecimal::add)) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Suggested score exceeds rubric total");
        }
        feedback.deleteAll(feedback.findAllByGradeIdAndSource(grade.getId(), FeedbackSource.AI));
        feedback.deleteAll(feedback.findAllByGradeIdAndSource(grade.getId(), FeedbackSource.RULE));
        var ruleById=rules(submission).stream().collect(java.util.stream.Collectors.toMap(AiRubricSuggestion::key, value->value));
        var allItems = rubricItemsFor(submission);
        var targets = ScoringTargets.of(allQuestions, allItems).stream().collect(java.util.stream.Collectors.toMap(ScoringTargets.Target::key, value -> value));
        BigDecimal itemTotal = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();
        for (AiRubricSuggestion item : itemSuggestions == null ? List.<AiRubricSuggestion>of() : itemSuggestions) {
            if (item == null || !seen.add(item.key())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Rubric suggestions must be unique");
            }
            var rubricItem = targets.get(item.key());
            if (rubricItem == null) throw notFound("Rubric item not found");
            if (item.suggestedScore() == null || item.suggestedScore().signum() < 0 || item.suggestedScore().stripTrailingZeros().scale() > 2
                    || item.suggestedScore().compareTo(rubricItem.maximum()) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid rubric score suggestion");
            }
            itemTotal = itemTotal.add(item.suggestedScore());
            var rule=ruleById.get(item.key());
            if (item.questionId() != null && rule == null) throw QuestionContent.invalid("AI cannot supply question-level manual scores");
            if(rule!=null && rule.suggestedScore().compareTo(item.suggestedScore())!=0)throw QuestionContent.invalid("Objective score cannot be supplied by AI");
            feedback.save(new Feedback(grade.getId(), item.rubricItemId(), null, rule==null?FeedbackSource.AI:FeedbackSource.RULE,
                    rule==null?required(item.feedback()):rule.feedback(),item.suggestedScore(),json(rule==null?item.evidence():rule.evidence()),json(item.issueCodes())).forQuestion(item.questionId()));
        }
        var manualIds = manualItems(submission);
        var manualQuestionIds = manualQuestions(submission);
        Set<String> expected = targets.values().stream()
                .filter(t -> t.rubricItemId() != null ? !manualIds.contains(t.rubricItemId()) : !manualQuestionIds.contains(t.questionId()))
                .map(ScoringTargets.Target::key).collect(java.util.stream.Collectors.toSet());
        if (!seen.equals(expected)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Every rubric item must appear exactly once");
        }
        if (itemTotal.compareTo(total) != 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Suggested total must equal the rubric item sum");
        }
        var ruleTotal=ruleById.values().stream().map(AiRubricSuggestion::suggestedScore).reduce(BigDecimal.ZERO,BigDecimal::add);
        grade.applyMixedSuggestion(total,ruleTotal,model,promptVersion,expected.size()>ruleById.size());
        return grade;
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Invalid AI grade suggestion");
        }
    }

    private String required(String value) {
        return value == null || value.isBlank() ? "No feedback supplied" : value;
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
