package com.ustb.seforge.ai.infrastructure;

import com.ustb.seforge.ai.application.AiUnavailableException;
import com.ustb.seforge.ai.application.ModelCapability;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.AiServices;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class AiServiceFactory {
    private final ModelRegistry registry;

    public AiServiceFactory(ModelRegistry registry) {
        this.registry = registry;
    }

    public <T> T create(Class<T> serviceType, ModelCapability capability,
                        ChatMemoryProvider memoryProvider, Object... authorizedTools) {
        AiModelEndpoint endpoint = registry.candidates(capability).stream().findFirst()
                .orElseThrow(() -> new AiUnavailableException("No model configured for " + capability));
        return create(serviceType, endpoint, memoryProvider, authorizedTools);
    }

    public <T> T create(Class<T> serviceType, AiModelEndpoint endpoint,
                        ChatMemoryProvider memoryProvider, Object... authorizedTools) {
        AiServices<T> builder = AiServices.builder(serviceType)
                .chatModel(endpoint.chatModel())
                .streamingChatModel(endpoint.streamingChatModel());
        if (memoryProvider != null) builder.chatMemoryProvider(memoryProvider);
        if (authorizedTools != null && authorizedTools.length > 0) {
            if (authorizedTools.length == 1 && authorizedTools[0] instanceof
                    com.ustb.seforge.ai.tool.AuthorizedToolRuntime.Session session) {
                builder.tools(session.executors());
            } else {
                builder.tools(Arrays.asList(authorizedTools));
            }
            builder.maxSequentialToolsInvocations(8);
        }
        return builder.build();
    }
}
