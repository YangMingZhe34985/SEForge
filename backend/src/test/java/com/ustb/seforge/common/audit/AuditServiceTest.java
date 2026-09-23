package com.ustb.seforge.common.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AuditServiceTest {
    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void failureAfterBusinessTransactionCommittedDoesNotEscape() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any(AuditLog.class))).thenThrow(new IllegalStateException("audit unavailable"));
        AuditService service = new AuditService(repository);

        assertThatCode(() -> service.record(1L, 2L, "COURSE_UPDATE", "COURSE", 2L,
                AuditService.SUCCEEDED)).doesNotThrowAnyException();

        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void auditFailureInsideBusinessTransactionPropagatesSoBusinessCanRollBack() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any(AuditLog.class))).thenThrow(new IllegalStateException("audit unavailable"));
        AuditService service = new AuditService(repository);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.record(1L, 2L, "REVIEW_REQUEST", "REVIEW", 3L,
                AuditService.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }
}
