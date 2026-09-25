package com.ustb.seforge.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.content.domain.KnowledgeChunk;
import com.ustb.seforge.content.infrastructure.EmbeddingProvider;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.content.infrastructure.VectorIndex;
import com.ustb.seforge.content.service.DocumentParserService.ParsedSection;
import com.ustb.seforge.content.service.IngestionPersistence.IngestionContext;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.JobHandler;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.service.JobExecutionAbortedException;
import com.ustb.seforge.job.service.JobSnapshot;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIngestionService implements JobHandler {
    private final IngestionPersistence persistence;
    private final DocumentParserService parser;
    private final ObjectStorage storage;
    private final EmbeddingProvider embeddings;
    private final VectorIndex vectors;
    private final ObjectMapper objectMapper;
    private final TokenCountEstimator tokenEstimator;
    private final DocumentSplitter splitter;
    private final AsyncJobService jobs;
    private final CourseIndexWriteService indexWrites;

    public KnowledgeIngestionService(IngestionPersistence persistence, DocumentParserService parser,
                                     ObjectStorage storage, EmbeddingProvider embeddings,
                                     VectorIndex vectors, ObjectMapper objectMapper,
                                     TokenCountEstimator tokenEstimator, AsyncJobService jobs,
                                     CourseIndexWriteService indexWrites) {
        this.persistence = persistence;
        this.parser = parser;
        this.storage = storage;
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.objectMapper = objectMapper;
        this.tokenEstimator = tokenEstimator;
        this.jobs = jobs;
        this.indexWrites = indexWrites;
        this.splitter = DocumentSplitters.recursive(700, 100, tokenEstimator);
    }

    @Override
    public JobKind kind() {
        return JobKind.INGEST_DOCUMENT;
    }

    @Override
    public String handle(JobSnapshot job) throws Exception {
        AtomicReference<IngestionContext> started = new AtomicReference<>();
        jobs.runIfActive(job.id(), job.workerId(), () -> started.set(persistence.begin(job.id())));
        IngestionContext context = started.get();
        List<String> newVectorIds = List.of();
        boolean committed = false;
        try {
            List<ParsedSection> sections;
            try (InputStream input = storage.open(context.objectKey())) {
                sections = parser.parse(context.originalName(), input);
            }
            checkpoint(job);
            if (sections.isEmpty() || sections.stream().allMatch(section -> section.text().isBlank())) {
                throw new IllegalArgumentException("Document contains no extractable text");
            }

            List<TextSegment> segments = split(context, sections);
            List<Embedding> vectorsForSegments = embeddings.embedAll(context.embeddingVersion(), segments);
            checkpoint(job);
            List<String> vectorIds = new ArrayList<>(segments.size());
            List<KnowledgeChunk> chunks = new ArrayList<>(segments.size());
            for (int index = 0; index < segments.size(); index++) {
                TextSegment segment = segments.get(index);
                String vectorId = UUID.randomUUID().toString();
                vectorIds.add(vectorId);
                Metadata metadata = segment.metadata();
                chunks.add(new KnowledgeChunk(context.courseId(), context.documentId(), context.chapterId(), index,
                        segment.text(), tokenEstimator.estimateTokenCountInText(segment.text()),
                        metadata.getInteger("page"), metadata.getString("section"),
                        context.originalName(), context.parserVersion(), context.embeddingVersion(),
                        vectorId, json(metadata.toMap())));
            }
            newVectorIds = List.copyOf(vectorIds);
            String result = json(Map.of("documentId", context.documentId(), "chunks", chunks.size(),
                    "embeddingVersion", context.embeddingVersion()));
            AtomicReference<List<String>> staleVectorIds = new AtomicReference<>(List.of());
            indexWrites.execute(context.courseId(), () -> {
                persistence.requireLive(context.documentId());
                checkpoint(job);
                vectors.addAll(context.embeddingVersion(), context.courseId(), vectorIds, vectorsForSegments, segments);
                jobs.completeAtomically(job.id(), job.workerId(), result, () -> {
                    staleVectorIds.set(persistence.replaceChunks(
                            context.documentId(), context.embeddingVersion(), chunks));
                    persistence.complete(job.id(), context.documentId(), context.embeddingVersion());
                });
                return null;
            });
            committed = true;
            try {
                vectors.deleteIds(context.embeddingVersion(), context.courseId(), staleVectorIds.get());
            } catch (RuntimeException ignored) {
                // Reconciliation removes stale vectors after the committed index swap.
            }
            return result;
        } catch (Exception exception) {
            if (!committed && !newVectorIds.isEmpty()) {
                try {
                    vectors.deleteIds(context.embeddingVersion(), context.courseId(), newVectorIds);
                } catch (RuntimeException ignored) {
                    // Reconciliation will remove any vector left behind by a partial provider failure.
                }
            }
            if (exception instanceof JobExecutionAbortedException) {
                persistence.cancel(job.id(), context.documentId());
            } else {
                persistence.fail(job.id(), context.documentId(), exception);
            }
            throw exception;
        }
    }

    private void checkpoint(JobSnapshot job) {
        if (!jobs.isExecutionActive(job.id(), job.workerId())) {
            throw new JobExecutionAbortedException("Ingestion was cancelled or its worker lease was lost");
        }
    }

    private List<TextSegment> split(IngestionContext context, List<ParsedSection> sections) {
        List<TextSegment> result = new ArrayList<>();
        for (ParsedSection section : sections) {
            Metadata metadata = new Metadata()
                    .put("courseId", context.courseId())
                    .put("documentId", context.documentId())
                    .put("source", context.originalName())
                    .put("page", section.page() == null ? 0 : section.page())
                    .put("section", section.section() == null ? "" : section.section())
                    .put("parserVersion", context.parserVersion())
                    .put("embeddingVersion", context.embeddingVersion());
            metadata.put("chapterId", context.chapterId() == null ? 0L : context.chapterId());
            result.addAll(splitter.split(Document.from(section.text(), metadata)));
        }
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize ingestion metadata", exception);
        }
    }
}
