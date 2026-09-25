package com.ustb.seforge.review.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.review.service.SonarGateway;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/** Real scanner/server on an internal-only network. No production credentials or database. */
final class PhaseFiveSonarFixture implements AutoCloseable {
    final Network network = Network.builder().createNetworkCmdModifier(c -> c.withInternal(true)).build();
    final Network management = Network.newNetwork();
    final GenericContainer<?> sonar = new GenericContainer<>("sonarqube:10.7.0-community")
            .withNetwork(management).withNetworkAliases("sonar").withExposedPorts(9000)
            .withEnv("SONAR_ES_BOOTSTRAP_CHECKS_DISABLE", "true")
            .withEnv("SONAR_WEB_JAVAOPTS", "-Xms128m -Xmx512m")
            .withEnv("SONAR_CE_JAVAOPTS", "-Xms128m -Xmx512m")
            .waitingFor(Wait.forHttp("/api/system/status").forResponsePredicate(b -> b.contains("\"status\":\"UP\"")))
            .withStartupTimeout(Duration.ofMinutes(5));
    final GenericContainer<?> scanner = new GenericContainer<>("sonarsource/sonar-scanner-cli:12.1.0.3233_8.0.1")
            .withNetwork(network).withCreateContainerCmdModifier(c -> c.withEntrypoint("/bin/sh").withUser("0"))
            .withCommand("-c", "sleep infinity");
    SonarGateway gateway;
    Path lastWorkspace;

    void start() throws Exception {
        sonar.start();
        DockerClientFactory.instance().client().connectToNetworkCmd().withNetworkId(network.getId())
                .withContainerId(sonar.getContainerId()).withContainerNetwork(new com.github.dockerjava.api.model.ContainerNetwork().withAliases("sonar")).exec();
        scanner.start();
        String url = "http://" + sonar.getHost() + ":" + sonar.getMappedPort(9000);
        var tokenResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url + "/api/user_tokens/generate"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("name=phase5-test-only")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        String token = new ObjectMapper().readTree(tokenResponse.body()).path("token").asText();
        SEForgeProperties settings = new SEForgeProperties();
        settings.getSonar().setEnabled(true); settings.getSonar().setServerUrl(url); settings.getSonar().setToken(token);
        settings.getSonar().setScannerTimeout(Duration.ofMinutes(4));
        gateway = new SonarScannerGateway(settings, new SonarScannerGateway.ProcessScannerRunner(builder -> {
            Path root = builder.directory().toPath(); lastWorkspace = root;
            // Host Windows paths are not valid Linux scanner paths. Translate only this
            // application-created workspace; keep cleanup confined to its /tmp child.
            String directoryName = root.getFileName().toString();
            if (!directoryName.matches("seforge-sonar-[A-Za-z0-9-]+")) throw new IOException("Unexpected scanner workspace");
            String remoteRoot = "/tmp/" + directoryName;
            scanner.copyFileToContainer(MountableFile.forHostPath(root), remoteRoot);
            List<String> command = new ArrayList<>(List.of("env", "SONAR_TOKEN=" + token, "SONAR_HOST_URL=http://sonar:9000"));
            builder.command().forEach(arg -> command.add(arg.startsWith("-Dsonar.host.url=")
                    ? "-Dsonar.host.url=http://sonar:9000"
                    : arg.contains(root.toString()) ? arg.replace(root.toString(), remoteRoot).replace('\\', '/') : arg));
            command.add("-Dsonar.scanner.skipJreProvisioning=true");
            CompletableFuture<Container.ExecResult> result = CompletableFuture.supplyAsync(() -> {
                try {
                    var execution = scanner.execInContainer(command.toArray(String[]::new));
                    Files.writeString(Path.of("target", "phase5-sonar-scanner.log"), execution.getStdout() + execution.getStderr());
                    if (execution.getExitCode() == 0) scanner.copyFileFromContainer(remoteRoot + "/.sonar-control/report-task.txt", root.resolve(".sonar-control/report-task.txt").toString());
                    return execution;
                } catch (Exception e) { throw new CompletionException(e); }
                finally {
                    // Root is created by the application, never from an archive entry or user input.
                    try { scanner.execInContainer("rm", "-r", "--", remoteRoot); } catch (Exception e) { throw new CompletionException(e); }
                }
            });
            return new RemoteProcess(result);
        }), new SonarScannerGateway.HttpSonarClient(new ObjectMapper()));
    }

    void assertNetworkIsolation() throws Exception {
        var inspected = DockerClientFactory.instance().client().inspectNetworkCmd().withNetworkId(network.getId()).exec();
        assertThat(inspected.getInternal()).isTrue();
        assertThat(scanner.getContainerInfo().getNetworkSettings().getNetworks()).hasSize(1);
        var local = scanner.execInContainer("curl", "--noproxy", "*", "-fsS", "--max-time", "10", "http://sonar:9000/api/system/status");
        assertThat(local.getExitCode()).isZero();
        var publicIp = scanner.execInContainer("curl", "--noproxy", "*", "-sS", "--connect-timeout", "2", "--max-time", "3", "http://1.1.1.1");
        assertThat(publicIp.getExitCode()).isNotZero();
        Files.writeString(Path.of("target/phase5-scanner-network.txt"), "internal=true; networks=1; sonar reachable; public 1.1.1.1 connection blocked; exit=" + publicIp.getExitCode());
    }
    @Override public void close() { scanner.close(); sonar.close(); network.close(); management.close(); }

    private static final class RemoteProcess extends Process {
        final CompletableFuture<Container.ExecResult> result;
        RemoteProcess(CompletableFuture<Container.ExecResult> result) { this.result = result; }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { var r = result.join(); return new ByteArrayInputStream((r.getStdout() + r.getStderr()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() throws InterruptedException { try { return result.get().getExitCode(); } catch (ExecutionException e) { throw new IllegalStateException(e); } }
        @Override public boolean waitFor(long value, TimeUnit unit) throws InterruptedException { try { result.get(value, unit); return true; } catch (TimeoutException e) { return false; } catch (ExecutionException e) { throw new IllegalStateException(e); } }
        @Override public int exitValue() { if (!result.isDone()) throw new IllegalThreadStateException(); return result.join().getExitCode(); }
        @Override public void destroy() { result.cancel(true); }
        @Override public boolean isAlive() { return !result.isDone(); }
    }
}
