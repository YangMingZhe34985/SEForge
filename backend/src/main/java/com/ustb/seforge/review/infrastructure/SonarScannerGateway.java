package com.ustb.seforge.review.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.review.api.ExternalSonarFinding;
import com.ustb.seforge.review.service.SonarGateway;
import com.ustb.seforge.review.service.SonarGatewayException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Runs a pre-installed SonarScanner CLI and then reads the authoritative result from SonarQube.
 * It never invokes a shell or a student build command. Scanner and server failures are propagated,
 * so the surrounding durable job can retry and eventually dead-letter instead of reporting success.
 */
@Component
public class SonarScannerGateway implements SonarGateway {
    private static final int MAX_API_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> TERMINAL_FAILURES = Set.of("FAILED", "CANCELED");
    private final SEForgeProperties properties;
    private final ScannerRunner scanner;
    private final SonarClient sonar;

    @Autowired
    public SonarScannerGateway(SEForgeProperties properties, ObjectMapper objectMapper) {
        this(properties, new ProcessScannerRunner(), new HttpSonarClient(objectMapper));
    }

    SonarScannerGateway(SEForgeProperties properties, ScannerRunner scanner, SonarClient sonar) {
        this.properties = properties;
        this.scanner = scanner;
        this.sonar = sonar;
    }

    @Override
    public Analysis analyze(ScanRequest request) {
        Settings settings = Settings.from(properties.getSonar());
        if (!settings.enabled()) {
            throw new SonarGatewayException("SonarQube code review is disabled");
        }
        ScannerReport report = scanner.scan(settings, request);
        ServerResult result = sonar.awaitAndRead(settings, request.projectKey(), report.computeTaskId());
        return new Analysis(request.projectKey(), report.computeTaskId(), result.analysisId(),
                result.qualityGate(), result.measures(), result.findings());
    }

    interface ScannerRunner {
        ScannerReport scan(Settings settings, ScanRequest request);
    }

    interface SonarClient {
        ServerResult awaitAndRead(Settings settings, String projectKey, String computeTaskId);
    }

    @FunctionalInterface
    interface ProcessLauncher {
        Process start(ProcessBuilder builder) throws IOException;
    }

    record ScannerReport(String computeTaskId) {
    }

    record ServerResult(
            String analysisId,
            String qualityGate,
            Map<String, String> measures,
            List<ExternalSonarFinding> findings) {
    }

