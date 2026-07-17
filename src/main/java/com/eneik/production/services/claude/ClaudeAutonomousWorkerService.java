package com.eneik.production.services.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.agents.AgentCreateParams;
import com.anthropic.models.beta.agents.BetaManagedAgentsAgentToolset20260401Params;
import com.anthropic.models.beta.agents.BetaManagedAgentsModel;
import com.anthropic.models.beta.environments.BetaCloudConfigParams;
import com.anthropic.models.beta.environments.BetaUnrestrictedNetwork;
import com.anthropic.models.beta.environments.EnvironmentCreateParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsAgentParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsGitHubRepositoryResourceParams;
import com.anthropic.models.beta.sessions.SessionCreateParams;
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsStreamSessionEvents;
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserMessageEventParams;
import com.anthropic.models.beta.sessions.events.EventSendParams;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Autonomous engineering worker built on the Claude Developer Platform's Managed Agents surface —
 * this is the "self" that replaces the old Antigravity/Gemini interactions-API integration. Anthropic
 * hosts both the agent loop and a per-session sandbox (bash/read/write/edit/glob/grep/web_search/
 * web_fetch); the worker clones the project's repository, diagnoses/repairs the requested problem,
 * runs the project's own tests, commits, and pushes a dedicated branch. Opening the pull request is
 * done by this service afterward via a plain GitHub REST call, not from inside the sandbox, so no
 * GitHub MCP server / vault / OAuth credential has to be provisioned just to file a PR.
 */
@Service
public class ClaudeAutonomousWorkerService {
    private static final Logger log = LoggerFactory.getLogger(ClaudeAutonomousWorkerService.class);
    private static final DateTimeFormatter BRANCH_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String AGENT_NAME = "Eneik Claude Autonomous Worker";

    private final SystemSettingsService settingsService;
    private final AnthropicClientFactory clientFactory;
    private final HttpClient githubHttpClient;
    private final String githubOrganization;
    private final int requestTimeoutSeconds;

    @Autowired
    public ClaudeAutonomousWorkerService(SystemSettingsService settingsService,
                                         @Value("${github.org:eneikcoworking-ctrl}") String githubOrganization,
                                         @Value("${claude-worker.request-timeout-seconds:600}") int requestTimeoutSeconds) {
        this(settingsService, ClaudeAutonomousWorkerService::defaultClient, githubOrganization, requestTimeoutSeconds);
    }

    ClaudeAutonomousWorkerService(SystemSettingsService settingsService,
                                  AnthropicClientFactory clientFactory,
                                  String githubOrganization,
                                  int requestTimeoutSeconds) {
        this.settingsService = settingsService;
        this.clientFactory = clientFactory;
        this.githubHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.githubOrganization = githubOrganization == null || githubOrganization.isBlank()
                ? "eneikcoworking-ctrl"
                : githubOrganization.trim();
        this.requestTimeoutSeconds = Math.max(60, Math.min(1800, requestTimeoutSeconds));
    }

