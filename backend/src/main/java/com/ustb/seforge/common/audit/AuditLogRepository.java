package com.ustb.seforge.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Newest-first projection for the administrator audit trail. The ad-hoc join enriches
     * entries with the actor's username without adding a mapped association to the entity.
     */
    @Query("""
            select a.id, a.actorId, u.username, a.courseId, a.action, a.targetType, a.targetId,
                   a.outcome, a.traceId, a.occurredAt
            from AuditLog a left join User u on u.id = a.actorId
            where (:action is null or a.action = :action)
              and (:outcome is null or a.outcome = :outcome)
              and (:actorId is null or a.actorId = :actorId)
              and (:fromTime is null or a.occurredAt >= :fromTime)
              and (:toTime is null or a.occurredAt < :toTime)
            order by a.occurredAt desc, a.id desc
            """)
    Page<Object[]> findEntries(@Param("action") String action, @Param("outcome") String outcome,
                               @Param("actorId") Long actorId, @Param("fromTime") Instant fromTime,
                               @Param("toTime") Instant toTime, Pageable pageable);
}
