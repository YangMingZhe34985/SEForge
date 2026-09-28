package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.course.service.CourseResourceFileService;
import com.ustb.seforge.content.service.KnowledgeDocumentService;
import com.ustb.seforge.content.api.DocumentUploadView;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/resources")
public class TeachingResourceController {
    private final CourseResourceFileService resources;
    private final KnowledgeDocumentService knowledge;
    public TeachingResourceController(CourseResourceFileService resources,KnowledgeDocumentService knowledge){this.resources=resources;this.knowledge=knowledge;}
    public record Edit(@NotBlank @Size(max=255) String name,@Size(max=5000) String description){}
    public record Link(Long chapterId,@NotBlank @Size(max=255) String name,@NotBlank @Size(max=2048) String url,@Size(max=5000) String description){}
    @PutMapping("/{id}")
    public ApiEnvelope<CourseResourceView> edit(@PathVariable Long courseId,@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody Edit input){
        return ApiEnvelope.success(resources.update(courseId,id,user.userId(),input.name(),input.description()));
    }
    @PostMapping("/links")
    public ApiEnvelope<CourseResourceView> link(@PathVariable Long courseId,@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody Link input){
        return ApiEnvelope.success(resources.link(courseId,input.chapterId(),user.userId(),input.name(),input.url(),input.description()));
    }
    @PostMapping("/{id}/knowledge")
    public ApiEnvelope<DocumentUploadView> include(@PathVariable Long courseId,@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){
        return ApiEnvelope.success(knowledge.includeResource(courseId,id,user.userId()));
    }
}