    private static AnthropicClient defaultClient(String apiKey) {
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    public DiagnosticResult runDiagnostic(ProjectEntity project, ProjectOperationalContext context, String userMessage) {
        if (!settingsService.effectiveBoolean("claude_worker_enabled")) {
            return DiagnosticResult.unavailable("Claude autonomous worker is disabled.");
        }

        String apiKey = settingsService.effectiveValue("anthropic_api_key");
        if (apiKey == null || apiKey.isBlank()) {
            return DiagnosticResult.unavailable("Anthropic API key is missing. Configure anthropic_api_key.");
        }

        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return DiagnosticResult.unavailable("Repository owner/name is missing.");
        }

        boolean pushEnabled = settingsService.effectiveBoolean("claude_worker_push_enabled");
        String githubToken = pushEnabled ? settingsService.effectiveValue("github_token") : "";
        boolean pushRequested = pushEnabled && githubToken != null && !githubToken.isBlank();
        String branchName = branchName(project);
        String defaultBranch = project.getDefaultBranch() == null || project.getDefaultBranch().isBlank()
                ? "main" : project.getDefaultBranch();
        String model = firstNonBlank(settingsService.effectiveValue("claude_worker_model"), "claude-opus-4-8");

        AnthropicClient client = clientFactory.create(apiKey);
        try {
            String agentId = ensureAgent(client, model);
            String environmentId = ensureEnvironment(client);

            SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                    .agent(BetaManagedAgentsAgentParams.builder()
                            .type(BetaManagedAgentsAgentParams.Type.AGENT)
                            .id(agentId)
                            .build())
                    .environmentId(environmentId)
                    .title("Claude autonomous worker: " + project.getName());

            if (pushRequested) {
                sessionBuilder.addResource(BetaManagedAgentsGitHubRepositoryResourceParams.builder()
                        .type(BetaManagedAgentsGitHubRepositoryResourceParams.Type.GITHUB_REPOSITORY)
                        .url("https://github.com/" + repoRef.owner() + "/" + repoRef.repo())
                        .authorizationToken(githubToken)
                        .build());
            }

            var session = client.beta().sessions().create(sessionBuilder.build());
            String mission = buildMission(project, context, userMessage, repoRef, branchName, defaultBranch, pushRequested);

            RunOutcome outcome = drainSession(client, session.id(), mission);
            String output = redact(outcome.transcript(), apiKey, githubToken);

            BranchVerification verification = pushRequested
                    ? verifyBranch(repoRef, branchName, githubToken)
                    : new BranchVerification(false, "", "push disabled or GitHub token missing");

            String prUrl = "";
            if (verification.exists() && pushRequested) {
                prUrl = openPullRequest(repoRef, branchName, defaultBranch, project, githubToken);
            }

            String status = outcome.errored()
                    ? "api_error"
                    : verification.exists() ? "branch_verified"
                    : pushRequested ? "completed_unverified_branch" : "completed_read_only";

            String finalOutput = output + "\n\nBRANCH_VERIFICATION: " + verification.message()
                    + (prUrl.isBlank() ? "" : "\nPR_URL: " + prUrl);

            return new DiagnosticResult(!outcome.errored(), status, branchName, pushRequested,
                    verification.exists(), verification.commitSha(), finalOutput);
        } catch (Exception e) {
            log.error("ClaudeAutonomousWorkerService: diagnostic run failed for project {}", project.getId(), e);
            return new DiagnosticResult(false, "error", branchName, pushRequested, false, "", e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Creates the Managed Agent once and reuses it forever after — agents are persisted, versioned
     * resources, not per-request parameters (see the Managed Agents "create once, reference by id"
     * contract). The id is cached in system_settings so a restart doesn't create a duplicate agent.
     */
    private String ensureAgent(AnthropicClient client, String model) {
        String cached = settingsService.effectiveValue("claude_worker_agent_id");
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        var agent = client.beta().agents().create(AgentCreateParams.builder()
                .name(AGENT_NAME)
                .model(AgentCreateParams.Model.ofBetaManagedAgents(BetaManagedAgentsModel.of(model)))
                .system(AGENT_SYSTEM_PROMPT)
                .addTool(BetaManagedAgentsAgentToolset20260401Params.builder()
                        .type(BetaManagedAgentsAgentToolset20260401Params.Type.AGENT_TOOLSET_20260401)
                        .build())
                .build());
        settingsService.save("claude_worker_agent_id", agent.id());
        return agent.id();
    }

    private String ensureEnvironment(AnthropicClient client) {
        String cached = settingsService.effectiveValue("claude_worker_environment_id");
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        var environment = client.beta().environments().create(EnvironmentCreateParams.builder()
                .name("eneik-claude-worker")
                .config(BetaCloudConfigParams.builder()
                        .networking(BetaUnrestrictedNetwork.builder().build())
                        .build())
                .build());
        settingsService.save("claude_worker_environment_id", environment.id());
        return environment.id();
    }

    private RunOutcome drainSession(AnthropicClient client, String sessionId, String mission) {
        StringBuilder transcript = new StringBuilder();
        boolean errored = false;
        Instant deadline = Instant.now().plusSeconds(requestTimeoutSeconds);

        try (var stream = client.beta().sessions().events().streamStreaming(sessionId)) {
            client.beta().sessions().events().send(sessionId, EventSendParams.builder()
                    .addEvent(BetaManagedAgentsUserMessageEventParams.builder()
                            .type(BetaManagedAgentsUserMessageEventParams.Type.USER_MESSAGE)
                            .addTextContent(mission)
                            .build())
                    .build());

            for (BetaManagedAgentsStreamSessionEvents event : (Iterable<BetaManagedAgentsStreamSessionEvents>) stream.stream()::iterator) {
                if (event.isAgentMessage()) {
                    event.asAgentMessage().content().forEach(block -> transcript.append(block.text()));
                } else if (event.isAgentToolUse()) {
                    transcript.append("\n[tool: ").append(event.asAgentToolUse().name()).append("]\n");
                } else if (event.isSessionStatusIdle()) {
                    break;
                } else if (event.isSessionError()) {
                    errored = true;
                    transcript.append("\n[session error]\n");
                    break;
                }
                if (Instant.now().isAfter(deadline)) {
                    transcript.append("\n[worker timed out after ").append(requestTimeoutSeconds).append("s]\n");
                    break;
                }
            }
        } catch (Exception e) {
            errored = true;
            transcript.append("\n[stream error: ").append(e.getMessage()).append("]\n");
        }

        return new RunOutcome(transcript.toString(), errored);
    }

    private String buildMission(ProjectEntity project,
                                ProjectOperationalContext context,
                                String userMessage,
                                RepoRef repoRef,
                                String branchName,
                                String defaultBranch,
                                boolean pushRequested) {
        String repoHttps = "https://github.com/" + repoRef.owner() + "/" + repoRef.repo() + ".git";

        return """
                You are the Eneik Claude Autonomous Engineering Worker.
                The Eneik Project Operator delegated one bounded engineering mission to you.

                HARD RULES:
                - Work in English only.
                - Do not commit directly to %s; work on the dedicated branch below.
                - Use exactly this branch name: %s
                - Mandatory sandbox verification: run the project's real build/test command (e.g. 'mvn -q test'
                  or 'npm test') via the bash tool and confirm it passes before committing.
                - No placeholders or stubs: do not write TODO comments, empty mock classes, or temporary stub
                  implementations. Every code change must be a complete, fully implemented fix.
                - Prefer fixing the root cause and proving it with tests over merely describing the issue.
                - Keep scope coherent with the operator request; do not fix unrelated issues you notice — note
                  them in FOLLOW_UP_WISHLIST instead.
                - If the code cannot be changed safely, produce an evidence-backed diagnosis instead of a
                  speculative change.
                - Never print, echo, or store credentials in files. If a token appears in command output,
                  redact it before including that output in your final message.
                - Return a final message with these exact headings:
                  STATUS
                  ROOT_CAUSE
                  CHANGES_MADE
                  TESTS_RUN
                  FOLLOW_UP_WISHLIST

                REPOSITORY:
                - clone_url: %s
                - default_branch: %s
                - push_requested: %s

                REQUIRED GIT FLOW:
                1. Clone the repository (it is already mounted read-write in your workspace if push_requested=true;
                   otherwise clone %s read-only).
                2. Checkout %s and pull latest.
                3. Create and switch to branch %s.
                4. Diagnose and, when feasible, repair the problem described below using repository evidence.
                5. Run the project's real tests; if tests cannot run, say exactly why.
                6. Commit only if you made a useful, complete change.
                7. If push_requested=true, push branch %s. Do not open a pull request yourself and do not merge
                   anything — the operator opens the PR and Eneik's merge gates decide after review.

                OPERATOR REQUEST:
                %s

                PROJECT FACTS:
                %s
                """.formatted(
                defaultBranch,
                branchName,
                repoHttps,
                defaultBranch,
                pushRequested,
                repoHttps,
                defaultBranch,
                branchName,
                branchName,
                userMessage == null || userMessage.isBlank() ? "Run a deep diagnostic for the next stalled project work." : userMessage,
                preview(context.promptJson(), 18_000)
        );
    }

    private static final String AGENT_SYSTEM_PROMPT = """
            You are a senior autonomous software engineer operating inside a sandboxed workspace on behalf of
            the Eneik Production System. You are given one bounded engineering mission per session: diagnose
            and, where safe, repair a real problem in a real repository, proving your work with the project's
            own tests. You never fabricate results, never merge your own work, and never leave placeholder or
            stub code behind.
            """;

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
            HttpResponse<String> response = githubHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                JsonNode body = new ObjectMapper().readTree(response.body());
                String sha = body.path("commit").path("sha").asText("");
                return new BranchVerification(true, sha, "branch exists on GitHub; commit=" + sha);
            }
            return new BranchVerification(false, "", "branch lookup HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BranchVerification(false, "", "branch verification interrupted");
        } catch (Exception e) {
            return new BranchVerification(false, "", "branch verification failed: " + e.getMessage());
        }
    }

    private String openPullRequest(RepoRef repoRef, String branchName, String defaultBranch, ProjectEntity project, String githubToken) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
            body.put("title", "Claude autonomous worker: " + project.getName());
            body.put("head", branchName);
            body.put("base", defaultBranch);
            body.put("body", "Opened automatically after a Claude autonomous worker session pushed branch `"
                    + branchName + "`. Eneik merge gates review before merge.");

            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls";
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com" + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = githubHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readTree(response.body()).path("html_url").asText("");
            }
            log.warn("ClaudeAutonomousWorkerService: failed to open PR for branch {} (HTTP {})", branchName, response.statusCode());
            return "";
        } catch (Exception e) {
            log.warn("ClaudeAutonomousWorkerService: failed to open PR for branch {}: {}", branchName, e.getMessage());
            return "";
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
        return "claude-autonomy/" + clean + "/" + BRANCH_TIME.format(Instant.now());
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

    private record RunOutcome(String transcript, boolean errored) {}

    @FunctionalInterface
    interface AnthropicClientFactory {
        AnthropicClient create(String apiKey);
    }

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
