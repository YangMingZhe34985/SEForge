package com.ustb.seforge.assignment.service;
import com.ustb.seforge.assignment.repository.*;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
/** Authorized read of legacy submission attachments; callers cannot supply an object key. */
@Service
public class GradingAttachmentService {
    private final SubmissionRepository submissions;private final SubmissionAnswerRepository answers;private final CourseAccessService access;private final ObjectStorage storage;
    public GradingAttachmentService(SubmissionRepository submissions,SubmissionAnswerRepository answers,CourseAccessService access,ObjectStorage storage){this.submissions=submissions;this.answers=answers;this.access=access;this.storage=storage;}
    public record File(String name,byte[] bytes){}
    @Transactional(readOnly=true)
    public File read(Long submissionId,Long questionId,Long actor){
        var submission=submissions.findById(submissionId).orElseThrow(this::missing);access.requireTeachingStaff(submission.getCourseId(),actor);
        var answer=answers.findBySubmissionIdAndQuestionId(submissionId,questionId).filter(a->a.getAttachmentObjectKey()!=null).orElseThrow(this::missing);
        String key=answer.getAttachmentObjectKey();
        try(InputStream input=storage.open(key)) {byte[] bytes=input.readNBytes(50*1024*1024+1);if(bytes.length>50*1024*1024)throw new AppException(ErrorCode.CONFLICT,"Attachment exceeds safe download limit");return new File(key.substring(key.lastIndexOf('/')+1),bytes);}
        catch(IOException e){throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE,"Submission attachment storage unavailable");}
    }
    private AppException missing(){return new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Attachment not found");}
}
