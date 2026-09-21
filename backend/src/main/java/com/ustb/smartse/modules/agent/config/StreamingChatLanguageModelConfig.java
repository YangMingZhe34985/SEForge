package com.ustb.smartse.modules.agent.config;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StreamingChatLanguageModelConfig {

    @Value("${langchain4j.openai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.openai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.openai.chat-model.max-tokens:4096}")
    private Integer maxTokens;

    @Bean
    public StreamingChatLanguageModel deepseekStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .build();
    }
}
