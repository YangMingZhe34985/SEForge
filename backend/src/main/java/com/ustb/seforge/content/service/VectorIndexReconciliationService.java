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

    public VectorIndexReconciliationService(KnowledgeChunkRepository chunks, VectorIndex vectors,
                                            VectorIndexVersionPolicy versions,
                                            SEForgeProperties properties) {
        this.chunks = chunks;
        this.vectors = vectors;
        this.versions = versions;
        this.properties = properties;
    }

    public ReconciliationResult reconcile(Long courseId, String requestedVersion) {
        if (courseId == null || courseId <= 0) {
            throw new IllegalArgumentException("courseId is required for vector reconciliation");
        }
        String version = versions.requireManaged(requestedVersion);
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
        return new ReconciliationResult(courseId, version, vectors.collectionName(version),
                databaseIds.size(), indexIds.size(), orphanIds.size(), missingIds);
    }

    public record ReconciliationResult(Long courseId, String embeddingVersion, String collectionName,
                                       int databaseVectors, int indexedVectors, int orphansDeleted,
                                       List<String> missingVectorIds) {
        public ReconciliationResult {
            missingVectorIds = List.copyOf(new ArrayList<>(missingVectorIds));
        }
    }
}
