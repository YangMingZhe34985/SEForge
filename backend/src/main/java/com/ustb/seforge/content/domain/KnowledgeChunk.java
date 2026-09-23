package com.ustb.seforge.content.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "knowledge_chunk", indexes = @Index(
        name = "idx_knowledge_chunk_course_version", columnList = "course_id,embedding_version"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_knowledge_chunk_vector", columnNames = "vector_id"),
                @UniqueConstraint(name = "uk_knowledge_chunk_version",
                        columnNames = {"document_id", "chunk_index", "embedding_version"})
        })
public class KnowledgeChunk extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "longtext")
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "page_no")
    private Integer page;

    @Column(name = "section_name", length = 255)
    private String section;

    @Column(nullable = false, length = 512)
    private String source;

    @Column(name = "parser_version", nullable = false, length = 64)
    private String parserVersion;

    @Column(name = "embedding_version", nullable = false, length = 64)
    private String embeddingVersion;

    @Column(name = "vector_id", nullable = false, length = 190)
    private String vectorId;

    @Column(columnDefinition = "json")
    private String metadata;

    protected KnowledgeChunk() {
    }

    public KnowledgeChunk(Long courseId, Long documentId, Long chapterId, int chunkIndex,
                          String content, int tokenCount, Integer page, String section,
                          String source, String parserVersion, String embeddingVersion,
                          String vectorId, String metadata) {
        this.courseId = courseId;
        this.documentId = documentId;
        this.chapterId = chapterId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.page = page;
        this.section = section;
        this.source = source;
        this.parserVersion = parserVersion;
        this.embeddingVersion = embeddingVersion;
        this.vectorId = vectorId;
        this.metadata = metadata;
    }

    public Long getCourseId() { return courseId; }
    public Long getDocumentId() { return documentId; }
    public Long getChapterId() { return chapterId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public int getTokenCount() { return tokenCount; }
    public Integer getPage() { return page; }
    public String getSection() { return section; }
    public String getSource() { return source; }
    public String getParserVersion() { return parserVersion; }
    public String getEmbeddingVersion() { return embeddingVersion; }
    public String getVectorId() { return vectorId; }
    public String getMetadata() { return metadata; }
}
