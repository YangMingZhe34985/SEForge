package com.ustb.seforge.review.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.identity.security.UserPrincipal;
import com.ustb.seforge.review.domain.ReviewType;
import com.ustb.seforge.review.service.ReviewSubmissionService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/reviews")
public class ReviewController {
    private final ReviewSubmissionService reviews;

    public ReviewController(ReviewSubmissionService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/documents")
    public ApiEnvelope<ReviewJobView> document(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateDocumentReviewRequest request) {
        return ApiEnvelope.success(reviews.submitDocument(courseId, principal.userId(), request));
    }

    @PostMapping("/assignments")
    public ApiEnvelope<ReviewJobView> assignment(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAssignmentReviewRequest request) {
        return ApiEnvelope.success(reviews.submitAssignment(courseId, principal.userId(), request));
    }

    @PostMapping("/code")
    public ApiEnvelope<ReviewJobView> code(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCodeReviewRequest request) {
        return ApiEnvelope.success(reviews.submitCode(courseId, principal.userId(), request));
    }

    @GetMapping
    public ApiEnvelope<PageResponse<ReviewJobView>> list(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ReviewType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiEnvelope.success(reviews.list(courseId, principal.userId(), type, page, size));
    }

    @GetMapping("/{reviewJobId}")
    public ApiEnvelope<ReviewJobView> get(
            @PathVariable Long courseId,
            @PathVariable Long reviewJobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(reviews.get(courseId, reviewJobId, principal.userId()));
    }

    @PostMapping("/{reviewJobId}/retry")
    public ApiEnvelope<ReviewJobView> retry(
            @PathVariable Long courseId,
            @PathVariable Long reviewJobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(reviews.retry(courseId, reviewJobId, principal.userId()));
    }

    @GetMapping("/{reviewJobId}/report")
    public ApiEnvelope<ReviewReportView> report(
            @PathVariable Long courseId,
            @PathVariable Long reviewJobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(reviews.reportForJob(courseId, reviewJobId, principal.userId()));
    }

    @GetMapping("/reports/{reportId}/export")
    public ResponseEntity<ByteArrayResource> export(
            @PathVariable Long courseId,
            @PathVariable Long reportId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ReviewSubmissionService.ExportedReport export = reviews.export(
                courseId, reportId, principal.userId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(export.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(export.content()));
    }
}
