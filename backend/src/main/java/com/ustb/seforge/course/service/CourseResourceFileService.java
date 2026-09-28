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
    private final com.ustb.seforge.content.service.KnowledgeDocumentService knowledge;
    private final com.ustb.seforge.course.repository.CourseResourceRepository resources;
    private final com.ustb.seforge.content.repository.KnowledgeDocumentRepository documents;
    private final com.ustb.seforge.course.repository.CourseRepository courseRows;
    private final com.ustb.seforge.config.SEForgeProperties properties;

    public CourseResourceFileService(CourseService courses, CourseAccessService access, ObjectStorage storage,
            com.ustb.seforge.content.service.KnowledgeDocumentService knowledge,
            com.ustb.seforge.course.repository.CourseResourceRepository resources,
            com.ustb.seforge.content.repository.KnowledgeDocumentRepository documents,
            com.ustb.seforge.course.repository.CourseRepository courseRows,
            com.ustb.seforge.config.SEForgeProperties properties) {
        this.courses = courses;
        this.access = access;
        this.storage = storage;
        this.knowledge=knowledge;this.resources=resources;this.documents=documents;this.courseRows=courseRows;this.properties=properties;
    }

    @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseResourceView upload(Long courseId, Long chapterId, Long actorId, MultipartFile file) {
        return upload(courseId,chapterId,actorId,file,false);
    }

    @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseResourceView upload(Long courseId, Long chapterId, Long actorId, MultipartFile file, boolean include) {
        access.requireTeachingStaff(courseId, actorId);
        courseRows.findForUpdate(courseId).orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Course not found"));
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
        if(!java.util.Set.of("pdf","ppt","pptx","docx","md","txt","png","jpg","jpeg","zip","csv","xls","xlsx","json","java","py","js","ts","c","cpp","h","sql").contains(extension))
            throw new AppException(ErrorCode.VALIDATION_FAILED,"Unsupported teaching attachment type");
        if(include && !com.ustb.seforge.content.service.KnowledgeDocumentService.supports(fileName))
            throw new AppException(ErrorCode.VALIDATION_FAILED,"This attachment cannot be indexed");
        if(resources.sumStoredBytes(courseId)+file.getSize()>properties.getStorage().getCourseQuotaBytes())
            throw new AppException(ErrorCode.VALIDATION_FAILED,"Course storage quota exceeded");
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
            var resource=courses.createResource(courseId, actorId, new CreateCourseResourceRequest(
                    chapterId, fileName, null, type, objectKey, contentType, file.getSize()));
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization(){
                        public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(objectKey);}catch(Exception ignored){}}
                    });
            if(include)knowledge.includeResource(courseId,resource.id(),actorId);
            return resource;
        } catch (RuntimeException failure) {
            try { storage.delete(objectKey); } catch (IOException ignored) { /* Retained for reconciliation. */ }
            throw failure;
        }
    }

    public InputStream open(Long courseId, Long resourceId, Long actorId) {
        CourseResourceView resource = courses.getResource(courseId, resourceId, actorId);
        if(resource.externalUrl()!=null)throw new AppException(ErrorCode.CONFLICT,"External links are opened by the browser, never fetched by the server");
        try {
            return storage.open(resource.objectKey());
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE, "Resource storage is unavailable");
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void remove(Long course,Long id,Long actor){
        access.requireTeachingStaff(course,actor);
        courseRows.findForUpdate(course).orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Course not found"));
        var row=resources.findByIdAndCourseIdAndStatus(id,course,com.ustb.seforge.course.domain.ResourceStatus.ACTIVE)
                .orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Resource not found"));
        documents.findByResourceId(id).ifPresent(d->knowledge.delete(course,d.getId(),actor));
        if(row.getExternalUrl()==null)try{storage.delete(row.getObjectKey());}
        catch(IOException e){throw new AppException(ErrorCode.STORAGE_UNAVAILABLE,"Resource deletion failed; retry after MinIO recovers");}
        row.remove();
    }

    @org.springframework.transaction.annotation.Transactional
    public CourseResourceView update(Long course,Long id,Long actor,String name,String description){
        access.requireTeachingStaff(course,actor);
        var row=resources.findByIdAndCourseIdAndStatus(id,course,com.ustb.seforge.course.domain.ResourceStatus.ACTIVE)
                .orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Resource not found"));
        if(row.getExternalUrl()==null && !suffix(row.getName()).equals(suffix(name)))throw new AppException(ErrorCode.VALIDATION_FAILED,"Keep the file extension when renaming");
        row.updateInfo(name,description);
        return courses.getResource(course,id,actor);
    }
    private String suffix(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot).toLowerCase(Locale.ROOT);}

    @org.springframework.transaction.annotation.Transactional
    public CourseResourceView link(Long course,Long chapter,Long actor,String name,String url,String description){
        java.net.URI uri;
        try{uri=java.net.URI.create(url);}catch(RuntimeException e){throw new AppException(ErrorCode.VALIDATION_FAILED,"Invalid link");}
        if(!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
            throw new AppException(ErrorCode.VALIDATION_FAILED,"Only HTTP(S) links without credentials are allowed");
        var value=courses.createResource(course,actor,new CreateCourseResourceRequest(chapter,name,description,ResourceType.LINK,
                "courses/"+course+"/links/"+UUID.randomUUID(),null,0L));
        resources.findById(value.id()).orElseThrow().externalUrl(url);
        return courses.getResource(course,value.id(),actor);
    }
}
