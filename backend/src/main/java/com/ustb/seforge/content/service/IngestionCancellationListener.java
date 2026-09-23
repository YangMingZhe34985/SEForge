package com.ustb.seforge.content.service;

import com.ustb.seforge.content.repository.IngestionJobRepository;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.JobCancellationRequested;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class IngestionCancellationListener {
    private final IngestionJobRepository ingestions;
    private final KnowledgeDocumentRepository documents;

    public IngestionCancellationListener(IngestionJobRepository ingestions,
                                         KnowledgeDocumentRepository documents) {
        this.ingestions = ingestions;
        this.documents = documents;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void cancellationRequested(JobCancellationRequested event) {
        if (event.kind() != JobKind.INGEST_DOCUMENT) return;
        ingestions.findByAsyncJobId(event.jobId()).ifPresent(ingestion -> {
            ingestion.cancelled();
            documents.findById(ingestion.getDocumentId())
                    .ifPresent(com.ustb.seforge.content.domain.KnowledgeDocument::ingestionCancelled);
        });
    }
}
