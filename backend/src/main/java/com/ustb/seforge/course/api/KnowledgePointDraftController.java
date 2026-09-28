package com.ustb.seforge.course.api;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.course.service.KnowledgePointDraftService;
import com.ustb.seforge.identity.security.UserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/courses/{course}/chapters/{chapter}/knowledge-point-drafts")
public class KnowledgePointDraftController {
    private final KnowledgePointDraftService drafts;
    public KnowledgePointDraftController(KnowledgePointDraftService drafts){this.drafts=drafts;}
    @PostMapping
    public ApiEnvelope<KnowledgePointDraftService.Draft> generate(@PathVariable Long course,@PathVariable Long chapter,@AuthenticationPrincipal UserPrincipal user){
        return ApiEnvelope.success(drafts.generate(course,chapter,user.userId()));
    }
    public record Confirmation(List<KnowledgePointDraftService.Proposal> points){}
    @PostMapping("/{draft}/confirm")
    public ApiEnvelope<List<Long>> confirm(@PathVariable Long course,@PathVariable Long chapter,@PathVariable String draft,@AuthenticationPrincipal UserPrincipal user,@RequestBody Confirmation input){
        return ApiEnvelope.success(drafts.confirm(course,chapter,user.userId(),draft,input.points()));
    }
}
