package com.ustb.seforge.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisLoginAttemptService implements LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptService.class);
    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentHashMap<String, LocalWindow> localFallback = new ConcurrentHashMap<>();

    public RedisLoginAttemptService(
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${seforge.security.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${seforge.security.login-rate-limit.window:PT15M}") Duration window) {
        this.redis = redisProvider.getIfAvailable();
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    public boolean isBlocked(String identifier, String remoteAddress) {
        String key = key(identifier, remoteAddress);
        if (redis == null) return localBlocked(key);
        try {
            String value = redis.opsForValue().get(key);
            return value != null && Long.parseLong(value) >= maxAttempts;
        } catch (DataAccessException | NumberFormatException exception) {
            log.warn("Login rate-limit store unavailable; using process-local protection");
            return localBlocked(key);
        }
    }

    @Override
    public void recordFailure(String identifier, String remoteAddress) {
        String key = key(identifier, remoteAddress);
        if (redis == null) {
            localFailure(key);
            return;
        }
        try {
            Long attempts = redis.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redis.expire(key, window);
            }
        } catch (DataAccessException exception) {
            log.warn("Could not record failed login attempt in Redis; using process-local protection");
            localFailure(key);
        }
    }

    @Override
    public void clear(String identifier, String remoteAddress) {
        String key = key(identifier, remoteAddress);
        localFallback.remove(key);
        if (redis == null) return;
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            log.warn("Could not clear login rate-limit state");
        }
    }

    private String key(String identifier, String remoteAddress) {
        String value = (identifier == null ? "" : identifier.trim().toLowerCase()) + '|' + remoteAddress;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "seforge:login-attempt:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private boolean localBlocked(String key) {
        LocalWindow state = localFallback.get(key);
        if (state == null) return false;
        if (state.expiresAt().isBefore(java.time.Instant.now())) {
            localFallback.remove(key, state);
            return false;
        }
        return state.attempts().get() >= maxAttempts;
    }

    private void localFailure(String key) {
        java.time.Instant now = java.time.Instant.now();
        localFallback.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return new LocalWindow(new AtomicInteger(1), now.plus(window));
            }
            existing.attempts().incrementAndGet();
            return existing;
        });
    }

    private record LocalWindow(AtomicInteger attempts, java.time.Instant expiresAt) {
    }
}
