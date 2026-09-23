package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.service.AssignmentService;
import com.ustb.seforge.assignment.service.TutorPolicy;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignments/{assignmentId}")
public class AssignmentController {
    private final AssignmentService assignments;

    public AssignmentController(AssignmentService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    public ApiEnvelope<AssignmentDetailsView> get(@PathVariable Long assignmentId,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(assignments.get(assignmentId, principal.userId()));
    }

    @PatchMapping
    public ApiEnvelope<AssignmentSummaryView> update(@PathVariable Long assignmentId,
                                                      @AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody UpdateAssignmentRequest request) {
        return ApiEnvelope.success(assignments.update(assignmentId, principal.userId(), request));
    }

    @PostMapping("/transition")
    public ApiEnvelope<AssignmentDetailsView> transition(@PathVariable Long assignmentId,
                                                          @AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody AssignmentTransitionRequest request) {
        return ApiEnvelope.success(assignments.transition(assignmentId, principal.userId(), request.status()));
    }

    @PostMapping("/questions")
    public ResponseEntity<ApiEnvelope<AssignmentQuestionView>> addQuestion(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.success(assignments.addQuestion(assignmentId, principal.userId(), request)));
    }

    @PutMapping("/questions/{questionId}")
    public ApiEnvelope<AssignmentQuestionView> updateQuestion(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertQuestionRequest request) {
        return ApiEnvelope.success(assignments.updateQuestion(
                assignmentId, questionId, principal.userId(), request));
    }

    @DeleteMapping("/questions/{questionId}")
    public ApiEnvelope<Void> deleteQuestion(@PathVariable Long assignmentId,
                                            @PathVariable Long questionId,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        assignments.deleteQuestion(assignmentId, questionId, principal.userId());
        return ApiEnvelope.success("Question deleted", null);
    }

    @PutMapping("/tutor-policy")
    public ApiEnvelope<TutorPolicy> updatePolicy(@PathVariable Long assignmentId,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestBody TutorPolicy policy) {
        return ApiEnvelope.success(assignments.updatePolicy(assignmentId, principal.userId(), policy));
    }

    @PutMapping("/extensions/{studentId}")
    public ApiEnvelope<TutorPolicy> setExtension(@PathVariable Long assignmentId,
                                                  @PathVariable Long studentId,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestBody AssignmentExtensionRequest request) {
        return ApiEnvelope.success(assignments.setExtension(
                assignmentId, studentId, principal.userId(), request.dueAt()));
    }
}
