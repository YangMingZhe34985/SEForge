package com.ustb.seforge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisMinioIntegrationTest {
    private static final String REDIS_PASSWORD = "integration-redis-password";
    private static final String MINIO_USER = "integration-minio-user";
    private static final String MINIO_PASSWORD = "integration-minio-password";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .waitingFor(Wait.forListeningPort());

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            "quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test
    void redisSupportsDurableSessionAndQueuePrimitives() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        configuration.setPassword(RedisPassword.of(REDIS_PASSWORD));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            template.afterPropertiesSet();
            template.opsForValue().set("seforge:test", "ready");
            assertThat(template.opsForValue().get("seforge:test")).isEqualTo("ready");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void minioStoresAndReadsCourseObjects() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000))
                .credentials(MINIO_USER, MINIO_PASSWORD)
                .build();
        String bucket = "seforge-integration";
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        byte[] content = "course material".getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectArgs.builder().bucket(bucket).object("courses/1/material.txt")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .contentType("text/plain").build());

        assertThat(client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())).isTrue();
        try (var stored = client.getObject(GetObjectArgs.builder().bucket(bucket)
                .object("courses/1/material.txt").build())) {
            assertThat(stored.readAllBytes()).isEqualTo(content);
        }
    }
}
