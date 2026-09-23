package com.ustb.seforge.common.audit;

import com.ustb.seforge.common.web.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String REJECTED = "REJECTED";

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            Long actorId, Long courseId, String action, String targetType, Long targetId, String outcome) {
        AuditLog entry = new AuditLog(
                actorId, courseId, action, targetType, targetId, outcome, TraceContext.getOrCreate());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // JpaRepository.save joins the surrounding transaction. A rollback therefore removes
            // the success audit together with the business mutation.
            repository.save(entry);
            return;
        }
        try {
            // Controllers often record after a service transaction has already committed. At that
            // point an unavailable audit store must not turn a successful command into an HTTP 500.
            repository.save(entry);
        } catch (RuntimeException failure) {
            log.error("Could not persist audit event action={} targetType={} targetId={}",
                    action, targetType, targetId, failure);
        }
    }
}
