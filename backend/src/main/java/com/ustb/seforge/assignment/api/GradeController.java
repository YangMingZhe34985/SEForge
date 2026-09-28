package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.service.GradeService;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GradeController {
    private final GradeService grades;
    private final com.ustb.seforge.assignment.service.GradingAttachmentService attachments;

    public GradeController(GradeService grades, com.ustb.seforge.assignment.service.GradingAttachmentService attachments) {
        this.grades = grades;
        this.attachments=attachments;
    }

    @GetMapping("/grades")
    public ApiEnvelope<PageResponse<GradeRecordView>> list(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(grades.list(courseId, principal.userId(), page, size));
    }

    @GetMapping("/submissions/{submissionId}/grade")
    public ApiEnvelope<GradeRecordView> get(@PathVariable Long submissionId,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(grades.getForSubmission(submissionId, principal.userId()));
    }

    @PostMapping("/submissions/{submissionId}/grade/confirm")
    public ApiEnvelope<GradeRecordView> confirm(@PathVariable Long submissionId,
                                                @AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody ConfirmGradeRequest request) {
        return ApiEnvelope.success(grades.confirm(submissionId, principal.userId(), request));
    }

    @GetMapping("/submissions/{submissionId}/grading")
    public ApiEnvelope<GradeService.GradingDetail> detail(@PathVariable Long submissionId,@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(grades.detail(submissionId,principal.userId()));
    }
    @GetMapping("/submissions/{submissionId}/grading/attachments/{questionId}")
    public org.springframework.http.ResponseEntity<byte[]> attachment(@PathVariable Long submissionId,@PathVariable Long questionId,@AuthenticationPrincipal UserPrincipal principal){
        var file=attachments.read(submissionId,questionId,principal.userId());
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
                .header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(file.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(file.bytes());
    }
    @PostMapping("/submissions/{submissionId}/grade/manual-review")
    public ApiEnvelope<GradeRecordView> manual(@PathVariable Long submissionId,@AuthenticationPrincipal UserPrincipal principal,@Valid @RequestBody ConfirmGradeRequest request) {
        return ApiEnvelope.success(grades.manual(submissionId,principal.userId(),request));
    }
    @PostMapping("/submissions/{submissionId}/grade/publish")
    public ApiEnvelope<GradeRecordView> publish(@PathVariable Long submissionId,@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(grades.publish(submissionId,principal.userId()));
    }
    @GetMapping("/assignments/{assignmentId}/grades/publication")
    public ApiEnvelope<GradeService.PublicationPreview> preview(@PathVariable Long assignmentId,@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(grades.publicationPreview(assignmentId,principal.userId()));
    }
    @PostMapping("/assignments/{assignmentId}/grades/publish")
    public ApiEnvelope<GradeService.PublicationPreview> publishAssignment(@PathVariable Long assignmentId,@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(grades.publishAssignment(assignmentId,principal.userId()));
    }
}
