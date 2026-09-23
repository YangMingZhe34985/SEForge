package com.ustb.seforge.content.repository;

import com.ustb.seforge.content.domain.IngestionJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {
    Optional<IngestionJob> findByAsyncJobId(Long asyncJobId);

    Optional<IngestionJob> findFirstByDocumentIdOrderByIdDesc(Long documentId);
}
