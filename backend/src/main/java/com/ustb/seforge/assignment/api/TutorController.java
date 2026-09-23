package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.service.TutorService;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignments/{assignmentId}/tutor")
public class TutorController {
    private final TutorService tutor;

    public TutorController(TutorService tutor) {
        this.tutor = tutor;
    }

    @PostMapping
    public ApiEnvelope<TutorResponseView> ask(@PathVariable Long assignmentId,
                                              @AuthenticationPrincipal UserPrincipal principal,
                                              @Valid @RequestBody TutorRequest request) {
        return ApiEnvelope.success(tutor.ask(assignmentId, principal.userId(), request));
    }
}
