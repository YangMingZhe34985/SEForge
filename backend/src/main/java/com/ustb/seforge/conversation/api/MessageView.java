package com.ustb.seforge.conversation.api;

import com.ustb.seforge.conversation.domain.ConversationMessage;
import com.ustb.seforge.conversation.domain.MessageRole;
import com.ustb.seforge.conversation.domain.MessageStatus;
import java.time.Instant;
import java.util.List;

public record MessageView(Long id, MessageRole role, String content, MessageStatus status,
                          List<CitationView> citations, Instant createdAt) {
    public static MessageView from(ConversationMessage message, List<CitationView> citations) {
        return new MessageView(message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                citations, message.getCreatedAt());
    }
}