    record Settings(
            boolean enabled,
            URI serverUri,
            String token,
            String scannerExecutable,
            Duration scannerTimeout,
            Duration computeTimeout,
            Duration apiTimeout,
            Duration pollInterval,
            int maxFindings,
            int maxProcessOutputBytes) {

        static Settings from(SEForgeProperties.Sonar source) {
            if (!source.isEnabled()) {
                return new Settings(false, URI.create("http://localhost/"), "", "", Duration.ZERO,
                        Duration.ZERO, Duration.ZERO, Duration.ZERO, 1, 4_096);
            }
            URI server = serverUri(source.getServerUrl());
            String token = required(source.getToken(), "SonarQube token is not configured");
            String executable = required(source.getScannerExecutable(),
                    "SonarScanner executable is not configured");
            Duration scannerTimeout = positive(source.getScannerTimeout(), "scanner timeout");
            Duration computeTimeout = positive(source.getComputeTimeout(), "compute timeout");
            Duration apiTimeout = positive(source.getApiTimeout(), "API timeout");
            Duration pollInterval = positive(source.getPollInterval(), "poll interval");
            if (source.getMaxFindings() < 1 || source.getMaxFindings() > 2_000) {
                throw new SonarGatewayException("Sonar max-findings must be between 1 and 2000");
            }
            if (source.getMaxProcessOutputBytes() < 4_096
                    || source.getMaxProcessOutputBytes() > 1_048_576) {
                throw new SonarGatewayException(
                        "Sonar max-process-output-bytes must be between 4096 and 1048576");
            }
            return new Settings(true, server, token, executable, scannerTimeout, computeTimeout,
                    apiTimeout, pollInterval, source.getMaxFindings(),
                    source.getMaxProcessOutputBytes());
        }

        private static URI serverUri(String value) {
            String configured = required(value, "SonarQube server URL is not configured");
            try {
                URI parsed = URI.create(configured.endsWith("/") ? configured : configured + "/");
                if (!("http".equalsIgnoreCase(parsed.getScheme())
                        || "https".equalsIgnoreCase(parsed.getScheme()))
                        || parsed.getHost() == null || parsed.getUserInfo() != null
                        || parsed.getQuery() != null || parsed.getFragment() != null) {
                    throw new IllegalArgumentException();
                }
                return parsed;
            } catch (IllegalArgumentException exception) {
                throw new SonarGatewayException("SonarQube server URL must be an HTTP(S) origin");
            }
        }

        private static String required(String value, String message) {
            if (value == null || value.isBlank() || value.indexOf('\0') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new SonarGatewayException(message);
            }
            return value.trim();
        }

        private static Duration positive(Duration value, String label) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new SonarGatewayException("Sonar " + label + " must be positive");
            }
            return value;
        }
    }

    static final class ProcessScannerRunner implements ScannerRunner {
        private static final Set<String> ALLOWED_ENVIRONMENT = Set.of(
                "PATH", "JAVA_HOME", "JRE_HOME", "HOME", "USERPROFILE", "SYSTEMROOT",
                "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP", "LANG", "LC_ALL",
                "SONAR_USER_HOME");
        private static final String EXCLUSIONS = String.join(",",
                "**/.git/**", "**/.svn/**", "**/.hg/**", "**/node_modules/**",
                "**/.scannerwork/**",
                "**/target/**", "**/build/**", "**/dist/**", "**/*.class", "**/*.jar",
                "**/*.war", "**/*.ear", "**/*.exe", "**/*.dll", "**/*.so", "**/*.dylib");
        private final ProcessLauncher launcher;

        ProcessScannerRunner() {
            this(ProcessBuilder::start);
        }

        ProcessScannerRunner(ProcessLauncher launcher) {
            this.launcher = launcher;
        }

        @Override
        public ScannerReport scan(Settings settings, ScanRequest request) {
            Path workspace = request.workspaceDirectory().toAbsolutePath().normalize();
            Path source = request.sourceDirectory().toAbsolutePath().normalize();
            if (!source.startsWith(workspace) || !Files.isDirectory(source)) {
                throw new SonarGatewayException("Sonar source directory is outside its workspace");
            }
            Path control = workspace.resolve(".sonar-control");
            Path work = source.resolve(".scannerwork");
            Path emptyBinaries = control.resolve("empty-binaries");
            Path metadata = control.resolve("report-task.txt");
            try {
                Files.createDirectories(work);
                Files.createDirectories(emptyBinaries);
                Files.writeString(workspace.resolve("sonar-project.properties"),
                        "# Generated by SEForge; student scanner configuration is not used.\n",
                        StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new SonarGatewayException("Could not prepare the SonarScanner workspace", exception);
            }

            List<String> command = new ArrayList<>();
            command.add(settings.scannerExecutable());
            command.add("-Dsonar.projectKey=" + request.projectKey());
            command.add("-Dsonar.projectName=" + request.projectName());
            command.add("-Dsonar.projectBaseDir=" + source);
            command.add("-Dsonar.sources=.");
            command.add("-Dsonar.host.url=" + settings.serverUri());
            command.add("-Dsonar.scanner.metadataFilePath=" + metadata);
            command.add("-Dsonar.working.directory=" + work);
            command.add("-Dsonar.scm.disabled=true");
            command.add("-Dsonar.sourceEncoding=UTF-8");
            // Java bytecode is deliberately not built: compiling an untrusted submission can run
            // annotation processors. An empty controlled directory enables source-only analysis.
            command.add("-Dsonar.java.binaries=" + emptyBinaries);
            command.add("-Dsonar.exclusions=" + EXCLUSIONS);

            ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile())
                    .redirectErrorStream(true);
            isolateEnvironment(builder.environment(), settings, control);
            Process process;
            try {
                process = launcher.start(builder);
            } catch (IOException exception) {
                throw new SonarGatewayException(
                        "SonarScanner could not be started; install the configured executable", exception);
            }
            OutputCollector output = new OutputCollector(process.getInputStream(),
                    settings.maxProcessOutputBytes());
            Thread reader = Thread.ofVirtual().name("sonar-scanner-output").start(output);
            boolean exited;
            try {
                exited = process.waitFor(settings.scannerTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                terminate(process);
                throw new SonarGatewayException("SonarScanner was interrupted", exception);
            }
            if (!exited) {
                terminate(process);
                join(reader);
                throw new SonarGatewayException("SonarScanner timed out after "
                        + settings.scannerTimeout());
            }
            join(reader);
            output.throwIfFailed();
            if (process.exitValue() != 0) {
                throw new SonarGatewayException("SonarScanner exited with code "
                        + process.exitValue() + ": " + output.text());
            }
            return new ScannerReport(readTaskId(metadata));
        }

        private void isolateEnvironment(Map<String, String> environment, Settings settings,
                                        Path controlDirectory) {
            Map<String, String> inherited = new HashMap<>(environment);
            environment.clear();
            inherited.forEach((key, value) -> {
                if (ALLOWED_ENVIRONMENT.stream().anyMatch(name -> name.equalsIgnoreCase(key))) {
                    environment.put(key, value);
                }
            });
            environment.put("SONAR_HOST_URL", settings.serverUri().toString());
            environment.put("SONAR_TOKEN", settings.token());
            environment.putIfAbsent("SONAR_USER_HOME",
                    controlDirectory.resolve("user-home").toString());
        }

        private String readTaskId(Path metadata) {
            if (!Files.isRegularFile(metadata)) {
                throw new SonarGatewayException(
                        "SonarScanner completed without report-task metadata");
            }
            Properties values = new Properties();
            try (var reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) {
                values.load(reader);
            } catch (IOException exception) {
                throw new SonarGatewayException("Could not read SonarScanner task metadata", exception);
            }
            String taskId = values.getProperty("ceTaskId");
            if (taskId == null || !taskId.matches("[A-Za-z0-9_-]{1,200}")) {
                throw new SonarGatewayException("SonarScanner returned an invalid compute task id");
            }
            return taskId;
        }

        private static void terminate(Process process) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        private static void join(Thread reader) {
            try {
                reader.join(Duration.ofSeconds(5));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SonarGatewayException("Interrupted while reading SonarScanner output", exception);
            }
        }
    }

    static final class HttpSonarClient implements SonarClient {
        private static final String METRICS = String.join(",", "bugs", "vulnerabilities",
                "code_smells", "complexity", "cognitive_complexity", "duplicated_lines_density",
                "ncloc");
        private final ObjectMapper objectMapper;

        HttpSonarClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public ServerResult awaitAndRead(Settings settings, String projectKey, String computeTaskId) {
            HttpClient client = HttpClient.newBuilder().connectTimeout(settings.apiTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            String analysisId = awaitComputeTask(client, settings, computeTaskId);
            List<ExternalSonarFinding> findings = new ArrayList<>(issues(client, settings, projectKey));
            findings.addAll(hotspots(client, settings, projectKey));
            if (findings.size() > settings.maxFindings()) throw new SonarGatewayException("Combined Sonar findings exceed safe limit");
            Map<String, String> measures = measures(client, settings, projectKey);
            String qualityGate = qualityGate(client, settings, analysisId);
            return new ServerResult(analysisId, qualityGate, measures, findings);
        }

        private String awaitComputeTask(HttpClient client, Settings settings, String taskId) {
            long deadline = System.nanoTime() + settings.computeTimeout().toNanos();
            while (System.nanoTime() < deadline) {
                JsonNode task = get(client, settings, "api/ce/task?id=" + encode(taskId)).path("task");
                String status = task.path("status").asText("").toUpperCase(Locale.ROOT);
                if ("SUCCESS".equals(status)) {
                    String analysisId = task.path("analysisId").asText("");
                    if (analysisId.isBlank()) {
                        throw new SonarGatewayException(
                                "SonarQube completed without an analysis identifier");
                    }
                    return analysisId;
                }
                if (TERMINAL_FAILURES.contains(status)) {
                    throw new SonarGatewayException("SonarQube compute task ended as " + status);
                }
                if (!("PENDING".equals(status) || "IN_PROGRESS".equals(status))) {
                    throw new SonarGatewayException("SonarQube returned unknown compute status: "
                            + (status.isBlank() ? "<empty>" : status));
                }
                sleep(settings.pollInterval());
            }
            throw new SonarGatewayException("SonarQube compute task timed out after "
                    + settings.computeTimeout());
        }

        private List<ExternalSonarFinding> issues(HttpClient client, Settings settings,
                                                  String projectKey) {
            int pageSize = Math.min(500, settings.maxFindings());
            int page = 1;
            int expected = -1;
            List<ExternalSonarFinding> findings = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            do {
                JsonNode response = get(client, settings, "api/issues/search?componentKeys="
                        + encode(projectKey) + "&resolved=false&ps=" + pageSize + "&p=" + page);
                int total = response.path("paging").path("total")
                        .asInt(response.path("total").asInt(-1));
                if (total < 0) throw new SonarGatewayException("SonarQube issues response has no total");
                if (total > settings.maxFindings()) {
                    throw new SonarGatewayException("SonarQube returned " + total
                            + " findings, above the configured safe limit of "
                            + settings.maxFindings());
                }
                if (expected < 0) expected = total;
                if (expected != total) {
                    throw new SonarGatewayException("SonarQube issue count changed during pagination");
                }
                JsonNode issues = response.path("issues");
                if (!issues.isArray()) {
                    throw new SonarGatewayException("SonarQube issues response is malformed");
                }
                for (JsonNode issue : issues) {
                    ExternalSonarFinding finding = finding(issue);
                    if (!keys.add(finding.findingKey())) {
                        throw new SonarGatewayException("SonarQube returned duplicate finding keys");
                    }
                    findings.add(finding);
                }
                if (issues.size() == 0 && findings.size() < expected) {
                    throw new SonarGatewayException("SonarQube issue pagination was incomplete");
                }
                page++;
            } while (findings.size() < expected);
            if (findings.size() != expected) {
                throw new SonarGatewayException("SonarQube issue pagination was incomplete");
            }
            return List.copyOf(findings);
        }

        private ExternalSonarFinding finding(JsonNode issue) {
            String key = required(issue, "key", "finding key", 100);
            String rule = required(issue, "rule", "finding rule", 150);
            String type = optional(issue, "type", "CODE_SMELL", 32);
            String severity = optional(issue, "severity", "UNKNOWN", 32);
            String component = required(issue, "component", "finding component", 500);
            String message = required(issue, "message", "finding message", 2_000);
            Integer line = issue.hasNonNull("line") ? Math.max(0, issue.path("line").asInt()) : null;
            return new ExternalSonarFinding(key, rule, type, severity, component, line, message);
        }

        private List<ExternalSonarFinding> hotspots(HttpClient client, Settings settings, String projectKey) {
            List<ExternalSonarFinding> findings = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            int expected = -1;
            for (int page = 1; ; page++) {
                JsonNode response = get(client, settings, "api/hotspots/search?projectKey=" + encode(projectKey) + "&ps=100&p=" + page);
                int total = response.path("paging").path("total").asInt(-1);
                if (total < 0 || total > settings.maxFindings() || (expected >= 0 && total != expected)
                        || !response.path("hotspots").isArray()) throw new SonarGatewayException("SonarQube hotspot pagination is invalid");
                expected = total;
                for (JsonNode hotspot : response.path("hotspots")) {
                    String key = required(hotspot, "key", "hotspot key", 100);
                    if (!keys.add(key)) throw new SonarGatewayException("SonarQube returned duplicate hotspot keys");
                    JsonNode detail = get(client, settings, "api/hotspots/show?hotspot=" + encode(key));
                    findings.add(new ExternalSonarFinding(key, required(detail.path("rule"), "key", "hotspot rule", 150),
                            "SECURITY_HOTSPOT", required(hotspot, "vulnerabilityProbability", "hotspot priority", 32),
                            required(hotspot, "component", "hotspot component", 500),
                            hotspot.hasNonNull("line") ? hotspot.path("line").asInt() : null,
                            required(hotspot, "message", "hotspot message", 2000)));
                }
                if (findings.size() == expected) return List.copyOf(findings);
                if (response.path("hotspots").isEmpty() || findings.size() > expected) throw new SonarGatewayException("SonarQube hotspot pagination was incomplete");
            }
        }

        private Map<String, String> measures(HttpClient client, Settings settings, String projectKey) {
            JsonNode response = get(client, settings, "api/measures/component?component="
                    + encode(projectKey) + "&metricKeys=" + encode(METRICS));
            JsonNode values = response.path("component").path("measures");
            if (!values.isArray()) throw new SonarGatewayException("SonarQube measures response is malformed");
            Map<String, String> result = new LinkedHashMap<>();
            for (JsonNode value : values) {
                String metric = value.path("metric").asText("");
                String measurement = value.path("value").asText("");
                if (!metric.isBlank() && !measurement.isBlank()) result.put(metric, measurement);
            }
            return Map.copyOf(result);
        }

        private String qualityGate(HttpClient client, Settings settings, String analysisId) {
            JsonNode response = get(client, settings, "api/qualitygates/project_status?analysisId="
                    + encode(analysisId));
            String status = response.path("projectStatus").path("status").asText("");
            if (status.isBlank()) {
                throw new SonarGatewayException("SonarQube quality-gate response is malformed");
            }
            return bounded(status.toUpperCase(Locale.ROOT), 32);
        }

        private JsonNode get(HttpClient client, Settings settings, String relativePath) {
            URI uri = settings.serverUri().resolve(relativePath);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(settings.apiTimeout())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + settings.token()).GET().build();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SonarGatewayException("SonarQube API call was interrupted", exception);
            } catch (IOException exception) {
                throw new SonarGatewayException("SonarQube API is unavailable", exception);
            }
            String body;
            try (InputStream input = response.body()) {
                byte[] bytes = input.readNBytes(MAX_API_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_API_RESPONSE_BYTES) {
                    throw new SonarGatewayException("SonarQube API response exceeded 2 MB");
                }
                body = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new SonarGatewayException("Could not read SonarQube API response", exception);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SonarGatewayException("SonarQube API returned HTTP "
                        + response.statusCode() + ": " + bounded(body, 500));
            }
            try {
                return objectMapper.readTree(body);
            } catch (IOException exception) {
                throw new SonarGatewayException("SonarQube API returned invalid JSON", exception);
            }
        }

        private String required(JsonNode node, String field, String label, int maximum) {
            String value = node.path(field).asText("").trim();
            if (value.isBlank()) throw new SonarGatewayException("SonarQube " + label + " is missing");
            if (value.length() > maximum) {
                throw new SonarGatewayException("SonarQube " + label + " exceeds the safe limit");
            }
            return value;
        }

        private String optional(JsonNode node, String field, String fallback, int maximum) {
            String value = node.path(field).asText("").trim();
            String normalized = value.isBlank() ? fallback : value;
            if (normalized.length() > maximum) {
                throw new SonarGatewayException("SonarQube " + field + " exceeds the safe limit");
            }
            return normalized;
        }

        private static void sleep(Duration duration) {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SonarGatewayException("SonarQube polling was interrupted", exception);
            }
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }

    private static final class OutputCollector implements Runnable {
        private final InputStream input;
        private final int maximum;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final AtomicReference<IOException> failure = new AtomicReference<>();
        private boolean truncated;

        private OutputCollector(InputStream input, int maximum) {
            this.input = input;
            this.maximum = maximum;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            try (input) {
                for (int read; (read = input.read(buffer)) != -1; ) {
                    int remaining = maximum - captured.size();
                    if (remaining > 0) captured.write(buffer, 0, Math.min(remaining, read));
                    if (read > remaining) truncated = true;
                }
            } catch (IOException exception) {
                failure.set(exception);
            }
        }

        private String text() {
            String value = captured.toString(StandardCharsets.UTF_8).trim();
            return value + (truncated ? " [output truncated]" : "");
        }

        private void throwIfFailed() {
            IOException exception = failure.get();
            if (exception != null) {
                throw new SonarGatewayException("Could not read SonarScanner output", exception);
            }
        }
    }
}
