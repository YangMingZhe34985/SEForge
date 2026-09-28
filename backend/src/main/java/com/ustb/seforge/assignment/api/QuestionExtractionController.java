package com.ustb.seforge.assignment.api;
import com.ustb.seforge.assignment.service.QuestionExtractionService;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/assignments/{assignmentId}")
public class QuestionExtractionController {
    private final QuestionExtractionService service;
    public QuestionExtractionController(QuestionExtractionService service){this.service=service;}
    @PostMapping("/question-imports")
    public ApiEnvelope<QuestionExtractionService.ImportView> extract(@PathVariable Long assignmentId,@RequestParam List<Long> sourceFile,@RequestParam(defaultValue="false") boolean reparse,@RequestParam(required=false) Long reparseFile,@AuthenticationPrincipal UserPrincipal user){return ApiEnvelope.success(service.extract(assignmentId,user.userId(),sourceFile,reparse,reparseFile));}
    @GetMapping("/question-imports/{id}")
    public ApiEnvelope<QuestionExtractionService.ImportView> get(@PathVariable Long assignmentId,@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){return ApiEnvelope.success(service.get(assignmentId,user.userId(),id));}
    @PostMapping("/question-imports/{id}/confirm")
    public ApiEnvelope<List<AssignmentQuestionView>> confirm(@PathVariable Long assignmentId,@PathVariable Long id,@RequestBody @Valid QuestionExtractionService.ConfirmRequest request,@AuthenticationPrincipal UserPrincipal user){return ApiEnvelope.success(service.confirm(assignmentId,user.userId(),id,request));}
    @PostMapping("/reference-markdown-drafts")
    public ApiEnvelope<QuestionExtractionService.ReferenceDraft> reference(@PathVariable Long assignmentId,@RequestParam Long sourceFile,@AuthenticationPrincipal UserPrincipal user){return ApiEnvelope.success(service.reference(assignmentId,user.userId(),sourceFile));}
}
