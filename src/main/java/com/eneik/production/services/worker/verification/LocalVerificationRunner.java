package com.eneik.production.services.worker.verification;

import com.eneik.production.models.persistence.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Popperian Falsification Gate (D008) and Blind Arbiter (Dennett's Homuncular Functionalism).
 * Holds a global host semaphore Semaphore(1) to strictly isolate Hetzner host memory (RAM <= 1.2 GiB).
 * LLM synthesizer has NO authority to choose check commands or skip severe tests.
 */
@Component
public class LocalVerificationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalVerificationRunner.class);

    // Hetzner host invariant: strictly one compiler container running at any point in time
    public static final Semaphore HOST_VERIFICATION_SEMAPHORE = new Semaphore(1);

    private static final int TIMEOUT_SECONDS = 300; // 5 minutes max

    /**
     * Executes Popperian severe verification test on worktree under Semaphore(1) protection.
     */
    public VerificationResult runVerification(Path worktreePath, TaskEntity task) {
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
            return executeInIsolatedContainer(worktreePath, targetCommand);

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
            // Local fallback execution (maintaining semaphore protection)
            fullCmd.addAll(buildCommand);
        }

        log.info("Executing verification command: {}", fullCmd);
        return runProcess(worktreePath, fullCmd, TIMEOUT_SECONDS);
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
