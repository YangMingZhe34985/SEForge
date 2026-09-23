package com.ustb.seforge.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.domain.JobStatus;
import com.ustb.seforge.job.service.JobEventBroker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class JobEventBrokerTest {
    @Test
    void duplicateTerminalPublicationEmitsOneDoneWithStableRequestId() {
        JobEventBroker broker = new JobEventBroker();
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        AsyncJobView running = new AsyncJobView(1L, JobKind.REVIEW_DOCUMENT, JobStatus.RUNNING,
                11L, 1, 3, false, null, null, now, now);
        AsyncJobView completed = new AsyncJobView(1L, JobKind.REVIEW_DOCUMENT, JobStatus.COMPLETED,
                11L, 1, 3, false, "{}", null, now, now.plusSeconds(1));

        SseEmitter emitter = broker.subscribe(running);
        broker.publish(completed);
        broker.publish(completed);
        List<ObservedEvent> events = events(emitter);

        assertThat(events).extracting(ObservedEvent::type)
                .containsExactly("job.status", "job.status", "done");
        assertThat(events).extracting(ObservedEvent::requestId)
                .containsOnly(events.getFirst().requestId());
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"FAILED", "CANCELLED", "DEAD_LETTER"})
    void unsuccessfulTerminalPublicationEmitsOneErrorAndNeverDone(JobStatus terminalStatus) {
        JobEventBroker broker = new JobEventBroker();
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        AsyncJobView running = new AsyncJobView(2L, JobKind.REVIEW_DOCUMENT, JobStatus.RUNNING,
                11L, 1, 3, false, null, null, now, now);
        AsyncJobView failed = new AsyncJobView(2L, JobKind.REVIEW_DOCUMENT, terminalStatus,
                11L, 1, 3, false, null, "failed", now, now.plusSeconds(1));

        SseEmitter emitter = broker.subscribe(running);
        broker.publish(failed);
        broker.publish(failed);

        assertThat(events(emitter)).extracting(ObservedEvent::type)
                .containsExactly("job.status", "job.status", "error")
                .doesNotContain("done");
    }

    private List<ObservedEvent> events(SseEmitter emitter) {
        Set<?> pending = (Set<?>) ReflectionTestUtils.getField(emitter, "earlySendAttempts");
        List<ObservedEvent> events = new ArrayList<>();
        if (pending == null) return events;
        for (Object item : pending) {
            Object data = ReflectionTestUtils.getField(item, "data");
            if (data != null && data.getClass().getSimpleName().equals("StreamEvent")) {
                events.add(new ObservedEvent(
                        ReflectionTestUtils.invokeMethod(data, "type"),
                        ReflectionTestUtils.invokeMethod(data, "requestId")));
            }
        }
        return events;
    }

    private record ObservedEvent(String type, String requestId) {
    }
}
