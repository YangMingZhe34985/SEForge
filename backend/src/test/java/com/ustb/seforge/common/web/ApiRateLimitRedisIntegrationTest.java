package com.ustb.seforge.common.web;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker=true)
class ApiRateLimitRedisIntegrationTest {
 @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.2-alpine").withExposedPorts(6379);
 @Test @SuppressWarnings("unchecked") void distributedLimitAndOutageCannotResetAllowance() {
  var config=new RedisStandaloneConfiguration(REDIS.getHost(),REDIS.getMappedPort(6379));
  var factory=new LettuceConnectionFactory(config,LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(400)).shutdownTimeout(Duration.ZERO).build());factory.afterPropertiesSet();
  try {
   var redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();
   ObjectProvider<StringRedisTemplate> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(redis);
   var first=new ApiRateLimitService(provider);var second=new ApiRateLimitService(provider);
   assertThat(first.allow("student","general",2,Duration.ofSeconds(30))).isTrue();
   assertThat(second.allow("student","general",2,Duration.ofSeconds(30))).isTrue();
   assertThat(first.allow("student","general",2,Duration.ofSeconds(30))).isFalse();
   assertThat(redis.keys("seforge:api-rate:*")).hasSize(1);
   REDIS.stop();
   assertThat(first.allow("student","general",2,Duration.ofSeconds(30))).isFalse();
   assertThat(first.allow("student","upload",1,Duration.ofSeconds(30))).isTrue();
   assertThat(first.allow("student","upload",1,Duration.ofSeconds(30))).isFalse();
  } finally {factory.destroy();}
 }
}
