package com.eneik.production.services.antigravity;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class AntigravityDiagnosticService {
    private static final DateTimeFormatter BRANCH_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String defaultAgent;
    private final String githubOrganization;
    private final int requestTimeoutSeconds;

    public AntigravityDiagnosticService(SystemSettingsService settingsService,
                                        ObjectMapper objectMapper,
                                        @Value("${antigravity.api-url:https://generativelanguage.googleapis.com/v1beta/interactions}") String apiUrl,
                                        @Value("${antigravity.agent:antigravity-preview-05-2026}") String defaultAgent,
                                        @Value("${github.org:eneikcoworking-ctrl}") String githubOrganization,
                                        @Value("${antigravity.request-timeout-seconds:300}") int requestTimeoutSeconds) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.apiUrl = apiUrl == null || apiUrl.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta/interactions"
                : apiUrl.trim();
        this.defaultAgent = defaultAgent == null || defaultAgent.isBlank()
                ? "antigravity-preview-05-2026"
                : defaultAgent.trim();
        this.githubOrganization = githubOrganization == null || githubOrganization.isBlank()
                ? "eneikcoworking-ctrl"
                : githubOrganization.trim();
        this.requestTimeoutSeconds = Math.max(60, Math.min(900, requestTimeoutSeconds));
    }

    public DiagnosticResult runDiagnostic(ProjectEntity project,
                                          ProjectOperationalContext context,
                                          String userMessage) {
        if (!settingsService.effectiveBoolean("antigravity_enabled")) {
            return DiagnosticResult.unavailable("Antigravity integration is disabled.");
        }

        String apiKey = firstNonBlank(
                settingsService.effectiveValue("antigravity_api_key"),
                settingsService.effectiveValue("gemini_api_key")
        );
        if (apiKey == null || apiKey.isBlank()) {
            return DiagnosticResult.unavailable("Antigravity API key is missing. Configure antigravity_api_key or gemini_api_key.");
        }

        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return DiagnosticResult.unavailable("Repository owner/name is missing.");
        }

        boolean pushEnabled = settingsService.effectiveBoolean("antigravity_push_enabled");
        String githubToken = pushEnabled ? settingsService.effectiveValue("github_token") : "";
        boolean pushRequested = pushEnabled && githubToken != null && !githubToken.isBlank();
        String branchName = branchName(project);
        String agent = firstNonBlank(settingsService.effectiveValue("antigravity_agent"), defaultAgent);

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("agent", agent);
            body.put("environment", "remote");
            body.put("input", buildMission(project, context, userMessage, repoRef, branchName, pushRequested, githubToken));
            ArrayNode tools = body.putArray("tools");
            tools.addObject().put("type", "code_execution");
            tools.addObject().put("type", "url_context");
            tools.addObject().put("type", "google_search");

            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String redactedBody = redact(response.body(), apiKey, githubToken);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new DiagnosticResult(
                        false,
                        "api_error",
                        branchName,
                        pushRequested,
                        false,
                        "",
                        "Antigravity API returned HTTP " + response.statusCode() + ": " + preview(redactedBody, 1_500)
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String output = redact(root.path("output_text").asText(redactedBody), apiKey, githubToken);
            BranchVerification verification = pushRequested
                    ? verifyBranch(repoRef, branchName, githubToken)
                    : new BranchVerification(false, "", "push disabled or GitHub token missing");
            String status = verification.exists()
                    ? "branch_verified"
                    : pushRequested ? "completed_unverified_branch" : "completed_read_only";
            return new DiagnosticResult(
                    true,
                    status,
                    branchName,
                    pushRequested,
                    verification.exists(),
                    verification.commitSha(),
                    output + "\n\nBRANCH_VERIFICATION: " + verification.message()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DiagnosticResult(false, "interrupted", branchName, pushRequested, false, "", "Antigravity diagnostic was interrupted.");
        } catch (Exception e) {
            return new DiagnosticResult(false, "error", branchName, pushRequested, false, "", e.getMessage());
        }
    }

    private String buildMission(ProjectEntity project,
                                ProjectOperationalContext context,
                                String userMessage,
                                RepoRef repoRef,
                                String branchName,
                                boolean pushRequested,
                                String githubToken) {
        String repoHttps = "https://github.com/" + repoRef.owner() + "/" + repoRef.repo() + ".git";
        String cloneUrl = pushRequested
                ? "https://x-access-token:" + githubToken + "@github.com/" + repoRef.owner() + "/" + repoRef.repo() + ".git"
                : repoHttps;

        return """
                You are Eneik Antigravity Diagnostic Worker.
                You are not the project manager. Gemini Project Operator delegated one bounded engineering diagnostic mission to you.

                HARD RULES:
                - Work in English only.
                - Never modify, merge, or force-push main.
                - Use exactly this diagnostic branch: %s
                - Keep code changes minimal and diagnostic. Prefer a small repair plus tests over broad refactoring.
                - If you cannot safely change code, still produce an evidence-backed diagnosis and follow-up wishlist items.
                - Do not print, echo, store in files, or reveal credentials. If a token appears in command output, redact it.
                - Return a final artifact with these exact headings:
                  STATUS
                  BRANCH
                  COMMIT
                  PR_URL
                  TESTS_RUN
                  ROOT_CAUSE
                  CHANGES_MADE
                  FOLLOW_UP_WISHLIST

                REPOSITORY:
                - public_url: %s
                - clone_url_for_this_mission: %s
                - default_branch: %s
                - push_requested: %s

                REQUIRED GIT FLOW:
                1. Clone repository.
                2. Checkout default branch and pull latest.
                3. Create branch %s from default branch.
                4. Diagnose the project task/problem using repository evidence.
                5. Run available tests or document why tests cannot run.
                6. Commit only if you made useful changes.
                7. If push_requested=true, push branch %s. Do not open or merge a PR unless the repository tooling makes that trivial; branch push is enough.

                OPERATOR REQUEST:
                %s

                PROJECT FACTS:
                %s
                """.formatted(
                branchName,
                repoHttps,
                cloneUrl,
                project.getDefaultBranch() == null || project.getDefaultBranch().isBlank() ? "main" : project.getDefaultBranch(),
                pushRequested,
                branchName,
                branchName,
                userMessage == null || userMessage.isBlank() ? "Run a deep diagnostic for the next stalled project work." : userMessage,
                preview(context.promptJson(), 18_000)
        );
    }

    private BranchVerification verifyBranch(RepoRef repoRef, String branchName, String githubToken) {
        if (githubToken == null || githubToken.isBlank()) {
            return new BranchVerification(false, "", "GitHub token missing; branch verification skipped.");
        }
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/branches/" + encode(branchName);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com" + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                JsonNode body = objectMapper.readTree(response.body());
                String sha = body.path("commit").path("sha").asText("");
                return new BranchVerification(true, sha, "branch exists on GitHub; commit=" + sha);
            }
            return new BranchVerification(false, "", "branch lookup HTTP " + response.statusCode() + " " + preview(response.body(), 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BranchVerification(false, "", "branch verification interrupted");
        } catch (Exception e) {
            return new BranchVerification(false, "", "branch verification failed: " + e.getMessage());
        }
    }

    private RepoRef repoRef(ProjectEntity project) {
        if (project == null) {
            return new RepoRef("", "");
        }
        String url = firstNonBlank(project.getRepositoryUrl(), project.getRepoUrl());
        if (url != null && url.startsWith("https://github.com/")) {
            String clean = url.replace("https://github.com/", "").replaceAll("/+$", "");
            if (clean.endsWith(".git")) {
                clean = clean.substring(0, clean.length() - 4);
            }
            String[] parts = clean.split("/");
            if (parts.length >= 2) {
                return new RepoRef(parts[0], parts[1]);
            }
        }
        String repo = project.getRepositoryName() == null ? "" : project.getRepositoryName();
        if (repo.contains("/")) {
            String[] parts = repo.split("/");
            if (parts.length >= 2) {
                return new RepoRef(parts[0], parts[1]);
            }
        }
        return new RepoRef(githubOrganization, repo);
    }

    private String branchName(ProjectEntity project) {
        String slug = project == null ? "unknown-project" : firstNonBlank(project.getSlug(), project.getRepositoryName(), project.getName());
        String clean = slug == null ? "unknown-project" : slug.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._/-]+", "-")
                .replaceAll("^[-/]+|[-/]+$", "");
        if (clean.isBlank()) {
            clean = "unknown-project";
        }
        return "ag-diagnostic/" + clean + "/" + BRANCH_TIME.format(Instant.now());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String redact(String text, String... secrets) {
        if (text == null) {
            return "";
        }
        String redacted = text;
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                redacted = redacted.replace(secret, "[REDACTED]");
            }
        }
        return redacted;
    }

    private String preview(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxChars ? compact : compact.substring(0, maxChars) + "... [truncated]";
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record RepoRef(String owner, String repo) {}

    private record BranchVerification(boolean exists, String commitSha, String message) {}

    public record DiagnosticResult(
            boolean available,
            String status,
            String branchName,
            boolean branchPushRequested,
            boolean branchVerified,
            String commitSha,
            String output
    ) {
        static DiagnosticResult unavailable(String reason) {
            return new DiagnosticResult(false, "unavailable", "", false, false, "", reason);
        }
    }
}
