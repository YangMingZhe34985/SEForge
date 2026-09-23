package com.ustb.seforge.content.api;

import com.ustb.seforge.content.domain.DocumentStatus;
import com.ustb.seforge.content.domain.KnowledgeDocument;
import com.ustb.seforge.job.api.AsyncJobView;
import java.time.Instant;

public record KnowledgeDocumentView(
        Long id,
        Long courseId,
        Long chapterId,
        String name,
        String mediaType,
        long sizeBytes,
        String checksum,
        DocumentStatus status,
        String parserVersion,
        String embeddingVersion,
        String chunkingVersion,
        String error,
        Instant ingestedAt,
        Instant createdAt,
        AsyncJobView job) {
    public static KnowledgeDocumentView from(KnowledgeDocument document, AsyncJobView job) {
        return new KnowledgeDocumentView(document.getId(), document.getCourseId(), document.getChapterId(),
                document.getOriginalName(), document.getMediaType(), document.getSizeBytes(),
                document.getChecksum(), document.getStatus(), document.getParserVersion(),
                document.getEmbeddingVersion(), document.getChunkingVersion(), document.getLastError(),
                document.getIngestedAt(), document.getCreatedAt(), job);
    }
}
