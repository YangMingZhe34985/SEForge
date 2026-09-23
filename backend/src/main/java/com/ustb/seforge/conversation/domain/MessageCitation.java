package com.ustb.seforge.conversation.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(name = "message_citation", uniqueConstraints = @UniqueConstraint(
        name = "uk_message_citation_order", columnNames = {"message_id", "ordinal"}))
public class MessageCitation extends BaseEntity {
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "document_id", nullable = false)
    private Long documentId;
    @Column(name = "chunk_id", nullable = false)
    private Long chunkId;
    @Column(nullable = false)
    private int ordinal;
    @Column(nullable = false, length = 255)
    private String label;
    @Column(name = "source_title", nullable = false, length = 255)
    private String sourceTitle;
    @Column(name = "page_number")
    private Integer page;
    @Column(length = 255)
    private String chapter;
    @Column(length = 255)
    private String section;
    @Column(name = "quote_text", columnDefinition = "text")
    private String quote;
    @Column(name = "relevance_score", precision = 8, scale = 6)
    private BigDecimal relevanceScore;

    protected MessageCitation() {
    }

    public MessageCitation(Long messageId, Long courseId, Long documentId, Long chunkId, int ordinal,
                           String label, String sourceTitle, Integer page, String chapter,
                           String section, String quote, Double relevanceScore) {
        this.messageId = messageId;
        this.courseId = courseId;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.ordinal = ordinal;
        this.label = label;
        this.sourceTitle = sourceTitle;
        this.page = page;
        this.chapter = chapter;
        this.section = section;
        this.quote = quote;
        this.relevanceScore = relevanceScore == null ? null : BigDecimal.valueOf(relevanceScore);
    }

    public Long getMessageId() { return messageId; }
    public Long getDocumentId() { return documentId; }
    public Long getChunkId() { return chunkId; }
    public int getOrdinal() { return ordinal; }
    public String getLabel() { return label; }
    public String getSourceTitle() { return sourceTitle; }
    public Integer getPage() { return page; }
    public String getChapter() { return chapter; }
    public String getSection() { return section; }
    public String getQuote() { return quote; }
    public Double getRelevanceScore() { return relevanceScore == null ? null : relevanceScore.doubleValue(); }
}
