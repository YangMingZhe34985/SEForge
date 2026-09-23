package com.ustb.seforge.conversation.repository;

import com.ustb.seforge.conversation.domain.MessageCitation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageCitationRepository extends JpaRepository<MessageCitation, Long> {
    List<MessageCitation> findAllByMessageIdOrderByOrdinalAsc(Long messageId);
}
