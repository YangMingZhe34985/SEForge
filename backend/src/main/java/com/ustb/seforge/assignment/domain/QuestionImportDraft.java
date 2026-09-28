package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.*;

/** Teacher-only transient review material, not a second formal question model. */
@Entity @Table(name="question_import_draft")
public class QuestionImportDraft extends BaseEntity {
    @Column(nullable=false) private Long assignmentId;
    @Column(nullable=false) private Long ownerId;
    @Column(nullable=false,unique=true) private Long sourceMediaId;
    @Column(nullable=false,columnDefinition="longtext") private String draftJson;
    @Column(columnDefinition="longtext") private String sourceSectionsJson;
    @Column(columnDefinition="longtext") private String confirmationJson;
    @Column(columnDefinition="longtext") private String resultJson;
    protected QuestionImportDraft() {}
    public QuestionImportDraft(Long assignmentId,Long ownerId,Long sourceMediaId,String draftJson) {
        this.assignmentId=assignmentId;this.ownerId=ownerId;this.sourceMediaId=sourceMediaId;this.draftJson=draftJson;
    }
    public Long getAssignmentId(){return assignmentId;}
    public Long getOwnerId(){return ownerId;}
    public Long getSourceMediaId(){return sourceMediaId;}
    public String getDraftJson(){return draftJson;}
    public String getSourceSectionsJson(){return sourceSectionsJson;}
    public void sourceSections(String value){sourceSectionsJson=value;}
    public String getConfirmationJson(){return confirmationJson;}
    public String getResultJson(){return resultJson;}
    public void replaceDraft(String value){draftJson=value;}
    public void confirm(String request,String result){confirmationJson=request;resultJson=result;}
}
