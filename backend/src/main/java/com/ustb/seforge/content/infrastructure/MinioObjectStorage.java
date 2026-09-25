package com.ustb.seforge.content.infrastructure;

import com.ustb.seforge.config.SEForgeProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class MinioObjectStorage implements ObjectStorage {
    private volatile MinioClient client;
    private final SEForgeProperties.Storage storage;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public MinioObjectStorage(SEForgeProperties properties) {
        storage = properties.getStorage();
        bucket = storage.getBucket();
    }

    private synchronized MinioClient client() {
        if (client == null) {
            if (storage.getAccessKey().isBlank() || storage.getSecretKey().isBlank()) {
                throw new IllegalArgumentException("Storage credentials are not configured");
            }
            client = MinioClient.builder().endpoint(storage.getEndpoint())
                    .credentials(storage.getAccessKey(), storage.getSecretKey()).build();
        }
        return client;
    }

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) throws IOException {
        ensureBucket();
        try {
            client().putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        try {
            return client().getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private synchronized void ensureBucket() throws IOException {
        if (bucketReady.get()) return;
        try {
            if (!client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady.set(true);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private com.ustb.seforge.common.exception.AppException unavailable(Exception failure) {
        String reason = failure instanceof IllegalArgumentException ? "CONFIGURATION" : "CONNECTION_OR_SERVICE";
        if (failure instanceof io.minio.errors.ErrorResponseException response) {
            reason = switch (response.errorResponse().code()) {
                case "InvalidAccessKeyId", "SignatureDoesNotMatch", "AccessDenied" -> "CREDENTIALS_OR_POLICY";
                case "NoSuchBucket" -> "BUCKET_NOT_INITIALIZED";
                case "NoSuchKey" -> "OBJECT_NOT_FOUND";
                default -> "SERVICE_RESPONSE";
            };
        }
        org.slf4j.LoggerFactory.getLogger(MinioObjectStorage.class)
                .warn("MinIO operation failed: reason={} exception={}", reason, failure.getClass().getSimpleName());
        return new com.ustb.seforge.common.exception.AppException(
                com.ustb.seforge.common.exception.ErrorCode.STORAGE_UNAVAILABLE,
                "MinIO 存储不可用（" + reason + "），请检查服务、宿主机端口、应用凭据及 minio-init 初始化结果",
                java.util.Map.of("component", "MINIO", "reason", reason));
    }
}
