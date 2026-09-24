package com.ustb.seforge.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Newest-first projection for the administrator audit trail. The ad-hoc join enriches
     * entries with the actor's username without adding a mapped association to the entity.
     */
    @Query("""
            select a.id, a.actorId, u.username, a.courseId, a.action, a.targetType, a.targetId,
                   a.outcome, a.traceId, a.occurredAt
            from AuditLog a left join User u on u.id = a.actorId
            order by a.occurredAt desc, a.id desc
            """)
    Page<Object[]> findEntries(Pageable pageable);
}
