package com.ustb.seforge.job.runtime;

import static org.assertj.core.api.Assertions.*;
import com.ustb.seforge.job.domain.*;
import com.ustb.seforge.job.infrastructure.*;
import com.ustb.seforge.job.service.*;
import com.ustb.seforge.job.repository.AsyncJobRepository;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobRuntimeIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.4")
            .withCommand("--log-bin-trust-function-creators=1")
            .withDatabaseName("seforge_phase2_test").withUsername("phase2_test").withPassword("phase2-test-only-password");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2-alpine")
            .withExposedPorts(6379).withCommand("/usr/local/bin/redis-server", "--save", "", "--appendonly", "no",
                    "--requirepass", "phase2-test-only-redis", "--enable-debug-command", "yes");
    private ConfigurableApplicationContext context;
    private AsyncJobService jobs;
    private RedisOutboxPublisher publisher;
    private JdbcTemplate jdbc;
    private StringRedisTemplate redis;
    private TransactionTemplate tx;
    private Process worker;
    private String[] arguments;

    @BeforeAll void setup() {
        arguments = new String[] {"--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                "--spring.datasource.username=" + MYSQL.getUsername(), "--spring.datasource.password=" + MYSQL.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(), "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--spring.data.redis.password=phase2-test-only-redis"};
        context = PhaseTwoProcess.start(arguments);
        jobs = context.getBean(AsyncJobService.class); publisher = context.getBean(RedisOutboxPublisher.class);
        jdbc = context.getBean(JdbcTemplate.class); redis = context.getBean(StringRedisTemplate.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jdbc.execute("create table phase2_probe_result(job_id bigint primary key)");
        jdbc.update("insert into users(id,email,username,password_hash) values (7,'phase2@example.invalid','phase2','not-a-login-hash')");
        jdbc.update("insert into semesters(id,code,name,starts_on,ends_on,status) values(1,'p2','p2','2026-01-01','2026-12-31','ACTIVE')");
        jdbc.update("insert into courses(id,code,name,semester_id,owner_id) values(9,'p2','p2',1,7)");
        jdbc.update("insert into course_members(course_id,user_id,role) values(9,7,'TEACHER')");
    }

    @AfterEach void stopWorker() throws Exception {
        if (worker != null && worker.isAlive()) { worker.destroyForcibly(); assertThat(worker.waitFor(15, TimeUnit.SECONDS)).isTrue(); }
        worker = null;
    }
    @AfterAll void close() { if (context != null) context.close(); }

    @Test void commitPrecedesPublishingAndDuplicateMessagesProduceOneResult() throws Exception {
        ExecutorService concurrent = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<Future<?>> publishing = new java.util.concurrent.atomic.AtomicReference<>();
        long id;
        try {
            id = tx.execute(status -> {
                long pending = submit("normal");
                publishing.set(concurrent.submit(publisher::publishPending));
                try { Thread.sleep(200); } catch (InterruptedException e) { throw new AssertionError(e); }
                assertThat(jobs.require(pending).status()).isEqualTo(JobStatus.PENDING);
                var records = redis.opsForStream().range("seforge:jobs", org.springframework.data.domain.Range.unbounded());
                assertThat(records == null || records.stream().noneMatch(record ->
                        Long.toString(pending).equals(record.getValue().get("jobId")))).isTrue();
                // No outbox record for this uncommitted job is visible to another transaction.
                assertThat(jdbc.queryForObject("select status from outbox_event where aggregate_id=?", String.class, pending)).isEqualTo("PENDING");
                return pending;
            });
            publishing.get().get(10, TimeUnit.SECONDS);
        } finally { concurrent.shutdownNow(); }
        publisher.publishPending(); publisher.publishPending();
        redis.opsForStream().add("seforge:jobs", Map.of("jobId", Long.toString(id)));
        startWorker(); await(() -> jobs.require(id).status() == JobStatus.COMPLETED);
        assertThat(resultCount(id)).isEqualTo(1);
        assertThat(jobs.require(id).attempts()).isEqualTo(1);
        jobs.recoverStalled(Instant.now().plusSeconds(10));
        assertThat(resultCount(id)).isEqualTo(1);
        Long rolledBack = tx.execute(status -> { long value = submit("rollback"); status.setRollbackOnly(); return value; });
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where aggregate_id=?", Integer.class, rolledBack)).isZero();
    }

    @Test void killedWorkerRecoversExpiredLeaseAndHeartbeatsProtectLiveWorker() throws Exception {
        long id = submit("slow"); publisher.publishPending(); startWorker();
        await(() -> jobs.require(id).status() == JobStatus.RUNNING);
        Thread.sleep(3500); // longer than the lease: the real worker heartbeat must extend it
        assertThat(jobs.require(id).status()).isEqualTo(JobStatus.RUNNING);
        assertThat(jobs.recoverStalled(Instant.now())).isZero();
        stopWorker(); Thread.sleep(3200);
        assertThat(jobs.recoverStalled(Instant.now())).isEqualTo(1);
        assertThat(jobs.recoverStalled(Instant.now())).isZero();
        Thread.sleep(2200); publisher.publishPending(); startWorker();
        await(() -> jobs.require(id).status() == JobStatus.COMPLETED);
        assertThat(jobs.require(id).attempts()).isEqualTo(2);
        assertThat(resultCount(id)).isEqualTo(1);
    }

    @Test void redisRestartLosesStreamButDatabaseRecoveryAndRunningWorkerRecreateIt() throws Exception {
        long id = submit("normal"); publisher.publishPending();
        restartRedis();
        await(this::redisReady);
        assertThat(redis.hasKey("seforge:jobs")).isFalse();
        startWorker(); // first restart: no stream/group exists
        Thread.sleep(1200);
        jobs.recoverStalled(Instant.now()); publisher.publishPending();
        await(() -> jobs.require(id).status() == JobStatus.COMPLETED);
        assertThat(resultCount(id)).isEqualTo(1);
        // Also lose the stream/group while the same worker process remains alive.
        restartRedis();
        await(this::redisReady);
        long next = submit("normal"); publisher.publishPending();
        await(() -> jobs.require(next).status() == JobStatus.COMPLETED);
        assertThat(resultCount(next)).isEqualTo(1);
    }

    @Test void cancelBeatsLateCompletionAndRepeatedFailureDeadLetters() throws Exception {
        long cancelled = submit("slow"); publisher.publishPending(); startWorker();
        await(() -> jobs.require(cancelled).status() == JobStatus.RUNNING);
        jobs.cancel(cancelled, 7L);
        await(() -> jobs.require(cancelled).status() == JobStatus.CANCELLED);
        Thread.sleep(5200);
        assertThat(resultCount(cancelled)).isZero();
        long failed = submit("fail"); publisher.publishPending();
        for (int attempt = 1; attempt <= 3; attempt++) {
            int expected = attempt;
            await(() -> jobs.require(failed).attempts() == expected && jobs.require(failed).status() != JobStatus.RUNNING);
            if (attempt < 3) { Thread.sleep((1L << attempt) * 1000 + 150); publisher.publishPending(); }
        }
        assertThat(jobs.require(failed).status()).isEqualTo(JobStatus.DEAD_LETTER);
        assertThat(resultCount(failed)).isZero();
    }

    private long submit(String mode) { return jobs.submit(JobKind.REVIEW_DOCUMENT, 7L, 9L, Map.of("mode", mode), UUID.randomUUID().toString()).id(); }
    private boolean redisReady() {
        try (var connection = redis.getConnectionFactory().getConnection()) { return "PONG".equals(connection.ping()); }
        catch (Exception e) { return false; }
    }
    private void restartRedis() throws Exception {
        // Redis execs a fresh server process with persistence disabled, losing all
        // stream/group state. Keep the container endpoint stable: Docker Desktop
        // can reallocate ephemeral published ports on a container stop/start.
        String previous = redisRunId();
        assertThat(previous).isNotBlank();
        var restart = REDIS.execInContainer("redis-cli", "-a", "phase2-test-only-redis", "DEBUG", "RESTART", "0");
        assertThat(restart.getStdout()).doesNotContain("ERR");
        await(() -> {
            try { String current = redisRunId(); return current != null && !previous.equals(current); }
            catch (RuntimeException unavailable) { return false; }
        });
    }
    private String redisRunId() {
        try (var connection = redis.getConnectionFactory().getConnection()) {
            return connection.serverCommands().info("server").getProperty("run_id");
        }
    }
    private int resultCount(long id) { return jdbc.queryForObject("select count(*) from phase2_probe_result where job_id=?", Integer.class, id); }
    private void startWorker() throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp"); command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(PhaseTwoProcess.class.getName()); command.addAll(List.of(arguments)); command.add("--spring.profiles.active=worker");
        Path log = Path.of("target", "phase2-worker-" + UUID.randomUUID() + ".log");
        worker = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (worker.isAlive() && System.nanoTime() < deadline) {
            if (Files.readString(log).contains("PHASE2_WORKER_READY")) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Worker did not become ready: " + Files.readString(log));
    }
    private void await(BooleanSupplier predicate) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (!predicate.getAsBoolean() && System.nanoTime() < deadline) {
            if (worker != null && !worker.isAlive()) throw new AssertionError("Worker exited; inspect target/phase2-worker-*.log");
            Thread.sleep(100);
        }
        assertThat(predicate.getAsBoolean()).as("Phase 2 runtime condition").isTrue();
    }
}
