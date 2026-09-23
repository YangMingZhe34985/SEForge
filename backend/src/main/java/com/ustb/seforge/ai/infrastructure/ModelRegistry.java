package com.ustb.seforge.ai.infrastructure;

import com.ustb.seforge.ai.application.ModelCapability;
import com.ustb.seforge.config.SEForgeProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ModelRegistry {
    private final Map<ModelCapability, List<AiModelEndpoint>> models = new EnumMap<>(ModelCapability.class);

    public ModelRegistry(SEForgeProperties properties) {
        SEForgeProperties.Ai ai = properties.getAi();
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
        return !candidates(capability).isEmpty();
    }

    private void register(ModelCapability capability, AiModelEndpoint endpoint) {
        models.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(endpoint);
    }

    private static AiModelEndpoint endpoint(String provider, String model, String baseUrl, String apiKey,
                                            SEForgeProperties.Ai settings) {
        OpenAiChatModel chat = OpenAiChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(model)
                .timeout(settings.getTimeout()).maxRetries(settings.getMaxRetries())
                .logRequests(false).logResponses(false).returnThinking(false).build();
        OpenAiStreamingChatModel streaming = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(model)
                .timeout(settings.getTimeout()).logRequests(false).logResponses(false)
                .returnThinking(false).build();
        return new AiModelEndpoint(provider, model, chat, streaming);
    }
}
