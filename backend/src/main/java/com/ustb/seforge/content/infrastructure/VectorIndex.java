package com.ustb.seforge.content.infrastructure;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface VectorIndex {
    void addAll(String embeddingVersion, Long courseId, List<String> ids, List<Embedding> embeddings,
                List<TextSegment> segments);

    List<VectorHit> search(String embeddingVersion, Long courseId, Embedding query,
                           int limit, double minimumScore);

    void deleteDocument(String embeddingVersion, Long courseId, Long documentId);

    Set<String> listIds(String embeddingVersion, Long courseId);

    void deleteIds(String embeddingVersion, Long courseId, Collection<String> ids);

    String collectionName(String embeddingVersion);
}
