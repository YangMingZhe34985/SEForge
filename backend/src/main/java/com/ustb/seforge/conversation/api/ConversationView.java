package com.ustb.seforge.conversation.api;

import com.ustb.seforge.conversation.domain.Conversation;
import com.ustb.seforge.conversation.domain.ConversationStatus;
import java.time.Instant;

public record ConversationView(Long id, Long courseId, String title, ConversationStatus status,
                               Instant lastMessageAt, Instant createdAt, Instant updatedAt) {
    public static ConversationView from(Conversation value) {
        return new ConversationView(value.getId(), value.getCourseId(), value.getTitle(), value.getStatus(),
                value.getLastMessageAt(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
