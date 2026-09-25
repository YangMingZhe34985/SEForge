package com.ustb.seforge.ai.infrastructure;

import com.ustb.seforge.ai.application.ModelCapability;
import com.ustb.seforge.config.SEForgeProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import com.ustb.seforge.ai.application.AiUnavailableException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ModelRegistry {
    private final Map<ModelCapability, List<AiModelEndpoint>> models = new EnumMap<>(ModelCapability.class);
    private final SEForgeProperties.Ai settings;
    private final Map<String, AiEmbeddingEndpoint> embeddings = new java.util.concurrent.ConcurrentHashMap<>();

    public ModelRegistry(SEForgeProperties properties) {
        SEForgeProperties.Ai ai = properties.getAi();
        settings = ai;
        if (!ai.isEnabled()) return;
        if (!ai.getDeepseekApiKey().isBlank()) {
            register(ModelCapability.FAST, endpoint("deepseek", ai.getFastModel(), ai.getDeepseekBaseUrl(),
                    ai.getDeepseekApiKey(), ai));
            register(ModelCapability.REASONING, endpoint("deepseek", ai.getReasoningModel(), ai.getDeepseekBaseUrl(),
                    ai.getDeepseekApiKey(), ai));
            register(ModelCapability.CODING, endpoint("deepseek", ai.getCodingModel(), ai.getDeepseekBaseUrl(),
                    ai.getDeepseekApiKey(), ai));
        }
        if (!ai.getDashscopeApiKey().isBlank()) {
            AiModelEndpoint fallback = endpoint("dashscope", ai.getFallbackModel(), ai.getDashscopeBaseUrl(),
                    ai.getDashscopeApiKey(), ai);
            register(ModelCapability.FAST, fallback);
            register(ModelCapability.REASONING, fallback);
            register(ModelCapability.CODING, fallback);
        }
    }

    public List<AiModelEndpoint> candidates(ModelCapability capability) {
        return List.copyOf(models.getOrDefault(capability, List.of()));
    }

    public boolean available(ModelCapability capability) {
        if (capability == ModelCapability.EMBEDDING) {
            return settings.isEnabled() && !settings.getDashscopeApiKey().isBlank();
        }
        return !candidates(capability).isEmpty();
    }

    public String embeddingVersion() { return settings.getEmbeddingModel(); }
    public int streamingRetries() { return Math.max(0, Math.min(settings.getMaxRetries(), 3)); }
    public java.time.Duration timeout() { return settings.getTimeout(); }

    public List<AiEmbeddingEndpoint> embeddingCandidates(String version) {
        if (!available(ModelCapability.EMBEDDING)) return List.of();
        if (version == null || !version.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("Invalid embedding version");
        }
        try {
            return List.of(embeddings.computeIfAbsent(version, name -> new AiEmbeddingEndpoint(
                    "dashscope", name, settings.getEmbeddingDimension(), OpenAiEmbeddingModel.builder()
                    .baseUrl(settings.getDashscopeBaseUrl()).apiKey(settings.getDashscopeApiKey())
                    .modelName(name).dimensions(settings.getEmbeddingDimension()).timeout(settings.getTimeout())
                    .maxRetries(settings.getMaxRetries()).maxSegmentsPerBatch(20)
                    .logRequests(false).logResponses(false).build())));
        } catch (RuntimeException failure) {
            throw new AiUnavailableException("Embedding provider configuration unavailable", failure);
        }
    }

    private void register(ModelCapability capability, AiModelEndpoint endpoint) {
        if (endpoint == null) return;
        models.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(endpoint);
    }

    private static AiModelEndpoint endpoint(String provider, String model, String baseUrl, String apiKey,
                                            SEForgeProperties.Ai settings) {
        try {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Model name required");
        OpenAiChatModel chat = OpenAiChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(model)
                .timeout(settings.getTimeout()).maxRetries(settings.getMaxRetries())
                .logRequests(false).logResponses(false).returnThinking(false).build();
        OpenAiStreamingChatModel streaming = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(model)
                .timeout(settings.getTimeout()).logRequests(false).logResponses(false)
                .returnThinking(false).build();
        return new AiModelEndpoint(provider, model, chat, streaming);
        } catch (RuntimeException invalidConfiguration) {
            // Never log exception text: SDK errors may contain credentials or URLs.
            org.slf4j.LoggerFactory.getLogger(ModelRegistry.class)
                    .warn("AI provider {} is unavailable due to invalid client configuration", provider);
            return null;
        }
    }
}
