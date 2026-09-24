package com.ustb.seforge.course.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.course.api.CourseResourceView;
import com.ustb.seforge.course.api.CreateCourseResourceRequest;
import com.ustb.seforge.course.domain.ResourceType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CourseResourceFileService {
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private final CourseService courses;
    private final CourseAccessService access;
    private final ObjectStorage storage;

    public CourseResourceFileService(CourseService courses, CourseAccessService access, ObjectStorage storage) {
        this.courses = courses;
        this.access = access;
        this.storage = storage;
    }

    public CourseResourceView upload(Long courseId, Long chapterId, Long actorId, MultipartFile file) {
        access.requireTeachingStaff(courseId, actorId);
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Resource must be non-empty and at most 200 MB");
        }
        String original = file.getOriginalFilename() == null ? "resource" : file.getOriginalFilename();
        String fileName = original.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 255 || fileName.indexOf('\0') >= 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Resource filename is invalid");
        }
        String extension = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        ResourceType type = switch (extension) {
            case "ppt", "pptx" -> ResourceType.SLIDE;
            case "pdf", "doc", "docx", "md", "txt" -> ResourceType.DOCUMENT;
            default -> ResourceType.OTHER;
        };
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String objectKey = "courses/" + courseId + "/resources/" + UUID.randomUUID();
        try (InputStream input = file.getInputStream()) {
            storage.put(objectKey, input, file.getSize(), contentType);
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE, "Resource storage is unavailable");
        }
        try {
            return courses.createResource(courseId, actorId, new CreateCourseResourceRequest(
                    chapterId, fileName, null, type, objectKey, contentType, file.getSize()));
        } catch (RuntimeException failure) {
            try { storage.delete(objectKey); } catch (IOException ignored) { /* Retained for reconciliation. */ }
            throw failure;
        }
    }

    public InputStream open(Long courseId, Long resourceId, Long actorId) {
        CourseResourceView resource = courses.getResource(courseId, resourceId, actorId);
        try {
            return storage.open(resource.objectKey());
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE, "Resource storage is unavailable");
        }
    }
}
