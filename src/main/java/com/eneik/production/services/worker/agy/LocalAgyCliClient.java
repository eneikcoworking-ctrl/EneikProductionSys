package com.eneik.production.services.worker.agy;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.deepseek.DeepSeekApiClient;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.security.AgyWorkerSecurityGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sovereign Zero-Cost Local Agy CLI Client.
 * Invokes the local agy CLI binary under dedicated product worker profiles (remotecli3 or root).
 * Enforces Raz Deontic Gate (D006) preventing L1/L2 identity conscription,
 * and parses output via LocalAgyOutputParser with Popperian Anti-Truncation Gate (D008).
 */
@Component
public class LocalAgyCliClient {

    private static final Logger log = LoggerFactory.getLogger(LocalAgyCliClient.class);

    private final LocalAgyCommandBuilder commandBuilder;
    private final LocalAgyOutputParser outputParser;
    private final SystemSettingsService settingsService;
    private ProcessRunner processRunner;
    private boolean customRunnerSet = false;

    private static final String[] BRIDGE_URLS = new String[] {
            System.getenv().getOrDefault("SOVEREIGN_HOST_BRIDGE_URL", "http://host.docker.internal:8095/execute-agy"),
            "http://172.17.0.1:8095/execute-agy",
            "http://127.0.0.1:8095/execute-agy"
    };

    @FunctionalInterface
    public interface ProcessRunner {
        ProcessResult run(List<String> command, int timeoutSeconds) throws IOException, InterruptedException;
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {}

    public LocalAgyCliClient() {
        this(new LocalAgyCommandBuilder(), new LocalAgyOutputParser(), null);
    }

    @Autowired
    public LocalAgyCliClient(LocalAgyCommandBuilder commandBuilder,
                             LocalAgyOutputParser outputParser,
                             @Autowired(required = false) SystemSettingsService settingsService) {
        this.commandBuilder = commandBuilder != null ? commandBuilder : new LocalAgyCommandBuilder();
        this.outputParser = outputParser != null ? outputParser : new LocalAgyOutputParser();
        this.settingsService = settingsService;
        this.processRunner = this::defaultRunProcess;
    }

    public void setProcessRunner(ProcessRunner processRunner) {
        this.processRunner = processRunner;
        this.customRunnerSet = true;
    }

    /**
     * Resolves the configured execution user for local agy CLI worker execution.
     * Enforces Raz Deontic Gate: will fail if resolved user is remotecli or remotecli2.
     */
    public String resolveExecutionUser() {
        if (settingsService != null) {
            String user = settingsService.effectiveValue("worker_local_agy_user");
            if (user != null && !user.isBlank()) {
                AgyWorkerSecurityGuard.validateWorkerExecutionUser(user);
                return user.trim();
            }
        }
        String envUser = System.getenv("WORKER_LOCAL_AGY_USER");
        if (envUser != null && !envUser.isBlank()) {
            AgyWorkerSecurityGuard.validateWorkerExecutionUser(envUser);
            return envUser.trim();
        }
        // Default product worker identity
        return "remotecli3";
    }

    /**
     * Generates code solution via local agy CLI.
     */
    public DeepSeekResponse generateSolution(TaskEntity task,
                                             String fileContentsContext,
                                             AccountEntity account,
                                             String reflectionFeedback) throws Exception {
        String executionUser = resolveExecutionUser();
        AgyWorkerSecurityGuard.validateWorkerExecutionUser(executionUser);

        String cynefin = task != null ? task.getCynefinDomain() : "complicated";
        String model = commandBuilder.resolveModel(cynefin);

        String fullPrompt = buildAgyPrompt(task, fileContentsContext, reflectionFeedback);
        List<String> command = commandBuilder.buildCommand(executionUser, cynefin, fullPrompt);

        log.info("LocalAgyCliClient: Executing agy under [{}] with model [{}] for task {}",
                executionUser, model, task != null ? task.getId() : "unknown");

        ProcessResult result;
        String binaryPath = commandBuilder.resolveBinaryPath(executionUser);
        boolean localBinaryExists = new File(binaryPath).canExecute();

        if (customRunnerSet || localBinaryExists) {
            try {
                result = processRunner.run(command, LocalAgyCommandBuilder.DEFAULT_TIMEOUT_SECONDS);
            } catch (IOException e) {
                if (!customRunnerSet && e.getMessage() != null && e.getMessage().contains("No such file")) {
                    log.info("Local binary not executable, falling back to host bridge for task {}", task != null ? task.getId() : "unknown");
                    result = callHostBridge(executionUser, model, fullPrompt, LocalAgyCommandBuilder.DEFAULT_TIMEOUT_SECONDS);
                } else {
                    throw e;
                }
            }
        } else {
            log.info("Local binary {} absent or not executable; executing agy via Host Bridge for task {}",
                    binaryPath, task != null ? task.getId() : "unknown");
            result = callHostBridge(executionUser, model, fullPrompt, LocalAgyCommandBuilder.DEFAULT_TIMEOUT_SECONDS);
        }

        if (result.exitCode() != 0) {
            log.error("LocalAgyCliClient: process failed with exit code {}. Stderr: {}",
                    result.exitCode(), result.stderr());
            throw new IOException("Local agy CLI process failed (exit " + result.exitCode() + "): " + result.stderr());
        }

        String stdout = result.stdout();
        if (stdout == null || stdout.isBlank()) {
            throw new IOException("Local agy CLI returned empty stdout");
        }

        return outputParser.parse(stdout, model);
    }

