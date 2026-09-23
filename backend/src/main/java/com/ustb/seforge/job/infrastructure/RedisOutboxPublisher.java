package com.ustb.seforge.job.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.job.domain.OutboxEvent;
import com.ustb.seforge.job.domain.OutboxStatus;
import com.ustb.seforge.job.repository.OutboxEventRepository;
import com.ustb.seforge.job.service.AsyncJobService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "seforge.jobs", name = "enabled", havingValue = "true")
public class RedisOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisOutboxPublisher.class);
    private final OutboxEventRepository outbox;
    private final AsyncJobService jobs;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SEForgeProperties properties;

    public RedisOutboxPublisher(OutboxEventRepository outbox, AsyncJobService jobs,
                                StringRedisTemplate redis, ObjectMapper objectMapper,
                                SEForgeProperties properties) {
        this.outbox = outbox;
        this.jobs = jobs;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${seforge.jobs.outbox-poll-interval:500}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outbox.findTop50ByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
                OutboxStatus.PENDING, Instant.now())) {
            try {
                Map<String, String> values = new LinkedHashMap<>();
                values.put("eventId", event.getId().toString());
                values.put("eventType", event.getEventType());
                JsonNode payload = objectMapper.readTree(event.getPayloadJson());
                payload.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText()));
                redis.opsForStream().add(StreamRecords.string(values)
                        .withStreamKey(properties.getJobs().getStream()));
                event.published();
                if ("AsyncJob".equals(event.getAggregateType())) {
                    jobs.markDispatched(event.getAggregateId());
                }
            } catch (JsonProcessingException | RuntimeException exception) {
                event.retryLater();
                log.warn("Could not publish outbox event {}: {}", event.getId(), exception.getMessage());
            }
        }
    }
}
