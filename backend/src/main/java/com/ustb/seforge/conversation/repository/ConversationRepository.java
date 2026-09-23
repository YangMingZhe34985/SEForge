package com.ustb.seforge.conversation.repository;

import com.ustb.seforge.conversation.domain.Conversation;
import com.ustb.seforge.conversation.domain.ConversationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByIdAndOwnerIdAndCourseId(Long id, Long ownerId, Long courseId);
    Page<Conversation> findAllByOwnerIdAndCourseIdAndStatusOrderByUpdatedAtDesc(
            Long ownerId, Long courseId, ConversationStatus status, Pageable pageable);
}
