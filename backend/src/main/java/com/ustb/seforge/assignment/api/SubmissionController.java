package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.service.SubmissionService;
import com.ustb.seforge.assignment.service.SubmissionManagementService;
import com.ustb.seforge.assignment.service.SubmissionAttachmentService;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assignments/{assignmentId}/submissions")
public class SubmissionController {
    private final SubmissionService submissions;
    private final SubmissionManagementService management;
    private final SubmissionAttachmentService attachments;

    public SubmissionController(SubmissionService submissions, SubmissionManagementService management,
                                SubmissionAttachmentService attachments) {
        this.submissions = submissions;
        this.management = management;
        this.attachments = attachments;
    }

    @GetMapping("/me")
    public ApiEnvelope<SubmissionView> current(@PathVariable Long assignmentId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(submissions.current(assignmentId, principal.userId()));
    }

    @GetMapping
    public ApiEnvelope<List<TeacherSubmissionView>> submitted(@PathVariable Long assignmentId,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(management.listSubmitted(assignmentId, principal.userId()));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiEnvelope<SubmissionAttachmentView> uploadAttachment(
            @PathVariable Long assignmentId,
            @RequestParam Long questionId,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(attachments.upload(
                assignmentId, questionId, principal.userId(), file));
    }

    @PutMapping("/draft")
    public ApiEnvelope<SubmissionView> saveDraft(@PathVariable Long assignmentId,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody SaveSubmissionRequest request) {
        return ApiEnvelope.success(submissions.saveDraft(assignmentId, principal.userId(), request));
    }

    @PostMapping
    public ApiEnvelope<SubmissionView> submit(@PathVariable Long assignmentId,
                                               @AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody SaveSubmissionRequest request) {
        return ApiEnvelope.success(submissions.submit(assignmentId, principal.userId(), request));
    }
}
