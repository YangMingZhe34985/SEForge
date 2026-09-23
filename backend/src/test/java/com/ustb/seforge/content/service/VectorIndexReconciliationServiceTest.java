package com.ustb.seforge.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.infrastructure.VectorIndex;
import com.ustb.seforge.content.repository.KnowledgeChunkRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VectorIndexReconciliationServiceTest {
    @Test
    void deletesOnlyCourseScopedOrphansAndReportsMissingRows() {
        KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
        VectorIndex vectors = mock(VectorIndex.class);
        VectorIndexVersionPolicy versions = mock(VectorIndexVersionPolicy.class);
        SEForgeProperties properties = new SEForgeProperties();
        properties.getVectorStore().setReconciliationBatchSize(10);
        when(versions.requireManaged("embedding-v2")).thenReturn("embedding-v2");
        when(chunks.findVectorIdsByCourseIdAndEmbeddingVersion(9L, "embedding-v2"))
                .thenReturn(List.of("kept", "missing"));
        when(vectors.listIds("embedding-v2", 9L)).thenReturn(Set.of("kept", "orphan"));
        when(vectors.collectionName("embedding-v2")).thenReturn("seforge_chunks_embedding_v2");

        var service = new VectorIndexReconciliationService(chunks, vectors, versions, properties);
        var result = service.reconcile(9L, "embedding-v2");

        verify(vectors).deleteIds("embedding-v2", 9L, List.of("orphan"));
        assertThat(result.orphansDeleted()).isEqualTo(1);
        assertThat(result.missingVectorIds()).containsExactly("missing");
        assertThat(result.collectionName()).isEqualTo("seforge_chunks_embedding_v2");
    }
}
