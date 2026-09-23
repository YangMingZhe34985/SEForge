package com.ustb.seforge.job.repository;

import com.ustb.seforge.job.domain.AsyncJob;
import com.ustb.seforge.job.domain.JobKind;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsyncJobRepository extends JpaRepository<AsyncJob, Long> {
    Optional<AsyncJob> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    Optional<AsyncJob> findByIdAndCourseId(Long id, Long courseId);

    Optional<AsyncJob> findByOwnerUserIdAndKindAndIdempotencyKey(
            Long ownerUserId, JobKind kind, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AsyncJob j where j.id = :id")
    Optional<AsyncJob> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j from AsyncJob j
            where (j.status = com.ustb.seforge.job.domain.JobStatus.RUNNING
                   and j.leaseExpiresAt <= :now)
               or (j.status = com.ustb.seforge.job.domain.JobStatus.QUEUED
                   and j.updatedAt <= :queuedBefore)
            order by j.id
            """)
    List<AsyncJob> findRecoverable(@Param("now") Instant now,
                                   @Param("queuedBefore") Instant queuedBefore,
                                   Pageable pageable);
}
