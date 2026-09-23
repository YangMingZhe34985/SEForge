package com.ustb.seforge.ai.infrastructure;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public record AiModelEndpoint(String provider, String model, ChatModel chatModel,
                              StreamingChatModel streamingChatModel) {
}
