package com.ustb.seforge.conversation.repository;

import com.ustb.seforge.conversation.domain.ConversationMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    Page<ConversationMessage> findAllByConversationIdOrderByIdAsc(Long conversationId, Pageable pageable);
    List<ConversationMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long id);
    Optional<ConversationMessage> findByConversationIdAndClientRequestId(Long conversationId, String requestId);
    Optional<ConversationMessage> findByIdAndConversationId(Long id, Long conversationId);
}
