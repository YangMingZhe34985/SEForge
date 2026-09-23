package com.ustb.seforge.content.support;

import com.ustb.seforge.content.infrastructure.VectorHit;
import com.ustb.seforge.content.infrastructure.VectorIndex;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryVectorIndex implements VectorIndex {
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void addAll(String embeddingVersion, Long courseId, List<String> ids, List<Embedding> embeddings,
                       List<TextSegment> segments) {
        if (courseId == null) throw new IllegalArgumentException("courseId filter is mandatory");
        for (int index = 0; index < ids.size(); index++) {
            TextSegment segment = segments.get(index);
            String vectorId = ids.get(index);
            if (!embeddingVersion.equals(segment.metadata().getString("embeddingVersion"))) {
                throw new IllegalArgumentException("embeddingVersion metadata mismatch");
            }
            if (!courseId.equals(segment.metadata().getLong("courseId"))) {
                throw new IllegalArgumentException("courseId metadata mismatch");
            }
            entries.removeIf(entry -> embeddingVersion.equals(entry.embeddingVersion)
                    && vectorId.equals(entry.id));
            entries.add(new Entry(embeddingVersion, vectorId, embeddings.get(index), segment));
        }
    }

    @Override
    public List<VectorHit> search(String embeddingVersion, Long courseId, Embedding query,
                                  int limit, double minimumScore) {
        if (courseId == null) throw new IllegalArgumentException("courseId filter is mandatory");
        List<VectorHit> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (!embeddingVersion.equals(entry.embeddingVersion)) continue;
            if (!courseId.equals(entry.segment.metadata().getLong("courseId"))) continue;
            double score = cosine(query.vector(), entry.embedding.vector());
            if (score >= minimumScore) result.add(new VectorHit(entry.id, score, entry.segment.text(),
                    entry.segment.metadata().toMap()));
        }
        return result.stream().sorted(Comparator.comparingDouble(VectorHit::score).reversed())
                .limit(limit).toList();
    }

    @Override
    public void deleteDocument(String embeddingVersion, Long courseId, Long documentId) {
        entries.removeIf(entry -> embeddingVersion.equals(entry.embeddingVersion)
                && courseId.equals(entry.segment.metadata().getLong("courseId"))
                && documentId.equals(entry.segment.metadata().getLong("documentId")));
    }

    @Override
    public Set<String> listIds(String embeddingVersion, Long courseId) {
        if (courseId == null) throw new IllegalArgumentException("courseId filter is mandatory");
        return entries.stream()
                .filter(entry -> embeddingVersion.equals(entry.embeddingVersion))
                .filter(entry -> courseId.equals(entry.segment.metadata().getLong("courseId")))
                .map(Entry::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void deleteIds(String embeddingVersion, Long courseId, Collection<String> ids) {
        if (courseId == null) throw new IllegalArgumentException("courseId filter is mandatory");
        entries.removeIf(entry -> embeddingVersion.equals(entry.embeddingVersion)
                && courseId.equals(entry.segment.metadata().getLong("courseId"))
                && ids.contains(entry.id));
    }

    @Override
    public String collectionName(String embeddingVersion) {
        return "memory_" + embeddingVersion;
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record Entry(String embeddingVersion, String id, Embedding embedding, TextSegment segment) {}
}
