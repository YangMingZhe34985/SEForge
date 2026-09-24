package com.ustb.seforge.identity.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.api.PasswordResetTokenView;
import com.ustb.seforge.identity.domain.User;
import com.ustb.seforge.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final SessionRevocationService sessions;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordEncoder passwords,
                                SessionRevocationService sessions,
                                ObjectProvider<StringRedisTemplate> redisProvider) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.redis = redisProvider.getIfAvailable();
    }

    public PasswordResetTokenView issue(Long userId) {
        users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        requireRedis();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(key(token), userId.toString(), TOKEN_LIFETIME);
        } catch (DataAccessException exception) {
            throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE, "Password reset store is unavailable");
        }
        return new PasswordResetTokenView(token, Instant.now().plus(TOKEN_LIFETIME));
    }

    @Transactional
    public void complete(String token, String newPassword) {
        requireRedis();
        String value;
        try {
            value = redis.opsForValue().getAndDelete(key(token));
        } catch (DataAccessException exception) {
            throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE, "Password reset store is unavailable");
        }
        if (value == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Reset token is invalid or expired");
        User user = users.findById(Long.parseLong(value))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        user.setPasswordHash(passwords.encode(newPassword));
        sessions.revokePrincipal(user.getUsername());
    }

    private void requireRedis() {
        if (redis == null) throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE,
                "Password reset store is unavailable");
    }

    private String key(String token) {
        if (token == null || token.length() > 200) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Reset token is invalid or expired");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "seforge:password-reset:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
