package com.ustb.seforge.content.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ingestion_job")
public class IngestionJob extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "document_id", nullable = false)
    private Long documentId;
    @Column(name = "async_job_id", unique = true)
    private Long asyncJobId;
    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IngestionStatus status;
    @Column(name = "parser_version", nullable = false, length = 64)
    private String parserVersion;
    @Column(name = "embedding_version", nullable = false, length = 64)
    private String embeddingVersion;
    @Column(name = "attempt_count", nullable = false)
    private int attempts;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected IngestionJob() {
    }

    public IngestionJob(Long courseId, Long documentId, Long asyncJobId, Long requestedBy,
                        String parserVersion, String embeddingVersion) {
        this.courseId = courseId;
        this.documentId = documentId;
        this.asyncJobId = asyncJobId;
        this.requestedBy = requestedBy;
        this.parserVersion = parserVersion;
        this.embeddingVersion = embeddingVersion;
        status = IngestionStatus.QUEUED;
    }

    public void processing() { status = IngestionStatus.PROCESSING; attempts++; startedAt = Instant.now(); }
    public void completed() { status = IngestionStatus.COMPLETED; completedAt = Instant.now(); }
    public void cancelled() {
        if (status == IngestionStatus.COMPLETED) return;
        status = IngestionStatus.CANCELLED;
        completedAt = Instant.now();
        errorCode = "CANCELLED";
        errorMessage = "Ingestion was cancelled";
    }
    public void failed(Throwable error) {
        if (status == IngestionStatus.CANCELLED) return;
        status = IngestionStatus.FAILED;
        completedAt = Instant.now();
        errorCode = error.getClass().getSimpleName();
        String message = error.getMessage() == null ? "Ingestion failed" : error.getMessage();
        errorMessage = message.substring(0, Math.min(message.length(), 4000));
    }

    public Long getDocumentId() { return documentId; }
    public Long getAsyncJobId() { return asyncJobId; }
    public Long getCourseId() { return courseId; }
    public String getParserVersion() { return parserVersion; }
    public String getEmbeddingVersion() { return embeddingVersion; }
    public IngestionStatus getStatus() { return status; }
}
