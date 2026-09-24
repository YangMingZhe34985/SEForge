package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.course.service.CourseService;
import com.ustb.seforge.course.service.CourseResourceFileService;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.core.io.InputStreamResource;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/courses/{courseId}")
public class CourseStructureController {
    private final CourseService courseService;
    private final AuditService auditService;
    private final CourseResourceFileService files;

    public CourseStructureController(CourseService courseService, AuditService auditService,
                                     CourseResourceFileService files) {
        this.courseService = courseService;
        this.auditService = auditService;
        this.files = files;
    }

    @GetMapping("/classes")
    public ApiEnvelope<List<CourseClassView>> classes(
            @PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.listClasses(courseId, principal.userId()));
    }

    @PostMapping("/classes")
    public ResponseEntity<ApiEnvelope<CourseClassView>> createClass(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCourseClassRequest request) {
        CourseClassView value = courseService.createClass(courseId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_CLASS_CREATE", "COURSE_CLASS", value.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(value));
    }

    @PutMapping("/classes/{classId}")
    public ApiEnvelope<CourseClassView> updateClass(@PathVariable Long courseId, @PathVariable Long classId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCourseClassRequest request) {
        CourseClassView value = courseService.updateClass(courseId, classId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_CLASS_UPDATE", "COURSE_CLASS", classId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(value);
    }

    @GetMapping("/invites")
    public ApiEnvelope<List<CourseInviteView>> invites(
            @PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.listInvites(courseId, principal.userId()));
    }

    @PostMapping("/invites")
    public ResponseEntity<ApiEnvelope<CourseInviteView>> createInvite(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateInviteRequest request) {
        CourseInviteView value = courseService.createInvite(courseId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_INVITE_CREATE", "COURSE_INVITE", value.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(value));
    }

    @DeleteMapping("/invites/{inviteId}")
    public ApiEnvelope<CourseInviteView> revokeInvite(@PathVariable Long courseId, @PathVariable Long inviteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        CourseInviteView value = courseService.revokeInvite(courseId, inviteId, principal.userId());
        auditService.record(principal.userId(), courseId, "COURSE_INVITE_REVOKE", "COURSE_INVITE", inviteId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(value);
    }

    @GetMapping("/members")
    public ApiEnvelope<List<CourseMemberView>> members(
            @PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.listMembers(courseId, principal.userId()));
    }

    @PutMapping("/members/{userId}")
    public ApiEnvelope<CourseMemberView> updateMember(@PathVariable Long courseId, @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCourseMemberRequest request) {
        CourseMemberView value = courseService.updateMember(courseId, userId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_MEMBER_UPDATE", "USER", userId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(value);
    }

    @GetMapping("/chapters")
    public ApiEnvelope<List<CourseChapterView>> chapters(
            @PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.listChapters(courseId, principal.userId()));
    }

    @PostMapping("/chapters")
    public ResponseEntity<ApiEnvelope<CourseChapterView>> createChapter(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateChapterRequest request) {
        CourseChapterView value = courseService.createChapter(courseId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_CHAPTER_CREATE", "COURSE_CHAPTER", value.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(value));
    }

    @PutMapping("/chapters/{chapterId}")
    public ApiEnvelope<CourseChapterView> updateChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateChapterRequest request) {
        CourseChapterView value = courseService.updateChapter(courseId, chapterId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_CHAPTER_UPDATE", "COURSE_CHAPTER", chapterId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(value);
    }

    @DeleteMapping("/chapters/{chapterId}")
    public ApiEnvelope<Void> deleteChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        courseService.deleteChapter(courseId, chapterId, principal.userId());
        auditService.record(principal.userId(), courseId, "COURSE_CHAPTER_DELETE", "COURSE_CHAPTER", chapterId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success("Chapter deleted", null);
    }

    @GetMapping("/knowledge-points")
    public ApiEnvelope<List<KnowledgePointView>> knowledgePoints(
            @PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.listKnowledgePoints(courseId, principal.userId()));
    }

    @PostMapping("/knowledge-points")
    public ResponseEntity<ApiEnvelope<KnowledgePointView>> createKnowledgePoint(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateKnowledgePointRequest request) {
        KnowledgePointView value = courseService.createKnowledgePoint(courseId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "KNOWLEDGE_POINT_CREATE", "KNOWLEDGE_POINT", value.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(value));
    }

    @PutMapping("/knowledge-points/{pointId}")
    public ApiEnvelope<KnowledgePointView> updateKnowledgePoint(@PathVariable Long courseId, @PathVariable Long pointId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateKnowledgePointRequest request) {
        KnowledgePointView value = courseService.updateKnowledgePoint(courseId, pointId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "KNOWLEDGE_POINT_UPDATE", "KNOWLEDGE_POINT", pointId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(value);
    }

    @DeleteMapping("/knowledge-points/{pointId}")
    public ApiEnvelope<Void> deleteKnowledgePoint(@PathVariable Long courseId, @PathVariable Long pointId,
            @AuthenticationPrincipal UserPrincipal principal) {
        courseService.deleteKnowledgePoint(courseId, pointId, principal.userId());
        auditService.record(principal.userId(), courseId, "KNOWLEDGE_POINT_DELETE", "KNOWLEDGE_POINT", pointId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success("Knowledge point deleted", null);
    }

    @GetMapping("/resources")
    public ApiEnvelope<List<CourseResourceView>> resources(
            @PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.listResources(courseId, principal.userId()));
    }

    @PostMapping("/resources")
    public ResponseEntity<ApiEnvelope<CourseResourceView>> createResource(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCourseResourceRequest request) {
        CourseResourceView value = courseService.createResource(courseId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_RESOURCE_CREATE", "COURSE_RESOURCE", value.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(value));
    }

    @GetMapping("/resources/{resourceId}")
    public ApiEnvelope<CourseResourceView> resource(
            @PathVariable Long courseId,
            @PathVariable Long resourceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.getResource(courseId, resourceId, principal.userId()));
    }

    @PostMapping(value = "/resources/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiEnvelope<CourseResourceView>> uploadResource(@PathVariable Long courseId,
            @RequestParam(required = false) Long chapterId, @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        CourseResourceView resource = files.upload(courseId, chapterId, principal.userId(), file);
        auditService.record(principal.userId(), courseId, "COURSE_RESOURCE_UPLOAD", "COURSE_RESOURCE", resource.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(resource));
    }

    @GetMapping("/resources/{resourceId}/download")
    public ResponseEntity<InputStreamResource> downloadResource(@PathVariable Long courseId,
            @PathVariable Long resourceId, @AuthenticationPrincipal UserPrincipal principal) {
        CourseResourceView resource = courseService.getResource(courseId, resourceId, principal.userId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resource.name(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(files.open(courseId, resourceId, principal.userId())));
    }

    @DeleteMapping("/resources/{resourceId}")
    public ApiEnvelope<Void> deleteResource(@PathVariable Long courseId, @PathVariable Long resourceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        courseService.removeResource(courseId, resourceId, principal.userId());
        auditService.record(principal.userId(), courseId, "COURSE_RESOURCE_DELETE", "COURSE_RESOURCE", resourceId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success("Resource removed", null);
    }
}
