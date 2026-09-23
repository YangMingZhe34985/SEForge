package com.ustb.seforge.content.infrastructure;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;

public interface EmbeddingProvider {
    String version();

    List<Embedding> embedAll(String embeddingVersion, List<TextSegment> segments);

    Embedding embed(String embeddingVersion, String text);
}
