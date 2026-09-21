package com.ustb.smartse.modules.agent.config;

import com.ustb.smartse.modules.agent.toolservice.GeneralToolService;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Service;

@Service
public class GeneralAssistantImpl implements AgentConfig.GeneralAssistant {

    private final AgentConfig.GeneralAssistant delegate;
    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemoryStore chatMemoryStore; // 添加持久化存储字段

    public GeneralAssistantImpl(
            ChatLanguageModel deepseekChatModel,
            StreamingChatLanguageModel deepseekStreamingChatModel,
            GeneralToolService generalToolService,
            ChatMemoryStore chatMemoryStore) {
        this.chatMemoryStore = chatMemoryStore; // 存储注入的持久化存储

        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(30)
                .chatMemoryStore(chatMemoryStore)
                .build();

        this.delegate = AiServices.builder(AgentConfig.GeneralAssistant.class)
                .chatLanguageModel(deepseekChatModel)
                .streamingChatLanguageModel(deepseekStreamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(generalToolService)
                .build();

        this.chatLanguageModel = deepseekChatModel;
    }

    @Override
    public String chat(Object memoryId, String userMessage) {
        return delegate.chat(memoryId, userMessage);
    }

    @Override
    public TokenStream stream(Object memoryId, String userMessage) {
        return delegate.stream(memoryId, userMessage);
    }

    @Override
    public String analyzeWithoutMemory(String message) {
        // 直接使用模型生成响应，不经过记忆系统
        return chatLanguageModel.chat(message);
    }
}
