package com.ustb.seforge.ai.infrastructure;

import com.ustb.seforge.ai.application.ModelCapability;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModelRouter {
    private final ModelRegistry registry;

    public ModelRouter(ModelRegistry registry) {
        this.registry = registry;
    }

    public List<AiModelEndpoint> candidates(ModelCapability capability) {
        return registry.candidates(capability);
    }

    public String embeddingVersion() { return registry.embeddingVersion(); }
    public int streamingRetries() { return registry.streamingRetries(); }
    public java.time.Duration timeout() { return registry.timeout(); }

    public List<AiEmbeddingEndpoint> embeddingCandidates(ModelCapability capability, String version) {
        if (capability != ModelCapability.EMBEDDING) throw new IllegalArgumentException("EMBEDDING required");
        return registry.embeddingCandidates(version);
    }
}
