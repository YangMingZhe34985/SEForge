package com.ustb.seforge.job.infrastructure;

import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.service.JobHandler;
import com.ustb.seforge.job.service.JobSnapshot;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.ustb.seforge.common.web.TraceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class RedisJobWorker {
    private static final Logger log = LoggerFactory.getLogger(RedisJobWorker.class);
    private final AsyncJobService jobs;
    private final StringRedisTemplate redis;
    private final SEForgeProperties properties;
    private final Map<JobKind, JobHandler> handlers;
    private final com.ustb.seforge.job.service.JobExecutionAuthorizer authorization;
    private final String workerId = "worker-" + UUID.randomUUID();
    @org.springframework.beans.factory.annotation.Value("${seforge.jobs.read-batch-size:5}")
    private int readBatchSize = 5;
    private final ScheduledExecutorService leaseHeartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seforge-job-lease-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public RedisJobWorker(AsyncJobService jobs, StringRedisTemplate redis, SEForgeProperties properties,
                          List<JobHandler> handlers, com.ustb.seforge.job.service.JobExecutionAuthorizer authorization) {
        this.jobs = jobs;
        this.redis = redis;
        this.properties = properties;
        this.authorization = authorization;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::kind,
                Function.identity(), (left, right) -> left, () -> new EnumMap<>(JobKind.class)));
    }

    @PostConstruct
    void ensureGroup() {
        try {
            redis.opsForStream().createGroup(properties.getJobs().getStream(), ReadOffset.from("0-0"),
                    properties.getJobs().getGroup());
        } catch (RuntimeException missingStreamOrExistingGroup) {
            try {
                redis.opsForStream().add(properties.getJobs().getStream(), Map.of("bootstrap", "true"));
                redis.opsForStream().createGroup(properties.getJobs().getStream(), ReadOffset.from("0-0"),
                        properties.getJobs().getGroup());
            } catch (RuntimeException ignored) {
                log.debug("Redis consumer group is already available or Redis is starting: {}",
                        ignored.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${seforge.jobs.worker-poll-interval:250}")
    public void poll() {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redis.opsForStream().read(
                    Consumer.from(properties.getJobs().getGroup(), workerId),
                    StreamReadOptions.empty().count(Math.max(1, Math.min(readBatchSize, 100))).block(Duration.ofMillis(100)),
                    StreamOffset.create(properties.getJobs().getStream(), ReadOffset.lastConsumed()));
        } catch (RuntimeException exception) {
            log.warn("Job stream unavailable: {}", exception.getMessage());
            // Redis may have restarted without its stream/group. Durable jobs are
            // redelivered by DB recovery; recreate the group without restarting us.
            ensureGroup();
            return;
        }
        if (records == null) return;
        for (MapRecord<String, Object, Object> record : records) {
            Object rawJobId = record.getValue().get("jobId");
            if (rawJobId != null) execute(Long.valueOf(rawJobId.toString()));
            redis.opsForStream().acknowledge(properties.getJobs().getStream(),
                    properties.getJobs().getGroup(), record.getId());
        }
    }

    private void execute(Long jobId) {
        String previousTrace = MDC.get(TraceContext.TRACE_ID);
        MDC.put(TraceContext.TRACE_ID, UUID.randomUUID().toString());
        try {
            executeWithTrace(jobId);
        } finally {
            if (previousTrace == null) MDC.remove(TraceContext.TRACE_ID);
            else MDC.put(TraceContext.TRACE_ID, previousTrace);
        }
    }

    private void executeWithTrace(Long jobId) {
        JobSnapshot job = jobs.claim(jobId, workerId).orElse(null);
        if (job == null) return;
        long heartbeatMillis = Math.max(250, properties.getJobs().getLeaseDuration().toMillis() / 3);
        String jobTrace = TraceContext.currentTraceId();
        ScheduledFuture<?> heartbeat = leaseHeartbeat.scheduleAtFixedRate(() -> {
            String previousTrace = MDC.get(TraceContext.TRACE_ID);
            MDC.put(TraceContext.TRACE_ID, jobTrace);
            try {
                if (!jobs.renewLease(job.id(), workerId)) {
                    log.warn("Worker {} no longer owns lease for job {}", workerId, job.id());
                }
            } catch (RuntimeException exception) {
                log.warn("Could not renew lease for job {}: {}", job.id(), exception.getMessage());
            } finally {
                if (previousTrace == null) MDC.remove(TraceContext.TRACE_ID);
                else MDC.put(TraceContext.TRACE_ID, previousTrace);
            }
        }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        JobHandler handler = handlers.get(job.kind());
        try {
            authorization.authorize(job);
            if (handler == null) {
                jobs.fail(job.id(), workerId,
                        new IllegalStateException("No handler registered for " + job.kind()));
                return;
            }
            if (!jobs.complete(job.id(), workerId, handler.handle(job))) {
                log.warn("Discarded completion for job {} because its lease is no longer owned", job.id());
            }
        } catch (Exception exception) {
            log.warn("Job {} attempt {} failed ({})", job.id(), job.attempt(), exception.getClass().getSimpleName());
            if (!jobs.fail(job.id(), workerId, exception)) {
                log.warn("Discarded failure for job {} because its lease is no longer owned", job.id());
            }
        } finally {
            heartbeat.cancel(false);
        }
    }

    @PreDestroy
    void close() {
        leaseHeartbeat.shutdownNow();
    }
}
