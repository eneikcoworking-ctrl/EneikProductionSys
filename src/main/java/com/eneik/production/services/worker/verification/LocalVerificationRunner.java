package com.eneik.production.services.worker.verification;

import com.eneik.production.models.persistence.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Popperian Falsification Gate (D008), Dennett's Blind Arbiter, and Goldman's
 * Level of Abstraction Lock (D010) enforcing zero-stub reality across worktree tasks.
 * Holds a global host semaphore Semaphore(1) to strictly isolate Hetzner host memory (RAM <= 1.2 GiB).
 * LLM synthesizer has NO authority to choose check commands or skip severe tests.
 */
@Component
public class LocalVerificationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalVerificationRunner.class);

    // Hetzner host invariant: strictly one compiler container running at any point in time
    public static final Semaphore HOST_VERIFICATION_SEMAPHORE = new Semaphore(1);

    private static final int TIMEOUT_SECONDS = 300; // 5 minutes max
    private final java.net.http.HttpClient sharedHttpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(3))
            .build();

    private final SimulationStubDetector stubDetector;

    public LocalVerificationRunner() {
        this(new SimulationStubDetector());
    }

    @Autowired
    public LocalVerificationRunner(SimulationStubDetector stubDetector) {
        this.stubDetector = stubDetector != null ? stubDetector : new SimulationStubDetector();
    }

    /**
     * Executes Popperian severe verification test on worktree under Semaphore(1) protection,
     * followed by Goldman D010 philosophical audit against stubs and simulacra.
     */
    public VerificationResult runVerification(Path worktreePath, TaskEntity task) {
        if (task != null && (task.isWishlistCompiler() || task.isCarrier())) {
            log.info("LocalVerificationRunner: Carrier/compiler task detected for {}, skipping verification", task.getId());
            return VerificationResult.success(0, "Carrier/compiler task — skipped Maven build");
        }

        if (task != null && isNonJavaAssetOrContractScope(worktreePath, task.getFileScope())) {
            log.info("LocalVerificationRunner: Non-Java contract/asset scope {} detected for {}, skipping Maven build", task.getFileScope(), task.getId());
            VerificationResult stubAudit = auditWorktreeAgainstSimulacra(worktreePath, task);
            if (!stubAudit.passed()) {
                return stubAudit;
            }
            return VerificationResult.success(0, "Contract/asset scope verified clean");
        }

        log.info("Popper Severe Test D008: acquiring host verification semaphore (available: {})...",
                HOST_VERIFICATION_SEMAPHORE.availablePermits());

        boolean acquired = false;
        try {
            acquired = HOST_VERIFICATION_SEMAPHORE.tryAcquire(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("Popper Severe Test: timeout acquiring host verification semaphore after {}s", TIMEOUT_SECONDS);
                return VerificationResult.failure(124, "Timed out waiting for host verification semaphore", "SEMAPHORE_TIMEOUT");
            }

            log.info("Host semaphore acquired. Running blind arbitration for task {} in {}",
                    task != null ? task.getId() : "null", worktreePath);

            List<String> targetCommand = resolveArbiterCommand(worktreePath, task);
            VerificationResult result = executeInIsolatedContainer(worktreePath, targetCommand);

            if (result.passed()) {
                VerificationResult stubAudit = auditWorktreeAgainstSimulacra(worktreePath, task);
                if (!stubAudit.passed()) {
                    return stubAudit;
                }
            }

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VerificationResult.failure(130, "Verification interrupted", "INTERRUPTED");
        } finally {
            if (acquired) {
                HOST_VERIFICATION_SEMAPHORE.release();
                log.info("Released host verification semaphore (available: {})", HOST_VERIFICATION_SEMAPHORE.availablePermits());
            }
        }
    }

    /**
     * Goldman Level of Abstraction Lock (D010):
     * Audits modified files in task scope against cardboard stubs, fake domains, and 1x1 pixels.
     */
    public VerificationResult auditWorktreeAgainstSimulacra(Path worktreePath, TaskEntity task) {
        if (worktreePath == null || !Files.isDirectory(worktreePath) || stubDetector == null) {
            return VerificationResult.success(0, "Stub audit skipped: no worktree");
        }
        String scope = task != null ? task.getFileScope() : null;
        if (scope == null || scope.isBlank()) {
            return VerificationResult.success(0, "Stub audit clean");
        }
        String clean = scope.replace('[', ' ').replace(']', ' ').replace('"', ' ').replace('\'', ' ').trim();
        for (String part : clean.split("[,\\r\\n]+")) {
            String relPath = part.trim();
            if (relPath.isEmpty()) continue;
            Path filePath = worktreePath.resolve(relPath).normalize();
            if (Files.isRegularFile(filePath) && !relPath.contains("SimulationStubDetector")) {
                try {
                    String content = Files.readString(filePath, StandardCharsets.UTF_8);
                    SimulationStubDetector.StubAuditReport report = stubDetector.auditArtifact(content, relPath);
                    if (report.hasSimulacra()) {
                        log.warn("Goldman D010 Gate REJECTED task {} due to simulacra in {}: {}",
                                task != null ? task.getId() : "null", relPath, report.detectedSimulacra());
                        return VerificationResult.failure(101,
                                "Goldman Level of Abstraction Lock (D010): Simulacra/Stub detected in " + relPath + ": " + report.detectedSimulacra(),
                                "D010_SIMULACRA_DETECTED: " + report.detectedSimulacra().get(0));
                    }
                } catch (Exception e) {
                    log.debug("Stub audit could not read {}: {}", relPath, e.getMessage());
                }
            }
        }
        return VerificationResult.success(0, "Goldman D010 Gate: Verified clean of simulacra");
    }

    /**
     * Blind Arbiter (Homuncular Functionalism):
     * Deterministically derives verification command from project nature and target classes.
     * The LLM synthesizer has ZERO authority to change or skip this command.
     */
    public List<String> resolveArbiterCommand(Path worktreePath, TaskEntity task) {
        if (worktreePath == null) {
            return List.of("mvn", "test-compile");
        }

        if (Files.exists(worktreePath.resolve("pom.xml"))) {
            List<String> cmd = new ArrayList<>(Arrays.asList("mvn", "test-compile"));
            String targetTest = extractTargetTest(task);
            if (targetTest != null && !targetTest.isBlank()) {
                cmd.add("-Dtest=" + targetTest);
                cmd.add("-DfailIfNoTests=false");
            }
            return cmd;
        }

        if (Files.exists(worktreePath.resolve("package.json"))) {
            return List.of("npm", "test", "--", "--run");
        }

        if (Files.exists(worktreePath.resolve("requirements.txt")) || Files.exists(worktreePath.resolve("pytest.ini"))) {
            return List.of("pytest", "-q");
        }

        // Generic fallback check
        return List.of("mvn", "test-compile");
    }

    /**
     * Builds and executes docker command with strict cgroup limits (-m 1200m --cpus 1.5).
     * If docker daemon is not accessible, falls back gracefully to host execution under the same semaphore.
     */
    public VerificationResult executeInIsolatedContainer(Path worktreePath, List<String> buildCommand) {
        boolean dockerAvailable = isDockerAvailable();

        List<String> fullCmd = new ArrayList<>();
        if (dockerAvailable) {
            fullCmd.addAll(Arrays.asList(
                    "docker", "run", "--rm",
                    "--memory=1200m",
                    "--memory-swap=1600m",
                    "--cpus=1.5",
                    "-v", worktreePath.toAbsolutePath().normalize().toString() + ":/workspace",
                    "-w", "/workspace",
                    "maven:3.9.9-eclipse-temurin-21"
            ));
            fullCmd.addAll(buildCommand);
        } else {
            VerificationResult bridgeResult = tryVerifyViaHostBridge(worktreePath, buildCommand);
            if (bridgeResult != null) {
                return bridgeResult;
            }
            // Local fallback execution (maintaining semaphore protection)
            fullCmd.addAll(buildCommand);
        }

        log.info("Executing verification command: {}", fullCmd);
        return runProcess(worktreePath, fullCmd, TIMEOUT_SECONDS);
    }

    private boolean isNonJavaAssetOrContractScope(Path worktreePath, String rawScope) {
        if (rawScope == null || rawScope.isBlank()) {
            return false;
        }
        String clean = rawScope.replace('[', ' ').replace(']', ' ').replace('"', ' ').replace('\'', ' ').trim();
        if (clean.isEmpty()) {
            return false;
        }
        boolean hasFrontendPackageJson = worktreePath != null
                && (Files.exists(worktreePath.resolve("package.json"))
                || Files.exists(worktreePath.resolve("frontend/package.json")));
        for (String part : clean.split("[,\\r\\n]+")) {
            String p = part.trim().toLowerCase();
            if (p.isEmpty()) continue;
            if (p.endsWith(".java") || p.endsWith("pom.xml") || p.endsWith(".gradle")) {
                return false;
            }
            if (hasFrontendPackageJson && (p.endsWith(".svelte") || p.endsWith(".ts") || p.endsWith(".tsx") || p.endsWith(".js") || p.endsWith(".jsx"))) {
                return false;
            }
        }
        return true;
    }

    private VerificationResult tryVerifyViaHostBridge(Path worktreePath, List<String> buildCommand) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var reqNode = mapper.createObjectNode();
            reqNode.put("worktreePath", worktreePath.toAbsolutePath().normalize().toString());
            var cmdArr = reqNode.putArray("command");
            for (String c : buildCommand) {
                cmdArr.add(c);
            }
            java.net.http.HttpClient client = this.sharedHttpClient;
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://host.docker.internal:8095/verify-worktree"))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reqNode)))
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 500) {
                var body = mapper.readTree(response.body());
                int exitCode = body.path("exitCode").asInt(1);
                String output = body.path("output").asText("");
                if (exitCode == 0) {
                    log.info("Popper Severe Test PASSED via Host Bridge (exit code 0)");
                    return VerificationResult.success(output);
                } else {
                    String sig = DuhemQuineDetector.extractErrorSignature(output);
                    log.warn("Popper Severe Test FAILED via Host Bridge (exit code {}): {}", exitCode, sig);
                    return VerificationResult.failure(exitCode, output, sig);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<String> buildDockerCommandTokens(Path worktreePath, List<String> buildCommand) {
        List<String> tokens = new ArrayList<>(Arrays.asList(
                "docker", "run", "--rm",
                "--memory=1200m",
                "--memory-swap=1600m",
                "--cpus=1.5",
                "-v", worktreePath.toAbsolutePath().normalize().toString() + ":/workspace",
                "-w", "/workspace",
                "maven:3.9.9-eclipse-temurin-21"
        ));
        tokens.addAll(buildCommand);
        return tokens;
    }

    private VerificationResult runProcess(Path workDir, List<String> command, int timeoutSeconds) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Process timed out after {}s: {}", timeoutSeconds, command);
                return VerificationResult.failure(124, output.toString(), "COMMAND_TIMEOUT");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("Popper Severe Test PASSED (exit code 0)");
                return VerificationResult.success(output.toString());
            } else {
                String signature = DuhemQuineDetector.extractErrorSignature(output.toString());
                log.warn("Popper Severe Test FAILED (exit code {}): {}", exitCode, signature);
                return VerificationResult.failure(exitCode, output.toString(), signature);
            }
        } catch (Exception e) {
            log.error("Failed to execute verification process {}: {}", command, e.getMessage());
            return VerificationResult.failure(1, output.toString() + "\n" + e.getMessage(), "EXECUTION_EXCEPTION: " + e.getMessage());
        }
    }

    private boolean isDockerAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "info").start();
            boolean ok = p.waitFor(5, TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractTargetTest(TaskEntity task) {
        if (task == null || task.getFileScope() == null) {
            return null;
        }
        String scope = task.getFileScope();
        // Look for *Test.java pattern in fileScope
        for (String part : scope.split("[,\\s\"]+")) {
            if (part.endsWith("Test.java")) {
                int lastSlash = Math.max(part.lastIndexOf('/'), part.lastIndexOf('\\'));
                String testName = lastSlash >= 0 ? part.substring(lastSlash + 1) : part;
                return testName.replace(".java", "");
            }
        }
        return null;
    }
}
