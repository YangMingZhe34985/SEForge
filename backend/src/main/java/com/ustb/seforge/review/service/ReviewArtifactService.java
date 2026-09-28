package com.ustb.seforge.review.service;
import com.ustb.seforge.review.domain.ReviewArtifact;
import com.ustb.seforge.review.repository.ReviewArtifactRepository;
import com.ustb.seforge.assignment.service.AssignmentMediaService;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
@Service
public class ReviewArtifactService {
    private final ReviewArtifactRepository artifacts; private final ObjectStorage storage;
    private final CourseAccessService access; private final CourseRepository courses; private final UserRepository users; private final SEForgeProperties properties;
    public ReviewArtifactService(ReviewArtifactRepository artifacts,ObjectStorage storage,CourseAccessService access,CourseRepository courses,UserRepository users,SEForgeProperties properties){
        this.artifacts=artifacts;this.storage=storage;this.access=access;this.courses=courses;this.users=users;this.properties=properties;
    }
    @Transactional
    public ReviewArtifact upload(Long course,Long actor,MultipartFile file){
        access.requireTeachingStaff(course,actor);
        String original=file.getOriginalFilename()==null?"":file.getOriginalFilename();
        if(!original.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|docx|md|txt)$"))throw new AppException(ErrorCode.MALFORMED_REQUEST,"Review artifact supports PDF / DOCX / MD / TXT");
        byte[] bytes;try{bytes=AssignmentMediaService.validate(file);}catch(IOException e){throw new AppException(ErrorCode.MALFORMED_REQUEST,"Cannot read document");}
        users.findByIdForUpdate(actor).orElseThrow();courses.findForUpdate(course).orElseThrow();
        if(artifacts.bytesByOwner(actor)+bytes.length>properties.getStorage().getAttachmentUserQuotaBytes()||artifacts.bytesByCourse(course)+bytes.length>properties.getStorage().getAttachmentCourseQuotaBytes())
            throw new AppException(ErrorCode.CONFLICT,"Review artifact storage quota exceeded");
        String name=original.replaceAll("[^\\p{L}\\p{N}._-]","_");if(name.length()>180)name=name.substring(name.length()-180);
        String key="courses/"+course+"/review-artifacts/"+UUID.randomUUID()+"/"+name;
        try{storage.put(key,new ByteArrayInputStream(bytes),bytes.length,file.getContentType());}catch(IOException e){throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE,"Review artifact storage unavailable");}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(key);}catch(Exception ignored){}}});
        return artifacts.save(new ReviewArtifact(course,actor,key,name,file.getContentType(),bytes.length));
    }
    public ReviewArtifact require(Long course,Long id){return artifacts.findByIdAndCourseId(id,course).orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Review artifact not found"));}
}
