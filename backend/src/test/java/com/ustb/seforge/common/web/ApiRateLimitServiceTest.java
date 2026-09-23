package com.ustb.seforge.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class ApiRateLimitServiceTest {
    @Test
    void enforcesIndependentLocalBucketsWhenRedisIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        ApiRateLimitService service = new ApiRateLimitService(provider);

        assertThat(service.allow("user:7", "general", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(service.allow("user:7", "general", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(service.allow("user:7", "general", 2, Duration.ofMinutes(1))).isFalse();
        assertThat(service.allow("user:7", "upload", 1, Duration.ofMinutes(1))).isTrue();
    }
}