    private ProcessResult callHostBridge(String executionUser, String model, String prompt, int timeoutSeconds) throws IOException {
        String jsonPayload;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> req = java.util.Map.of(
                    "user", executionUser,
                    "model", model,
                    "prompt", prompt,
                    "timeoutSeconds", timeoutSeconds
            );
            jsonPayload = mapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new IOException("Failed to serialize host bridge request: " + e.getMessage(), e);
        }

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        IOException lastException = null;
        for (String url : BRIDGE_URLS) {
            try {
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(timeoutSeconds + 15))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                        .build();

                java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 403) {
                    throw new com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException(
                            "Raz Deontic Gate Violation (D006) from Bridge: " + response.body()
                    );
                }

                if (response.statusCode() == 200) {
                    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                    int exitCode = root.has("exitCode") ? root.get("exitCode").asInt() : 0;
                    String stdout = root.has("stdout") ? root.get("stdout").asText() : "";
                    String stderr = root.has("stderr") ? root.get("stderr").asText() : "";
                    return new ProcessResult(exitCode, stdout, stderr);
                }

                throw new IOException("Host bridge returned HTTP " + response.statusCode() + ": " + response.body());
            } catch (com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException d006) {
                throw d006;
            } catch (IOException e) {
                log.warn("Attempt to connect to bridge at {} failed: {}", url, e.getMessage());
                lastException = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Bridge request interrupted", e);
            }
        }

        throw new IOException("All Sovereign Host Bridge endpoints failed. Last error: " + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    public String buildAgyPrompt(TaskEntity task, String fileContentsContext, String reflectionFeedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an autonomous sovereign coding agent operating within EneikProductionSys.\n");
        sb.append("## DEONTIC BOUNDARY (JOSEPH RAZ D006):\n");
        sb.append("You must ONLY modify files within the declared fileScope. Modifying or creating any other file is strictly forbidden.\n\n");

        if (task != null) {
            sb.append("### TASK DEFINITION\n");
            sb.append("Title: ").append(task.getTitle()).append("\n");
            sb.append("File Scope: ").append(task.getFileScope()).append("\n");
            sb.append("Description & Acceptance Criteria:\n").append(task.getDescription()).append("\n\n");
        }

        if (fileContentsContext != null && !fileContentsContext.isBlank()) {
            sb.append("### EXISTING FILES IN SCOPE:\n");
            sb.append(fileContentsContext).append("\n\n");
        }

        if (reflectionFeedback != null && !reflectionFeedback.isBlank()) {
            sb.append("### PREVIOUS ATTEMPT FEEDBACK (POPPER D008):\n");
            sb.append(reflectionFeedback).append("\n");
            sb.append("Fix the issues indicated above.\n\n");
        }

        sb.append("### REQUIRED JSON OUTPUT FORMAT\n");
        sb.append("You MUST return your answer STRICTLY as a single valid JSON object with the following schema:\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"Brief explanation of changes\",\n");
        sb.append("  \"rationale\": \"Technical reasoning\",\n");
        sb.append("  \"scope_status\": \"SUFFICIENT\" or \"SCOPE_INSUFFICIENT\",\n");
        sb.append("  \"missing_files\": [],\n");
        sb.append("  \"files\": [\n");
        sb.append("    {\n");
        sb.append("      \"path\": \"relative/path/to/file\",\n");
        sb.append("      \"operation\": \"MODIFY\" or \"CREATE\",\n");
        sb.append("      \"mode\": \"FULL_CONTENT\" or \"PATCH_BLOCKS\",\n");
        sb.append("      \"content\": \"Full file content if mode=FULL_CONTENT\",\n");
        sb.append("      \"patch_blocks\": [\n");
        sb.append("        { \"search\": \"exact lines to find\", \"replace\": \"exact replacement lines\" }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("IMPORTANT: Do NOT use lazy truncation markers like '// ... rest of code' or '/* ... */'. Provide complete, executable code.\n");

        return sb.toString();
    }

    private ProcessResult defaultRunProcess(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outThread = new Thread(() -> readStream(process.getInputStream(), stdout));
        Thread errThread = new Thread(() -> readStream(process.getErrorStream(), stderr));
        outThread.start();
        errThread.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            outThread.interrupt();
            errThread.interrupt();
            throw new IOException("Local agy CLI process timed out after " + timeoutSeconds + " seconds");
        }

        outThread.join(2000);
        errThread.join(2000);

        int exitCode = process.exitValue();
        return new ProcessResult(exitCode, stdout.toString(), stderr.toString());
    }

    private void readStream(java.io.InputStream is, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                target.append(line).append("\n");
            }
        } catch (IOException ignored) {
        }
    }
}
