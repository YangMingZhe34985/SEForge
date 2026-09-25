package com.ustb.seforge.content.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.content.domain.IngestionJob;
import com.ustb.seforge.content.domain.IngestionStatus;
import com.ustb.seforge.content.domain.KnowledgeChunk;
import com.ustb.seforge.content.domain.KnowledgeDocument;
import com.ustb.seforge.content.repository.IngestionJobRepository;
import com.ustb.seforge.content.repository.KnowledgeChunkRepository;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPersistence {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final IngestionJobRepository ingestions;

    public IngestionPersistence(KnowledgeDocumentRepository documents, KnowledgeChunkRepository chunks,
                                IngestionJobRepository ingestions) {
        this.documents = documents;
        this.chunks = chunks;
        this.ingestions = ingestions;
    }

    @Transactional
    public IngestionContext begin(Long asyncJobId) {
        IngestionJob ingestion = ingestions.findByAsyncJobId(asyncJobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ingestion record not found"));
        KnowledgeDocument document = documents.findById(ingestion.getDocumentId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document not found"));
        requireLive(document.getId());
        ingestion.processing();
        document.beginIngestion();
        return IngestionContext.from(document, ingestion);
    }

    @Transactional
    public List<String> replaceChunks(Long documentId, String embeddingVersion,
                                      List<KnowledgeChunk> replacements) {
        List<String> staleVectorIds = chunks
                .findAllByDocumentIdAndEmbeddingVersionOrderByChunkIndexAsc(documentId, embeddingVersion)
                .stream().map(KnowledgeChunk::getVectorId).toList();
        chunks.deleteAllByDocumentIdAndEmbeddingVersion(documentId, embeddingVersion);
        chunks.flush();
        chunks.saveAll(replacements);
        return staleVectorIds;
    }

    @Transactional
    public void complete(Long asyncJobId, Long documentId, String embeddingVersion) {
        documents.findById(documentId).ifPresent(document -> document.indexed(embeddingVersion));
        ingestions.findByAsyncJobId(asyncJobId).ifPresent(IngestionJob::completed);
    }

    @Transactional
    public void fail(Long asyncJobId, Long documentId, Throwable error) {
        IngestionJob ingestion = ingestions.findByAsyncJobId(asyncJobId).orElse(null);
        if (ingestion == null) return;
        KnowledgeDocument document = documents.findById(documentId).orElse(null);
        if (ingestion.getStatus() == IngestionStatus.CANCELLED) {
            if (document != null) document.ingestionCancelled();
            return;
        }
        ingestion.failed(error);
        if (document != null) document.ingestionFailed(error);
    }

    @Transactional
    public void cancel(Long asyncJobId, Long documentId) {
        documents.findById(documentId).ifPresent(KnowledgeDocument::ingestionCancelled);
        ingestions.findByAsyncJobId(asyncJobId).ifPresent(IngestionJob::cancelled);
    }

    @Transactional(readOnly = true)
    public void requireLive(Long documentId) {
        KnowledgeDocument document = documents.findById(documentId).orElseThrow(() ->
                new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document not found"));
        if (document.getStatus() == com.ustb.seforge.content.domain.DocumentStatus.DELETED
                || document.getStatus() == com.ustb.seforge.content.domain.DocumentStatus.DELETING) {
            throw new com.ustb.seforge.job.service.JobExecutionAbortedException("Document was deleted");
        }
    }

    public record IngestionContext(Long documentId, Long courseId, Long chapterId, String objectKey,
                                   String originalName, String parserVersion, String embeddingVersion) {
        static IngestionContext from(KnowledgeDocument document, IngestionJob ingestion) {
            if (!document.getCourseId().equals(ingestion.getCourseId())) {
                throw new AppException(ErrorCode.CONFLICT, "Ingestion course does not match document course");
            }
            return new IngestionContext(document.getId(), document.getCourseId(), document.getChapterId(),
                    document.getObjectKey(), document.getOriginalName(), ingestion.getParserVersion(),
                    ingestion.getEmbeddingVersion());
        }
    }
}
