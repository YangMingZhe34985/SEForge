package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.*;

@Entity @Table(name = "assignment_media")
public class AssignmentMedia extends BaseEntity {
    public enum Purpose {
        QUESTION_CONTENT, REFERENCE_ANSWER, ANSWER, IMPORT,
        /** Legacy API aliases; all new rows use explicit canonical purposes. */
        CONTENT, REFERENCE;
        public Purpose canonical() { return this == CONTENT ? QUESTION_CONTENT : this == REFERENCE ? REFERENCE_ANSWER : this; }
    }
    @Column(nullable=false) private Long assignmentId;
    @Column(nullable=false) private Long courseId;
    @Column(nullable=false) private Long ownerId;
    private Long questionId;
    private Long submissionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private Purpose purpose;
    @Column(nullable=false,length=512) private String objectKey;
    @Column(nullable=false,length=180) private String fileName;
    @Column(nullable=false,length=100) private String mediaType;
    @Column(nullable=false) private long sizeBytes;
    protected AssignmentMedia() {}
    public AssignmentMedia(Long assignmentId, Long courseId, Long ownerId, Long questionId, Long submissionId,
            Purpose purpose, String key, String name, String type, long size) {
        this.assignmentId=assignmentId; this.courseId=courseId; this.ownerId=ownerId;
        this.questionId=questionId; this.submissionId=submissionId; this.purpose=purpose;
        this.objectKey=key; this.fileName=name; this.mediaType=type; this.sizeBytes=size;
    }
    public Long getAssignmentId(){return assignmentId;} public Long getCourseId(){return courseId;}
    public Long getOwnerId(){return ownerId;} public Long getQuestionId(){return questionId;}
    public Long getSubmissionId(){return submissionId;} public Purpose getPurpose(){return purpose;}
    public String getObjectKey(){return objectKey;} public String getFileName(){return fileName;}
    public String getMediaType(){return mediaType;} public long getSizeBytes(){return sizeBytes;}
}
