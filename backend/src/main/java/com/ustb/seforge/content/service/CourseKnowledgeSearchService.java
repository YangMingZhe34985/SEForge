package com.ustb.seforge.content.service;

import com.ustb.seforge.content.domain.DocumentStatus;
import com.ustb.seforge.content.domain.KnowledgeDocument;
import com.ustb.seforge.content.infrastructure.EmbeddingProvider;
import com.ustb.seforge.content.infrastructure.VectorHit;
import com.ustb.seforge.content.infrastructure.VectorIndex;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import com.ustb.seforge.content.repository.KnowledgeChunkRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseKnowledgeSearchService {
    private final EmbeddingProvider embeddings;
    private final VectorIndex vectors;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final VectorIndexVersionPolicy versions;

    public CourseKnowledgeSearchService(EmbeddingProvider embeddings, VectorIndex vectors,
                                        KnowledgeDocumentRepository documents,
                                        KnowledgeChunkRepository chunks,
                                        VectorIndexVersionPolicy versions) {
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.documents = documents;
        this.chunks = chunks;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEvidence> search(Long courseId, String query, int limit) {
        String activeVersion = versions.activeVersion();
        List<VectorHit> hits = vectors.search(
                activeVersion, courseId, embeddings.embed(activeVersion, query), limit, 0.60);
        List<KnowledgeEvidence> evidence = new ArrayList<>();
        for (VectorHit hit : hits) {
            Map<String, Object> metadata = hit.metadata();
            if (!courseId.equals(number(metadata.get("courseId")))
                    || !activeVersion.equals(String.valueOf(metadata.get("embeddingVersion")))) continue;
            Long documentId = number(metadata.get("documentId"));
            KnowledgeDocument document = documents.findByIdAndCourseId(documentId, courseId).orElse(null);
            if (document == null || document.getStatus() != DocumentStatus.READY) continue;
            var chunk = chunks.findByVectorIdAndCourseIdAndEmbeddingVersion(
                    hit.vectorId(), courseId, activeVersion).orElse(null);
            if (chunk == null || !documentId.equals(chunk.getDocumentId())) continue;
            // Vector metadata narrows the candidate; durable rows are authoritative
            // for cited text and location (including after reindex publication).
            evidence.add(new KnowledgeEvidence(hit.vectorId(), chunk.getId(), documentId, chunk.getChapterId(),
                    chunk.getSource(), chunk.getPage(), chunk.getSection(), chunk.getContent(), hit.score()));
        }
        return evidence;
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(value.toString());
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : value == null ? null : Integer.valueOf(value.toString());
    }
}
