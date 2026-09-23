package com.ustb.seforge.review.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.review.api.ExternalSonarFinding;
import com.ustb.seforge.review.infrastructure.SonarScannerGateway.ProcessScannerRunner;
import com.ustb.seforge.review.infrastructure.SonarScannerGateway.ScannerReport;
import com.ustb.seforge.review.infrastructure.SonarScannerGateway.ServerResult;
import com.ustb.seforge.review.infrastructure.SonarScannerGateway.Settings;
import com.ustb.seforge.review.service.SonarGateway;
import com.ustb.seforge.review.service.SonarGatewayException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SonarScannerGatewayTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void disabledGatewayFailsOnlyWhenInvokedAndDoesNotStartScanner() throws Exception {
        SEForgeProperties properties = new SEForgeProperties();
        AtomicBoolean scannerCalled = new AtomicBoolean();
        SonarScannerGateway gateway = new SonarScannerGateway(properties,
                (settings, request) -> {
                    scannerCalled.set(true);
                    return new ScannerReport("unexpected");
                },
                (settings, projectKey, taskId) -> {
                    throw new AssertionError("Sonar API must not be called");
                });

        SonarGateway.ScanRequest request = request();

        assertThatThrownBy(() -> gateway.analyze(request))
                .isInstanceOf(SonarGatewayException.class)
                .hasMessageContaining("disabled");
        assertThat(scannerCalled).isFalse();
    }

    @Test
    void orchestratesScannerAndAuthoritativeServerResult() throws Exception {
        SEForgeProperties properties = enabledProperties();
        ExternalSonarFinding finding = new ExternalSonarFinding(
                "finding-1", "java:S2095", "BUG", "MAJOR", "project:App.java", 12,
                "Close this resource");
        AtomicReference<String> observedTask = new AtomicReference<>();
        SonarScannerGateway gateway = new SonarScannerGateway(properties,
                (settings, request) -> new ScannerReport("task-1"),
                (settings, projectKey, taskId) -> {
                    observedTask.set(taskId);
                    return new ServerResult("analysis-1", "ERROR", Map.of("bugs", "1"),
                            List.of(finding));
                });

        SonarGateway.Analysis result = gateway.analyze(request());

        assertThat(observedTask).hasValue("task-1");
        assertThat(result.analysisId()).isEqualTo("analysis-1");
        assertThat(result.qualityGate()).isEqualTo("ERROR");
        assertThat(result.findings()).containsExactly(finding);
    }

    @Test
    void serverFailureIsPropagatedInsteadOfBecomingFakeSuccess() throws Exception {
        SEForgeProperties properties = enabledProperties();
        SonarScannerGateway gateway = new SonarScannerGateway(properties,
                (settings, request) -> new ScannerReport("task-1"),
                (settings, projectKey, taskId) -> {
                    throw new SonarGatewayException("compute task ended as FAILED");
                });

        assertThatThrownBy(() -> gateway.analyze(request()))
                .isInstanceOf(SonarGatewayException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void processRunnerUsesOneFixedExecutableTokenAndKeepsTokenOutOfArguments() throws Exception {
        SEForgeProperties properties = enabledProperties();
        properties.getSonar().setScannerExecutable("C:\\Program Files\\sonar-scanner.bat");
        Settings settings = Settings.from(properties.getSonar());
        AtomicReference<ProcessBuilder> launched = new AtomicReference<>();
        ProcessScannerRunner runner = new ProcessScannerRunner(builder -> {
            launched.set(builder);
            Path metadata = builder.command().stream()
                    .filter(value -> value.startsWith("-Dsonar.scanner.metadataFilePath="))
                    .map(value -> Path.of(value.substring(value.indexOf('=') + 1)))
                    .findFirst().orElseThrow();
            Files.writeString(metadata, "ceTaskId=task-safe\n", StandardCharsets.UTF_8);
            return new FakeProcess(true, 0, "scanner completed");
        });

        ScannerReport report = runner.scan(settings, request());

        assertThat(report.computeTaskId()).isEqualTo("task-safe");
        assertThat(launched.get().command().getFirst())
                .isEqualTo("C:\\Program Files\\sonar-scanner.bat");
        assertThat(launched.get().command()).noneMatch(value -> value.contains("secret-token"));
        assertThat(launched.get().environment()).containsEntry("SONAR_TOKEN", "secret-token");
        assertThat(launched.get().directory().toPath()).isEqualTo(temporaryDirectory);
    }

    @Test
    void processTimeoutKillsScannerAndFailsTheReview() throws Exception {
        SEForgeProperties properties = enabledProperties();
        properties.getSonar().setScannerTimeout(Duration.ofMillis(10));
        FakeProcess process = new FakeProcess(false, 0, "still running");
        ProcessScannerRunner runner = new ProcessScannerRunner(builder -> process);

        assertThatThrownBy(() -> runner.scan(Settings.from(properties.getSonar()), request()))
                .isInstanceOf(SonarGatewayException.class)
                .hasMessageContaining("timed out");
        assertThat(process.destroyed).isTrue();
    }

    @Test
    void nonZeroScannerExitIsNeverReportedAsSuccess() throws Exception {
        SEForgeProperties properties = enabledProperties();
        ProcessScannerRunner runner = new ProcessScannerRunner(
                builder -> new FakeProcess(true, 3, "analysis failed"));

        assertThatThrownBy(() -> runner.scan(Settings.from(properties.getSonar()), request()))
                .isInstanceOf(SonarGatewayException.class)
                .hasMessageContaining("code 3")
                .hasMessageContaining("analysis failed");
    }

    @Test
    void missingScannerBinaryFailsExplicitly() throws Exception {
        SEForgeProperties properties = enabledProperties();
        ProcessScannerRunner runner = new ProcessScannerRunner(builder -> {
            throw new IOException("file not found");
        });

        assertThatThrownBy(() -> runner.scan(Settings.from(properties.getSonar()), request()))
                .isInstanceOf(SonarGatewayException.class)
                .hasMessageContaining("install the configured executable");
    }

    @Test
    void healthIndicatorDoesNotProbeExternalService() {
        SEForgeProperties properties = new SEForgeProperties();
        SonarHealthIndicator health = new SonarHealthIndicator(properties);

        assertThat(health.health().getStatus().getCode()).isEqualTo("DEGRADED");

        properties.getSonar().setEnabled(true);
        properties.getSonar().setToken("configured");
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void productionConstructorDoesNotConnectOrStartAProcess() {
        SEForgeProperties properties = new SEForgeProperties();

        assertThat(new SonarScannerGateway(properties, new ObjectMapper())).isNotNull();
    }

    private SonarGateway.ScanRequest request() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("App.java"), "class App {}", StandardCharsets.UTF_8);
        return new SonarGateway.ScanRequest(temporaryDirectory, source, "seforge-review-1-2",
                "SEForge review 2");
    }

    private SEForgeProperties enabledProperties() {
        SEForgeProperties properties = new SEForgeProperties();
        properties.getSonar().setEnabled(true);
        properties.getSonar().setServerUrl("http://sonarqube:9000");
        properties.getSonar().setToken("secret-token");
        properties.getSonar().setScannerExecutable("sonar-scanner");
        return properties;
    }

    private static final class FakeProcess extends Process {
        private final boolean exits;
        private final int exitCode;
        private final InputStream output;
        private boolean destroyed;

        private FakeProcess(boolean exits, int exitCode, String output) {
            this.exits = exits;
            this.exitCode = exitCode;
            this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }

        @Override
        public InputStream getInputStream() { return output; }

        @Override
        public InputStream getErrorStream() { return InputStream.nullInputStream(); }

        @Override
        public int waitFor() { return exitCode; }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) { return exits; }

        @Override
        public int exitValue() { return exitCode; }

        @Override
        public void destroy() { destroyed = true; }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }

        @Override
        public boolean isAlive() { return !exits && !destroyed; }
    }
}
