package com.ustb.seforge.job.repository;

import com.ustb.seforge.job.domain.OutboxEvent;
import com.ustb.seforge.job.domain.OutboxStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findTop50ByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
            OutboxStatus status, Instant availableAt);

    boolean existsByAggregateTypeAndAggregateIdAndEventTypeAndStatus(
            String aggregateType, Long aggregateId, String eventType, OutboxStatus status);
}
