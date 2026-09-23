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

    public GradeController(GradeService grades) {
        this.grades = grades;
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
}
