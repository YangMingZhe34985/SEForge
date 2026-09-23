package com.ustb.seforge.content.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.content.domain.KnowledgeDocument;
import com.ustb.seforge.content.service.KnowledgeDocumentService;
import com.ustb.seforge.identity.security.UserPrincipal;
import com.ustb.seforge.job.api.AsyncJobView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/knowledge/documents")
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService documents;

    public KnowledgeDocumentController(KnowledgeDocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiEnvelope<DocumentUploadView> upload(@PathVariable Long courseId,
                                                  @RequestParam(required = false) Long chapterId,
                                                  @RequestParam MultipartFile file,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(documents.upload(courseId, chapterId, principal.userId(), file));
    }

    @GetMapping
    public ApiEnvelope<List<KnowledgeDocumentView>> list(@PathVariable Long courseId,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(documents.list(courseId, principal.userId()));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long courseId,
                                                        @PathVariable Long documentId,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        KnowledgeDocument document = documents.requireReadable(courseId, documentId, principal.userId());
        InputStream input = documents.open(document);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getMediaType()))
                .contentLength(document.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(input));
    }

    @PostMapping("/{documentId}/reindex")
    public ApiEnvelope<AsyncJobView> reindex(@PathVariable Long courseId,
                                             @PathVariable Long documentId,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(documents.reindex(courseId, documentId, principal.userId()));
    }

    @DeleteMapping("/{documentId}")
    public ApiEnvelope<Void> delete(@PathVariable Long courseId,
                                    @PathVariable Long documentId,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        documents.delete(courseId, documentId, principal.userId());
        return ApiEnvelope.success("Document deleted", null);
    }
}
