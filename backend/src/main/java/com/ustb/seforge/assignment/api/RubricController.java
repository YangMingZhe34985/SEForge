package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.service.RubricService;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignments/{assignmentId}/rubric")
public class RubricController {
    private final RubricService rubrics;

    public RubricController(RubricService rubrics) {
        this.rubrics = rubrics;
    }

    @GetMapping
    public ApiEnvelope<RubricView> get(@PathVariable Long assignmentId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(rubrics.get(assignmentId, principal.userId()));
    }

    @PutMapping
    public ApiEnvelope<RubricView> upsert(@PathVariable Long assignmentId,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody UpsertRubricRequest request) {
        return ApiEnvelope.success(rubrics.upsert(assignmentId, principal.userId(), request));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiEnvelope<RubricItemView>> addItem(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertRubricItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.success(rubrics.addItem(assignmentId, principal.userId(), request)));
    }

    @PutMapping("/items/{itemId}")
    public ApiEnvelope<RubricItemView> updateItem(@PathVariable Long assignmentId,
                                                  @PathVariable Long itemId,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody UpsertRubricItemRequest request) {
        return ApiEnvelope.success(rubrics.updateItem(assignmentId, itemId, principal.userId(), request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiEnvelope<Void> deleteItem(@PathVariable Long assignmentId,
                                        @PathVariable Long itemId,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        rubrics.deleteItem(assignmentId, itemId, principal.userId());
        return ApiEnvelope.success("Rubric item deleted", null);
    }
}
