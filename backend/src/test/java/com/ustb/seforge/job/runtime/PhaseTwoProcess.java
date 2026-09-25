package com.ustb.seforge.job.runtime;

import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.infrastructure.*;
import com.ustb.seforge.job.service.*;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only worker process. No web endpoint, no business Agent, no production bean scan. */
@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableConfigurationProperties(SEForgeProperties.class)
@EntityScan("com.ustb.seforge")
@EnableJpaRepositories({"com.ustb.seforge.job.repository", "com.ustb.seforge.identity.repository", "com.ustb.seforge.course.repository"})
@Import({AsyncJobService.class, JobEventBroker.class, RedisOutboxPublisher.class, RedisJobWorker.class,
        JobExecutionAuthorizer.class, CourseAccessService.class})
public class PhaseTwoProcess {
    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(PhaseTwoProcess.class).web(WebApplicationType.NONE)
                .properties(Map.ofEntries(
                        Map.entry("spring.config.name", "phase2-fixture"),
                        Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                        Map.entry("spring.flyway.clean-disabled", "true"),
                        Map.entry("spring.data.redis.repositories.enabled", "false"),
                        Map.entry("spring.data.redis.timeout", "2s"),
                        Map.entry("spring.data.redis.connect-timeout", "2s"),
                        Map.entry("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"),
                        Map.entry("seforge.jobs.enabled", "true"),
                        Map.entry("seforge.jobs.lease-duration", "3s"),
                        Map.entry("seforge.jobs.queued-stale-after", "1s"),
                        Map.entry("logging.level.root", "WARN")))
                .run(args);
    }

    public static void main(String[] args) throws Exception {
        try (var context = start(args)) {
            var worker = context.getBean(RedisJobWorker.class);
            System.out.println("PHASE2_WORKER_READY");
            while (!Thread.currentThread().isInterrupted()) { worker.poll(); Thread.sleep(50); }
        }
    }

    @Bean JobHandler probeHandler(AsyncJobService jobs, JdbcTemplate jdbc) {
        return new JobHandler() {
            @Override public JobKind kind() { return JobKind.REVIEW_DOCUMENT; }
            @Override public String handle(JobSnapshot job) throws Exception {
                if (job.payloadJson().contains("fail")) throw new IllegalStateException("Scripted dependency failure");
                if (job.payloadJson().contains("slow")) Thread.sleep(5000);
                return jobs.completeAtomically(job.id(), job.workerId(), () -> {
                    jdbc.update("insert into phase2_probe_result(job_id) values (?)", job.id());
                    return "{\"committed\":true}";
                });
            }
        };
    }
}
