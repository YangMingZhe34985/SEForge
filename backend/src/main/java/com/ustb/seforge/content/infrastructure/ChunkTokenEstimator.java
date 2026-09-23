package com.ustb.seforge.content.infrastructure;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChunkTokenEstimator implements TokenCountEstimator {
    private final OpenAiTokenCountEstimator delegate = new OpenAiTokenCountEstimator("gpt-4o-mini");

    @Override
    public int estimateTokenCountInText(String text) {
        return delegate.estimateTokenCountInText(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return delegate.estimateTokenCountInMessage(message);
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        return delegate.estimateTokenCountInMessages(messages);
    }

    public List<Integer> encode(String text) {
        return delegate.encode(text);
    }
}
