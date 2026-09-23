package com.ustb.seforge.content.infrastructure;

import com.ustb.seforge.ai.application.AiUnavailableException;
import com.ustb.seforge.config.SEForgeProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DashScopeEmbeddingProvider implements EmbeddingProvider {
    private final SEForgeProperties.Ai properties;
    private final ConcurrentMap<String, EmbeddingModel> models = new ConcurrentHashMap<>();

    public DashScopeEmbeddingProvider(SEForgeProperties properties) {
        this.properties = properties.getAi();
    }

    @Override
    public String version() {
        return properties.getEmbeddingModel();
    }

    @Override
    public List<Embedding> embedAll(String embeddingVersion, List<TextSegment> segments) {
        return model(embeddingVersion).embedAll(segments).content();
    }

    @Override
    public Embedding embed(String embeddingVersion, String text) {
        return model(embeddingVersion).embed(text).content();
    }

    private EmbeddingModel model(String embeddingVersion) {
        if (!properties.isEnabled() || properties.getDashscopeApiKey().isBlank()) {
            throw new AiUnavailableException("DashScope embedding is not configured");
        }
        if (embeddingVersion == null || embeddingVersion.isBlank()) {
            throw new IllegalArgumentException("embeddingVersion is required");
        }
        return models.computeIfAbsent(embeddingVersion, version -> OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getDashscopeBaseUrl())
                .apiKey(properties.getDashscopeApiKey())
                .modelName(version)
                .dimensions(properties.getEmbeddingDimension())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .maxSegmentsPerBatch(20)
                .logRequests(false).logResponses(false).build());
    }
}
