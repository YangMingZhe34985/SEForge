package com.ustb.seforge.content.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "knowledge_document", indexes = {
        @Index(name = "idx_knowledge_document_course_status", columnList = "course_id,status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_knowledge_document_object", columnNames = "object_key"),
        @UniqueConstraint(name = "uk_knowledge_document_checksum", columnNames = {"course_id", "checksum"})
})
public class KnowledgeDocument extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "media_type", nullable = false, length = 150)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DocumentStatus status;

    @Column(name = "parser_version", nullable = false, length = 64)
    private String parserVersion;

    @Column(name = "embedding_version", nullable = false, length = 64)
    private String embeddingVersion;

    @Column(name = "chunking_version", nullable = false, length = 64)
    private String chunkingVersion;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(Long courseId, Long chapterId, Long uploaderId, String originalName,
                             String objectKey, String mediaType, long sizeBytes, String checksum,
                             String parserVersion, String embeddingVersion, String chunkingVersion) {
        this.courseId = courseId;
        this.chapterId = chapterId;
        this.uploaderId = uploaderId;
        this.originalName = originalName;
        this.objectKey = objectKey;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.parserVersion = parserVersion;
        this.embeddingVersion = embeddingVersion;
        this.chunkingVersion = chunkingVersion;
        status = DocumentStatus.UPLOADED;
    }

    public void replaceUpload(Long chapterId, Long uploaderId, String originalName, String objectKey,
                              String mediaType, long sizeBytes, String parserVersion,
                              String embeddingVersion, String chunkingVersion) {
        this.chapterId = chapterId;
        this.uploaderId = uploaderId;
        this.originalName = originalName;
        this.objectKey = objectKey;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.parserVersion = parserVersion;
        this.embeddingVersion = embeddingVersion;
        this.chunkingVersion = chunkingVersion;
        this.status = DocumentStatus.UPLOADED;
        this.lastError = null;
        this.ingestedAt = null;
    }

    public void queued() { status = DocumentStatus.QUEUED; lastError = null; }
    public void processing() { status = DocumentStatus.PROCESSING; lastError = null; }
    public void ready() { status = DocumentStatus.READY; ingestedAt = Instant.now(); lastError = null; }
    public void reindex(String targetEmbeddingVersion) {
        lastError = null;
        if (ingestedAt == null) {
            embeddingVersion = targetEmbeddingVersion;
            status = DocumentStatus.UPLOADED;
        }
    }
    public void beginIngestion() {
        if (ingestedAt == null) processing();
    }
    public void indexed(String targetEmbeddingVersion) {
        embeddingVersion = targetEmbeddingVersion;
        ready();
    }
    public void ingestionFailed(Throwable error) {
        if (status == DocumentStatus.CANCELLED) return;
        if (ingestedAt == null) {
            failed(error);
            return;
        }
        status = DocumentStatus.READY;
        setError(error);
    }
    public void ingestionCancelled() {
        if (ingestedAt == null) {
            status = DocumentStatus.CANCELLED;
            lastError = "Ingestion was cancelled";
        } else {
            status = DocumentStatus.READY;
            lastError = "Reindex was cancelled; the previous index remains active";
        }
    }
    public void deleting() { status = DocumentStatus.DELETING; }
    public void deleted() { status = DocumentStatus.DELETED; }
    public void failed(Throwable error) {
        status = DocumentStatus.FAILED;
        setError(error);
    }
    private void setError(Throwable error) {
        String message = error == null || error.getMessage() == null ? "Ingestion failed" : error.getMessage();
        lastError = message.substring(0, Math.min(message.length(), 4000));
    }

    public Long getCourseId() { return courseId; }
    public Long getResourceId() { return resourceId; }
    public Long getChapterId() { return chapterId; }
    public Long getUploaderId() { return uploaderId; }
    public String getOriginalName() { return originalName; }
    public String getObjectKey() { return objectKey; }
    public String getMediaType() { return mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksum() { return checksum; }
    public DocumentStatus getStatus() { return status; }
    public String getParserVersion() { return parserVersion; }
    public String getEmbeddingVersion() { return embeddingVersion; }
    public String getChunkingVersion() { return chunkingVersion; }
    public String getLastError() { return lastError; }
    public Instant getIngestedAt() { return ingestedAt; }
}
