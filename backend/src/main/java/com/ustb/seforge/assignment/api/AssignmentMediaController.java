package com.ustb.seforge.assignment.api;
import com.ustb.seforge.assignment.domain.AssignmentMedia;
import com.ustb.seforge.assignment.service.*;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.identity.security.UserPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;

@RestController @RequestMapping("/api/v1/assignments/{assignmentId}/media")
public class AssignmentMediaController {
    private final AssignmentMediaService media;
    private final SubmissionService submissions;
    public AssignmentMediaController(AssignmentMediaService media,SubmissionService submissions){this.media=media;this.submissions=submissions;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiEnvelope<MediaView> upload(@PathVariable Long assignmentId,@RequestParam AssignmentMedia.Purpose purpose,
            @RequestParam MultipartFile file,@RequestParam(required=false) Long questionId,
            @RequestParam(required=false) Integer expectedAttempt,@RequestParam(defaultValue="false") boolean startNextAttempt,
            @AuthenticationPrincipal UserPrincipal user){
        var draft=purpose==AssignmentMedia.Purpose.ANSWER?submissions.requireCurrentDraftForAttachment(assignmentId,user.userId(),expectedAttempt,startNextAttempt):null;
        return ApiEnvelope.success(view(media.upload(assignmentId,user.userId(),purpose,draft,questionId,file)));
    }
    @GetMapping("/{id}")
    public ApiEnvelope<MediaView> info(@PathVariable Long assignmentId,@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){
        return ApiEnvelope.success(view(media.download(assignmentId,id,user.userId())));
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long assignmentId,@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user)throws IOException{
        var value=media.download(assignmentId,id,user.userId());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.getMediaType()))
                .header("X-Content-Type-Options","nosniff").header("Cache-Control","private, no-store")
                .header("Content-Disposition",ContentDisposition.attachment().filename(value.getFileName(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(media.read(value));
    }
    private MediaView view(AssignmentMedia m){return new MediaView(m.getId(),m.getFileName(),m.getMediaType(),m.getSizeBytes(),m.getSubmissionId(),m.getPurpose().canonical());}
    public record MediaView(Long id,String fileName,String mediaType,long sizeBytes,Long submissionId,AssignmentMedia.Purpose purpose){}
}
