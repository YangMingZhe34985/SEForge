package com.ustb.seforge.content.infrastructure;

import com.ustb.seforge.ai.application.AiGateway;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DashScopeEmbeddingProvider implements EmbeddingProvider {
    private final AiGateway runtime;

    public DashScopeEmbeddingProvider(AiGateway runtime) {
        this.runtime = runtime;
    }

    @Override
    public String version() {
        return runtime.embeddingVersion();
    }

    @Override
    public List<Embedding> embedAll(String embeddingVersion, List<TextSegment> segments) {
        return runtime.embed(embeddingVersion, segments);
    }

    @Override
    public Embedding embed(String embeddingVersion, String text) {
        return runtime.embed(embeddingVersion, List.of(TextSegment.from(text))).getFirst();
    }
}
