package com.ustb.seforge.conversation.repository;

import com.ustb.seforge.conversation.domain.ConversationGeneration;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface ConversationGenerationRepository extends JpaRepository<ConversationGeneration, Long> {
    Optional<ConversationGeneration> findFirstByConversationIdOrderByIdDesc(Long conversationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ConversationGeneration> findTopByConversationIdOrderByIdDesc(Long conversationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ConversationGeneration> findByConversationIdAndRequestId(Long conversationId, String requestId);
    @Query("select g.id from ConversationGeneration g where g.status = :status and g.deadlineAt < :now")
    List<Long> expiredIds(ConversationGeneration.Status status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ConversationGeneration g where g.id = :id")
    Optional<ConversationGeneration> lockById(Long id);
}
