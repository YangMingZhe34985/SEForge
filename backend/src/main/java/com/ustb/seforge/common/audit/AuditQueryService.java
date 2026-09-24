package com.ustb.seforge.common.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ustb.seforge.common.api.PageResponse;

@Service
public class AuditQueryService {
    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> list(int page, int size) {
        Page<Object[]> result = repository.findEntries(PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<AuditLogView> items = result.getContent().stream()
                .map(row -> new AuditLogView(
                        (Long) row[0], (Long) row[1], (String) row[2], (Long) row[3], (String) row[4],
                        (String) row[5], (Long) row[6], (String) row[7], (String) row[8], (Instant) row[9]))
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
