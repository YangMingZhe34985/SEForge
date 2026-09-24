package com.ustb.seforge.common.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;

@Service
public class AuditQueryService {
    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> list(int page, int size) {
        return list(page, size, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> list(int page, int size, String action, String outcome, Long actorId,
                                           Instant fromTime, Instant toTime) {
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Audit start time must precede end time");
        }
        Page<Object[]> result = repository.findEntries(blankToNull(action), blankToNull(outcome), actorId,
                fromTime, toTime, PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<AuditLogView> items = result.getContent().stream()
                .map(row -> new AuditLogView(
                        (Long) row[0], (Long) row[1], (String) row[2], (Long) row[3], (String) row[4],
                        (String) row[5], (Long) row[6], (String) row[7], (String) row[8], (Instant) row[9]))
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
