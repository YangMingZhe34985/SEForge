package com.ustb.seforge.common.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class ApiRateLimitService {
    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitService.class);
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local value = redis.call('INCR', KEYS[1])
            if value == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return value
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, LocalWindow> local = new ConcurrentHashMap<>();

    public ApiRateLimitService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public boolean allow(String subject, String bucket, int maximum, Duration window) {
        if (maximum < 1 || window == null || window.isZero() || window.isNegative()) return false;
        String key = "seforge:api-rate:" + bucket + ":" + hash(subject);
        if (redis != null) {
            try {
                Long value = redis.execute(INCREMENT, Collections.singletonList(key),
                        Long.toString(window.toMillis()));
                return value != null && value <= maximum;
            } catch (DataAccessException exception) {
                log.warn("API rate-limit store unavailable; using process-local protection");
            }
        }
        Instant now = Instant.now();
        LocalWindow state = local.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new LocalWindow(new AtomicInteger(1), now.plus(window));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return state.count().get() <= maximum;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private record LocalWindow(AtomicInteger count, Instant expiresAt) {
    }
}
