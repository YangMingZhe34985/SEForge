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
    private final MinioClient client;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public MinioObjectStorage(SEForgeProperties properties) {
        SEForgeProperties.Storage storage = properties.getStorage();
        client = MinioClient.builder().endpoint(storage.getEndpoint())
                .credentials(storage.getAccessKey(), storage.getSecretKey()).build();
        bucket = storage.getBucket();
    }

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) throws IOException {
        ensureBucket();
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw new IOException("Object storage upload failed", exception);
        }
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new IOException("Object storage read failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new IOException("Object storage delete failed", exception);
        }
    }

    private synchronized void ensureBucket() throws IOException {
        if (bucketReady.get()) return;
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady.set(true);
        } catch (Exception exception) {
            throw new IOException("Object storage is unavailable", exception);
        }
    }
}
