package com.ustb.seforge.content.service;

import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.infrastructure.VectorIndex;
import com.ustb.seforge.content.repository.KnowledgeChunkRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Reconciles durable chunk rows with one course-scoped, versioned vector collection. */
@Service
public class VectorIndexReconciliationService {
    private final KnowledgeChunkRepository chunks;
    private final VectorIndex vectors;
    private final VectorIndexVersionPolicy versions;
    private final SEForgeProperties properties;
    private final CourseIndexWriteService indexWrites;
    private final com.ustb.seforge.content.infrastructure.EmbeddingProvider embeddings;

    public VectorIndexReconciliationService(KnowledgeChunkRepository chunks, VectorIndex vectors,
                                            VectorIndexVersionPolicy versions,
                                            SEForgeProperties properties, CourseIndexWriteService indexWrites,
                                            com.ustb.seforge.content.infrastructure.EmbeddingProvider embeddings) {
        this.chunks = chunks;
        this.vectors = vectors;
        this.versions = versions;
        this.properties = properties;
        this.indexWrites = indexWrites;
        this.embeddings = embeddings;
    }

    public ReconciliationResult reconcile(Long courseId, String requestedVersion) {
        if (courseId == null || courseId <= 0) {
            throw new IllegalArgumentException("courseId is required for vector reconciliation");
        }
        String version = versions.requireManaged(requestedVersion);
        return indexWrites.execute(courseId, () -> reconcileLocked(courseId, version));
    }

    private ReconciliationResult reconcileLocked(Long courseId, String version) {
        Set<String> databaseIds = new HashSet<>(
                chunks.findVectorIdsByCourseIdAndEmbeddingVersion(courseId, version));
        Set<String> indexIds = new HashSet<>(vectors.listIds(version, courseId));

        List<String> orphanIds = indexIds.stream().filter(id -> !databaseIds.contains(id)).sorted().toList();
        List<String> missingIds = databaseIds.stream().filter(id -> !indexIds.contains(id)).sorted().toList();
        int batchSize = Math.max(1, Math.min(properties.getVectorStore().getReconciliationBatchSize(), 1000));
        for (int offset = 0; offset < orphanIds.size(); offset += batchSize) {
            vectors.deleteIds(version, courseId,
                    orphanIds.subList(offset, Math.min(offset + batchSize, orphanIds.size())));
        }
        int repaired = 0;
        for (int offset = 0; offset < missingIds.size(); offset += batchSize) {
            var rows = chunks.findAllByVectorIdIn(missingIds.subList(offset,
                    Math.min(offset + batchSize, missingIds.size()))).stream()
                    .filter(c -> courseId.equals(c.getCourseId()) && version.equals(c.getEmbeddingVersion())).toList();
            var segments = rows.stream().map(c -> dev.langchain4j.data.segment.TextSegment.from(c.getContent(),
                    new dev.langchain4j.data.document.Metadata().put("courseId", courseId)
                            .put("documentId", c.getDocumentId()).put("chapterId", c.getChapterId() == null ? 0L : c.getChapterId())
                            .put("page", c.getPage() == null ? 0 : c.getPage()).put("section", c.getSection() == null ? "" : c.getSection())
                            .put("source", c.getSource()).put("parserVersion", c.getParserVersion())
                            .put("embeddingVersion", version))).toList();
            if (segments.isEmpty()) continue;
            vectors.addAll(version, courseId, rows.stream().map(c -> c.getVectorId()).toList(),
                    embeddings.embedAll(version, segments), segments);
            repaired += rows.size();
        }
        return new ReconciliationResult(courseId, version, vectors.collectionName(version),
                databaseIds.size(), indexIds.size(), orphanIds.size(), missingIds, repaired);
    }

    public record ReconciliationResult(Long courseId, String embeddingVersion, String collectionName,
                                       int databaseVectors, int indexedVectors, int orphansDeleted,
                                       List<String> missingVectorIds, int repairedVectors) {
        public ReconciliationResult {
            missingVectorIds = List.copyOf(new ArrayList<>(missingVectorIds));
        }
    }
}
