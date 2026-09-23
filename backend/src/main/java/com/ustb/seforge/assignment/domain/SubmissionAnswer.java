package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "submission_answer")
public class SubmissionAnswer extends BaseEntity {
    @Column(name = "submission_id", nullable = false)
    private Long submissionId;
    @Column(name = "question_id", nullable = false)
    private Long questionId;
    @Column(name = "answer_text", columnDefinition = "longtext")
    private String answerText;
    @Column(name = "answer_data", columnDefinition = "json")
    private String answerData;
    @Column(name = "attachment_object_key", length = 512)
    private String attachmentObjectKey;
    @Column(name = "attachment_size_bytes", nullable = false)
    private long attachmentSizeBytes;

    protected SubmissionAnswer() {
    }

    public SubmissionAnswer(Long submissionId, Long questionId, String answerText,
                            String answerDataJson, String attachmentObjectKey) {
        this.submissionId = submissionId;
        this.questionId = questionId;
        this.answerText = answerText;
        this.answerData = answerDataJson;
        this.attachmentObjectKey = attachmentObjectKey;
    }

    public void update(String answerText, String answerDataJson, String attachmentObjectKey) {
        this.answerText = answerText;
        this.answerData = answerDataJson;
        this.attachmentObjectKey = attachmentObjectKey;
    }

    public void update(String answerText, String answerDataJson, String attachmentObjectKey,
                       long attachmentSizeBytes) {
        this.answerText = answerText;
        this.answerData = answerDataJson;
        this.attachmentObjectKey = attachmentObjectKey;
        this.attachmentSizeBytes = Math.max(attachmentSizeBytes, 0);
    }

    public Long getSubmissionId() { return submissionId; }
    public Long getQuestionId() { return questionId; }
    public String getAnswerText() { return answerText; }
    public String getAnswerDataJson() { return answerData; }
    public String getAttachmentObjectKey() { return attachmentObjectKey; }
    public long getAttachmentSizeBytes() { return attachmentSizeBytes; }
}
