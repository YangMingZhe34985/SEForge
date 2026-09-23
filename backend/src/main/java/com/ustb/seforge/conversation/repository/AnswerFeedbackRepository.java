package com.ustb.seforge.conversation.repository;

import com.ustb.seforge.conversation.domain.AnswerFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, Long> {
    Optional<AnswerFeedback> findByMessageIdAndUserId(Long messageId, Long userId);
}
