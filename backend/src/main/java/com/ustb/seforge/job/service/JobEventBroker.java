package com.ustb.seforge.job.service;

import com.ustb.seforge.common.web.TraceContext;
import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.JobStatus;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class JobEventBroker {
    private final Map<Long, Set<Subscription>> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public SseEmitter subscribe(AsyncJobView job) {
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        Subscription subscription = new Subscription(job.id(), emitter,
                UUID.randomUUID().toString(), TraceContext.getOrCreate());
        if (!job.status().isTerminal()) {
            subscribers.computeIfAbsent(job.id(), ignored -> ConcurrentHashMap.newKeySet()).add(subscription);
            emitter.onCompletion(subscription::close);
            emitter.onTimeout(subscription::close);
            emitter.onError(ignored -> subscription.close());
        }
        subscription.publish(job);
        return emitter;
    }

    public void publish(AsyncJobView job) {
        Set<Subscription> subscriptions = subscribers.getOrDefault(job.id(), Set.of());
        for (Subscription subscription : subscriptions) {
            if (!subscription.publish(job)) remove(job.id(), subscription);
        }
    }

    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        subscribers.forEach((jobId, subscriptions) -> subscriptions.forEach(subscription -> {
            if (!subscription.heartbeat()) {
                remove(jobId, subscription);
            }
        }));
    }

    private void remove(Long jobId, Subscription subscription) {
        Set<Subscription> subscriptions = subscribers.get(jobId);
        if (subscriptions != null) {
            subscriptions.remove(subscription);
            if (subscriptions.isEmpty()) subscribers.remove(jobId);
        }
    }

    private final class Subscription {
        private final Long jobId;
        private final SseEmitter emitter;
        private final String requestId;
        private final String traceId;
        private final AtomicBoolean terminal = new AtomicBoolean();

        private Subscription(Long jobId, SseEmitter emitter, String requestId, String traceId) {
            this.jobId = jobId;
            this.emitter = emitter;
            this.requestId = requestId;
            this.traceId = traceId;
        }

        synchronized boolean publish(AsyncJobView job) {
            if (terminal.get()) return false;
            if (!send("job.status", job)) return closeLocked();
            if (!job.status().isTerminal()) return true;
            if (terminal.compareAndSet(false, true)) {
                String terminalType = job.status() == JobStatus.COMPLETED ? "done" : "error";
                send(terminalType, Map.of("status", job.status().name()));
                emitter.complete();
            }
            remove(jobId, this);
            return false;
        }

        synchronized boolean heartbeat() {
            if (terminal.get()) return false;
            return send("heartbeat", Map.of("at", Instant.now().toString())) || closeLocked();
        }

        synchronized void close() {
            closeLocked();
        }

        private boolean closeLocked() {
            terminal.set(true);
            remove(jobId, this);
            return false;
        }

        private boolean send(String type, Object data) {
            try {
                String eventId = Long.toString(sequence.incrementAndGet());
                emitter.send(SseEmitter.event().id(eventId).name(type).data(new StreamEvent(
                        type, requestId, traceId, eventId, data)));
                return true;
            } catch (IOException | IllegalStateException exception) {
                return false;
            }
        }
    }

    private record StreamEvent(String type, String requestId, String traceId, String eventId, Object data) {
    }
}
