package com.ustb.seforge.review.domain;
import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.*;
@Entity @Table(name="review_artifact")
public class ReviewArtifact extends BaseEntity {
    @Column(name="course_id",nullable=false) private Long courseId;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(name="object_key",nullable=false,length=512) private String objectKey;
    @Column(name="file_name",nullable=false) private String fileName;
    @Column(name="media_type",nullable=false,length=128) private String mediaType;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
    protected ReviewArtifact() {}
    public ReviewArtifact(Long course,Long owner,String key,String name,String type,long size){courseId=course;ownerId=owner;objectKey=key;fileName=name;mediaType=type;sizeBytes=size;}
    public Long getCourseId(){return courseId;} public Long getOwnerId(){return ownerId;}
    public String getObjectKey(){return objectKey;} public String getFileName(){return fileName;}
    public String getMediaType(){return mediaType;} public long getSizeBytes(){return sizeBytes;}
}
