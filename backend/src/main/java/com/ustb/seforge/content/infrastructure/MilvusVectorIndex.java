package com.ustb.seforge.content.infrastructure;

import com.ustb.seforge.ai.application.AiUnavailableException;
import com.ustb.seforge.config.SEForgeProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.dml.QueryIteratorParam;
import io.milvus.response.QueryResultsWrapper.RowRecord;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class MilvusVectorIndex implements VectorIndex {
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern COLLECTION_PREFIX = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,119}");
    private static final String ID_FIELD = "id";

    private final SEForgeProperties properties;
    private final ConcurrentMap<String, StoreHandle> stores = new ConcurrentHashMap<>();

    public MilvusVectorIndex(SEForgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addAll(String embeddingVersion, Long courseId, List<String> ids, List<Embedding> embeddings,
                       List<TextSegment> segments) {
        String version = validateVersion(embeddingVersion);
        requireCourse(courseId);
        if (ids == null || embeddings == null || segments == null
                || ids.size() != embeddings.size() || ids.size() != segments.size()) {
            throw new IllegalArgumentException("Vector ids, embeddings and segments must have equal sizes");
        }
        for (TextSegment segment : segments) {
            Long segmentCourseId = segment.metadata().getLong("courseId");
            String segmentVersion = segment.metadata().getString("embeddingVersion");
            if (!courseId.equals(segmentCourseId) || !version.equals(segmentVersion)) {
                throw new IllegalArgumentException(
                        "Every vector requires courseId and matching embeddingVersion metadata");
            }
        }
        if (!ids.isEmpty()) handle(version).store().addAll(ids, embeddings, segments);
    }

    @Override
    public List<VectorHit> search(String embeddingVersion, Long courseId, Embedding query,
                                  int limit, double minimumScore) {
        String version = validateVersion(embeddingVersion);
        requireCourse(courseId);
        Filter tenantAndVersion = MetadataFilterBuilder.metadataKey("courseId").isEqualTo(courseId)
                .and(MetadataFilterBuilder.metadataKey("embeddingVersion").isEqualTo(version));
        try {
        return handle(version).store().search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(query)
                        .maxResults(Math.min(Math.max(limit, 1), 20))
                        .minScore(minimumScore)
                        .filter(tenantAndVersion)
                        .build()).matches().stream()
                .map(match -> new VectorHit(match.embeddingId(), match.score(), match.embedded().text(),
                        match.embedded().metadata().toMap()))
                .toList();
        } catch (RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public void deleteDocument(String embeddingVersion, Long courseId, Long documentId) {
        String version = validateVersion(embeddingVersion);
        requireCourse(courseId);
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        Filter filter = MetadataFilterBuilder.metadataKey("courseId").isEqualTo(courseId)
                .and(MetadataFilterBuilder.metadataKey("documentId").isEqualTo(documentId))
                .and(MetadataFilterBuilder.metadataKey("embeddingVersion").isEqualTo(version));
        handle(version).store().removeAll(filter);
    }

    @Override
    public Set<String> listIds(String embeddingVersion, Long courseId) {
        String version = validateVersion(embeddingVersion);
        requireCourse(courseId);
        StoreHandle handle = handle(version);
        String expression = "metadata[\"courseId\"] == " + courseId
                + " && metadata[\"embeddingVersion\"] == \"" + version + "\"";
        int pageSize = Math.min(Math.max(
                properties.getVectorStore().getReconciliationPageSize(), 1), 10_000);
        R<QueryIterator> response = handle.client().queryIterator(QueryIteratorParam.newBuilder()
                .withCollectionName(handle.collectionName())
                .withExpr(expression)
                .withOutFields(List.of(ID_FIELD))
                .withBatchSize((long) pageSize)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build());
        ensureSuccess(response, "Could not scan Milvus vector ids");

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        QueryIterator iterator = response.getData();
        try {
            while (true) {
                List<RowRecord> rows = iterator.next();
                if (rows == null || rows.isEmpty()) break;
                for (RowRecord row : rows) {
                    Object id = row.get(ID_FIELD);
                    if (id != null) ids.add(id.toString());
                }
            }
        } finally {
            iterator.close();
        }
        return Set.copyOf(ids);
    }

    @Override
    public void deleteIds(String embeddingVersion, Long courseId, Collection<String> ids) {
        String version = validateVersion(embeddingVersion);
        requireCourse(courseId);
        if (ids == null || ids.isEmpty()) return;
        Set<String> owned = listIds(version, courseId);
        List<String> scoped = ids.stream().filter(owned::contains).distinct().toList();
        if (!scoped.isEmpty()) handle(version).store().removeAll(scoped);
    }

    @Override
    public String collectionName(String embeddingVersion) {
        String version = validateVersion(embeddingVersion);
        String prefix = properties.getVectorStore().getCollectionPrefix();
        prefix = prefix == null ? "" : prefix.trim();
        if (!COLLECTION_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Milvus collection-prefix must match "
                    + COLLECTION_PREFIX.pattern());
        }
        String readable = version.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return prefix + "_" + readable + "_" + shortHash(version);
    }

    private StoreHandle handle(String version) {
        if (!properties.getVectorStore().isEnabled()) {
            throw unavailable(null);
        }
        try {
            return stores.computeIfAbsent(version, this::createHandle);
        } catch (RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    private com.ustb.seforge.common.exception.AppException unavailable(RuntimeException failure) {
        String reason = !properties.getVectorStore().isEnabled() ? "DISABLED" : "CONNECTION_OR_INDEX";
        org.slf4j.LoggerFactory.getLogger(MilvusVectorIndex.class).warn(
                "Milvus unavailable: reason={} exception={}", reason,
                failure == null ? "none" : failure.getClass().getSimpleName());
        return new com.ustb.seforge.common.exception.AppException(
                com.ustb.seforge.common.exception.ErrorCode.VECTOR_STORE_UNAVAILABLE,
                "Milvus 检索不可用（" + reason + "），请检查向量开关、MILVUS_HOST/端口映射与索引状态",
                java.util.Map.of("component", "MILVUS", "reason", reason));
    }

    private StoreHandle createHandle(String version) {
        String collectionName = collectionName(version);
        ConnectParam connect = ConnectParam.newBuilder()
                .withHost(properties.getVectorStore().getHost())
                .withPort(properties.getVectorStore().getPort())
                .withConnectTimeout(5, TimeUnit.SECONDS)
                .build();
        MilvusServiceClient client = new MilvusServiceClient(connect);
        try {
        EmbeddingStore<TextSegment> store = MilvusEmbeddingStore.builder()
                .milvusClient(client)
                .collectionName(collectionName)
                .dimension(properties.getAi().getEmbeddingDimension())
                .retrieveEmbeddingsOnSearch(false)
                // Strong reads see acknowledged inserts/deletes, including growing segments.
                // Per-batch flush is unnecessary and hits Milvus' default flush rate limit.
                .consistencyLevel(ConsistencyLevelEnum.STRONG)
                .autoFlushOnInsert(false)
                .build();
        return new StoreHandle(collectionName, client, store);
        } catch (RuntimeException failure) {
            try { client.close(3); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            throw failure;
        }
    }

    private void ensureSuccess(R<?> response, String message) {
        if (response == null || response.getStatus() == null
                || response.getStatus() != R.Status.Success.getCode() || response.getData() == null) {
            String detail = response == null ? "no response" : response.getMessage();
            throw new AiUnavailableException(message + (detail == null || detail.isBlank() ? "" : ": " + detail));
        }
    }

    private String validateVersion(String value) {
        String version = value == null ? "" : value.trim();
        if (!VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("Embedding version must match " + VERSION.pattern());
        }
        return version;
    }

    private void requireCourse(Long courseId) {
        if (courseId == null || courseId <= 0) {
            throw new IllegalArgumentException("courseId filter is mandatory");
        }
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(12);
            for (int index = 0; index < 6; index++) {
                result.append(String.format("%02x", digest[index] & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @PreDestroy
    void close() {
        for (StoreHandle handle : stores.values()) {
            try {
                handle.client().close(3);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private record StoreHandle(String collectionName, MilvusServiceClient client,
                               EmbeddingStore<TextSegment> store) {
    }
}
