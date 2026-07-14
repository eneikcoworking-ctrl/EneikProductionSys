package com.eneik.production.services.dashboard;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class ProjectOperatorService {
    private static final int MAX_TOOL_OUTPUT = 8_000;
    private static final int MAX_FILE_BYTES = 24_000;

    private final ProjectRepository projectRepository;
    private final ProjectOperationalContextService contextService;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final ProjectFlowService projectFlowService;
    private final ClaimService claimService;
    private final Path workspaceRoot;
    private final Path systemRepoRoot;
    private final Path memoryRoot;
    private final boolean allowMutatingTools;
    private final String dockerComposeProjectName;
    private final int stuckThresholdMinutes;
    private final int maxToolRounds;
    private final boolean answerCriticEnabled;
    private final ObjectMapper objectMapper;

    public ProjectOperatorService(ProjectRepository projectRepository,
                                  ProjectOperationalContextService contextService,
                                  MLPredictionServiceClient mlPredictionServiceClient,
                                  ProjectFlowService projectFlowService,
                                  ClaimService claimService,
                                  @Value("${project-factory.workspace-root:./project-workspaces}") String workspaceRoot,
                                  @Value("${eneik.operator.system-repo-root:}") String systemRepoRoot,
                                  @Value("${eneik.operator.memory-root:./data/operator-memory}") String memoryRoot,
                                  @Value("${eneik.operator.allow-mutating-tools:false}") boolean allowMutatingTools,
                                  @Value("${eneik.operator.docker-compose-project-name:}") String dockerComposeProjectName,
                                  @Value("${jules.stuck-threshold-minutes:30}") int stuckThresholdMinutes,
                                  @Value("${eneik.operator.max-tool-rounds:3}") int maxToolRounds,
                                  @Value("${eneik.operator.answer-critic-enabled:true}") boolean answerCriticEnabled,
                                  ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.contextService = contextService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.projectFlowService = projectFlowService;
        this.claimService = claimService;
        this.workspaceRoot = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        this.systemRepoRoot = (systemRepoRoot == null || systemRepoRoot.isBlank())
                ? Paths.get(".").toAbsolutePath().normalize()
                : Paths.get(systemRepoRoot).toAbsolutePath().normalize();
        this.memoryRoot = Paths.get(memoryRoot).toAbsolutePath().normalize();
        this.allowMutatingTools = allowMutatingTools;
        this.dockerComposeProjectName = dockerComposeProjectName == null ? "" : dockerComposeProjectName.trim();
        this.stuckThresholdMinutes = stuckThresholdMinutes;
        this.maxToolRounds = Math.max(1, Math.min(5, maxToolRounds));
        this.answerCriticEnabled = answerCriticEnabled;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String answer(UUID projectId, String fallbackProjectName, String userMessage) {
        ProjectEntity project = resolveProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        ProjectOperationalContext context = contextService.build(project.getId(), fallbackProjectName);
        OperatorEvidence evidence = runGeminiToolLoop(project, context, userMessage);

        String systemInstruction = """
                You are Gemini Project Operator inside Eneik Production System.

                ENEIK MANAGEMENT SYSTEM IS PRIMARY:
                - Truth is factive: assert only what is present in PROJECT_FACT_PACK or OPERATOR_EVIDENCE.
                - Durable memory is subordinate to current evidence: use project memory as history, not as proof of current runtime state.
                - Bivalence: a verification either passed, failed, or was not run. Do not say "probably" for tool results.
                - Epistemic marking: label important claims as VERIFIED, INFERRED, or NOT AVAILABLE.
                - Deontic clarity: separate Obligatory, Permitted, and Forbidden actions when giving operational instructions.
                - Lean / TOC / Six Sigma are management lenses, not decorative boilerplate. Use them only when they improve the concrete decision.
                - Roles are lenses. You may speak as TAG-00..TAG-11 when useful, but Eneik Management System overrides role style.
                - CORE JULES INVARIANT: every enabled Jules account is capable of every BARCAN-TAG-00..11 role. Never diagnose missing role capability unless PROJECT_FACT_PACK says universalRolePool=false.

                OPERATOR RULES:
                - First sentence directly answers the user.
                - No greeting, no self-introduction, no motivational filler.
                - PROJECT_FACT_PACK and OPERATOR_EVIDENCE are internal data sources. Never mention their names, the prompt, or hidden context to the user.
                - Never invent files, commands, Docker status, PR facts, account counts, or test results.
                - Never invent causal explanations. For "why" questions, separate VERIFIED observations from INFERRED hypotheses and say NOT AVAILABLE when the causal link is not in evidence.
                - Every number about PRs, tasks, sessions, accounts, conflicts, Docker, or tests must be traceable to PROJECT_FACT_PACK or a named OPERATOR_EVIDENCE tool.
                - If shared Jules slots are free, do not claim a Jules capacity shortage. Diagnose dispatch flow, stuck claims, API failures, conflicts, or unanswered activity handling instead.
                - loop_closed Jules sessions are terminal local closures. Use closedAt and closureReason to explain why the session was stopped and which follow-up wishlist replaces the old branch.
                - You may say you executed an action only when OPERATOR_EVIDENCE contains operator_action [ok].
                - If a command was not run, say it was not run and why.
                - If a tool is unavailable, say so and give the nearest verifiable next step.
                - If operator_action is ok, report the completed action and do not ask for confirmation.
                - Do not say the Technical Lead must convert wishlist items when operator_action has already run orchestration.
                - Never ask the user to confirm an action that was already requested in the current message.
                - Prefer short operational recommendations grounded in evidence.
                - Use selected project facts only unless the user explicitly asks about the Eneik system itself.
                - Answer in the user's language unless the user asks for Jules task text or PR/task artifacts; those artifacts must be English.

                PROJECT_FACT_PACK:
                """ + context.promptJson() + "\n\nOPERATOR_EVIDENCE:\n" + evidence.toPrompt();

        String prompt = "User request:\n" + userMessage + "\n\n"
                + "Answer from the evidence above. If a tool was run, cite the tool name and result. "
                + "If a mutating tool was not run, say it was not run. Do not claim any action that is absent from OPERATOR_EVIDENCE.";
        String answer = mlPredictionServiceClient.chat(prompt, systemInstruction);
        if (answer == null || answer.isBlank()) {
            return evidence.fallbackAnswer();
        }
        return reviewAnswerWithCritic(project, context, evidence, userMessage, answer);
    }

    private Optional<ProjectEntity> resolveProject(UUID projectId) {
        if (projectId != null) {
            return projectRepository.findById(projectId);
        }
        return projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active);
    }

    private String reviewAnswerWithCritic(ProjectEntity project,
                                          ProjectOperationalContext context,
                                          OperatorEvidence evidence,
                                          String userMessage,
                                          String answer) {
        if (!answerCriticEnabled) {
            return answer;
        }
        String systemInstruction = """
                You are the Eneik Project Operator answer critic.
                Return ONLY valid JSON. Do not include markdown.

                Check whether the candidate answer is acceptable.
                Reject or revise when:
                - It contains a greeting, self-introduction, motivational filler, or generic agile boilerplate.
                - It answers with global system noise when the user asked about the selected project.
                - It uses numbers, PR facts, task/session/account counts, Docker status, commands, or test results not supported by evidence.
                - It claims a mutating action was executed without matching tool evidence.
                - It mentions hidden source names such as PROJECT_FACT_PACK, OPERATOR_EVIDENCE, prompt, or internal context.
                - It fails to say NOT AVAILABLE when evidence is insufficient.

                JSON schema:
                {"verdict":"pass|revise","issues":["short issue"],"revisedAnswer":"required when verdict=revise"}
                """;
        String prompt = "Project: " + project.getName() + " (" + project.getId() + ")\n"
                + "User request:\n" + userMessage + "\n\n"
                + "Candidate answer:\n" + answer + "\n\n"
                + "Selected project facts:\n" + trim(context.promptJson()) + "\n\n"
                + "Tool evidence:\n" + evidence.toPrompt() + "\n\n"
                + "If revising, keep the user's language and preserve only evidence-grounded claims.";
        String response = mlPredictionServiceClient.chat(prompt, systemInstruction);
        if (response == null || response.isBlank()) {
            return answer;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(response));
            String verdict = textArg(root, "verdict", "pass");
            String revisedAnswer = textArg(root, "revisedAnswer", "");
            if ("revise".equalsIgnoreCase(verdict) && !revisedAnswer.isBlank()) {
                return revisedAnswer;
            }
        } catch (Exception ignored) {
            // Critic is a guardrail. If it fails to produce valid JSON, keep the original evidence-based answer.
        }
        return answer;
    }

    private OperatorEvidence runGeminiToolLoop(ProjectEntity project, ProjectOperationalContext context, String userMessage) {
        List<ToolObservation> observations = new ArrayList<>();
        observations.add(operatorScope(project, userMessage));
        observations.add(projectMemoryRead(project));
        Set<String> executedSignatures = new LinkedHashSet<>();
        boolean executedPlannedTool = false;

        for (int round = 1; round <= maxToolRounds; round++) {
            OperatorToolPlan plan = planOperatorTools(project, context, userMessage, observations, round);
            observations.add(new ToolObservation(
                    "operator_tool_plan_round_" + round,
                    plan.valid() ? "ok" : "fallback",
                    plan.summary()
            ));
            if (plan.calls().isEmpty()) {
                break;
            }
            int executedThisRound = 0;
            for (OperatorToolCall call : plan.calls().stream().limit(12).toList()) {
                String signature = toolSignature(call);
                if (executedSignatures.contains(signature)) {
                    observations.add(new ToolObservation(call.tool(), "skipped_duplicate",
                            "Duplicate tool call skipped in round " + round + ": " + signature));
                    continue;
                }
                executedSignatures.add(signature);
                observations.add(executeToolCall(project, context, userMessage, call));
                executedThisRound++;
                executedPlannedTool = true;
            }
            if (executedThisRound == 0) {
                break;
            }
        }

        if (!executedPlannedTool) {
            observations.addAll(defaultReadOnlyEvidence(project, context));
        }

        return new OperatorEvidence(observations);
    }

    private ToolObservation operatorScope(ProjectEntity project, String userMessage) {
        Path projectWorkspace = resolveProjectWorkspace(project);
        boolean wantsSystem = wantsSystemRoot(userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT));
        Path primaryRoot = wantsSystem ? systemRepoRoot : projectWorkspace;
        Path dockerRoot = hasComposeFile(primaryRoot) ? primaryRoot : systemRepoRoot;
        return new ToolObservation("operator_scope", "ok", "project="
                + project.getName()
                + ", workspace=" + projectWorkspace
                + ", defaultRoot=" + primaryRoot
                + ", dockerRoot=" + dockerRoot
                + ", memoryRoot=" + memoryRoot
                + ", maxToolRounds=" + maxToolRounds
                + ", answerCriticEnabled=" + answerCriticEnabled
                + ", mutatingTools=" + allowMutatingTools
                + ", dockerComposeProjectName=" + valueOrUnset(dockerComposeProjectName));
    }

    private OperatorToolPlan planOperatorTools(ProjectEntity project,
                                               ProjectOperationalContext context,
                                               String userMessage,
                                               List<ToolObservation> observations,
                                               int round) {
        String systemInstruction = """
                You are Gemini Project Operator tool planner inside Eneik Production System.
                Return ONLY valid JSON. Do not include markdown.

                Decide which backend tools are needed before answering the user.
                Rules:
                - Use tools aggressively when facts, code, Docker, Git, tests, PRs, sessions, or execution state may matter.
                - Use already collected observations before asking for another tool.
                - When observations are enough for a factual answer, return an empty toolCalls array.
                - Do not request mutating tools for explanation questions. Explanation questions include "how does it work", "why", "what happened", "what do you see", "recommend".
                - Request mutating tools only when the user explicitly asks to run, create, add, orchestrate, dispatch, start, build, pull, or execute.
                - Prefer multiple narrow tools over one broad generic command.
                - Max 12 tool calls.

                JSON schema:
                {
                  "toolCalls": [
                    {"tool": "tool_name", "reason": "short reason", "args": {"key": "value"}}
                  ]
                }

                Available tools:
                """ + toolCatalog();

        String prompt = "Current project id: " + project.getId() + "\n"
                + "Current project name: " + project.getName() + "\n"
                + "Tool planning round: " + round + " of " + maxToolRounds + "\n"
                + "User request:\n" + userMessage + "\n\n"
                + "Selected project fact pack summary:\n" + trim(context.promptJson()) + "\n\n"
                + "Already collected observations:\n" + observationsForPlanner(observations);
        String response = mlPredictionServiceClient.chat(prompt, systemInstruction);
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(response));
            List<OperatorToolCall> calls = new ArrayList<>();
            JsonNode toolCalls = root.path("toolCalls");
            if (toolCalls.isArray()) {
                for (JsonNode item : toolCalls) {
                    String tool = textArg(item, "tool", "");
                    if (tool.isBlank()) {
                        continue;
                    }
                    calls.add(new OperatorToolCall(
                            tool,
                            item.path("args"),
                            textArg(item, "reason", "")
                    ));
                    if (calls.size() >= 12) {
                        break;
                    }
                }
            }
            return new OperatorToolPlan(true, calls, calls.isEmpty()
                    ? "Gemini returned no tool calls; using default read-only evidence."
                    : "Gemini requested " + calls.size() + " tool call(s): "
                    + calls.stream().map(OperatorToolCall::tool).reduce((a, b) -> a + ", " + b).orElse(""));
        } catch (Exception e) {
            return new OperatorToolPlan(false, List.of(),
                    "Tool planning failed; using default read-only evidence. Error: " + e.getMessage()
                            + "\nRaw planner response: " + trim(response));
        }
    }

    private String observationsForPlanner(List<ToolObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return "<none>";
        }
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, observations.size() - 18);
        for (int index = start; index < observations.size(); index++) {
            ToolObservation observation = observations.get(index);
            builder.append("## ").append(observation.tool()).append(" [").append(observation.status()).append("]\n")
                    .append(compact(observation.output(), 1_600))
                    .append("\n\n");
        }
        return trim(builder.toString());
    }

    private String toolSignature(OperatorToolCall call) {
        String args = call.args() == null || call.args().isMissingNode() ? "{}" : call.args().toString();
        return call.tool() + "::" + args;
    }

    private String toolCatalog() {
        return """
                Read-only project/data tools:
                - project_context {}
                - list_tasks {}
                - list_wishlist {}
                - list_jules_sessions {}
                - list_pr_reviews {}
                - list_accounts {}
                - list_conflicts {}
                - list_bottlenecks {}
                - project_memory_read {}

                Repository/code tools:
                - repo_profile {"root":"project|system"}
                - workspace_tree {"root":"project|system","depth":4}
                - find_files {"root":"project|system","glob":"optional glob like *.java","limit":80}
                - read_file {"root":"project|system","path":"relative/path"}
                - read_many_files {"root":"project|system","paths":["relative/path"],"maxBytesPerFile":8000}
                - search_code {"root":"project|system","query":"literal text"}

                Git tools:
                - git_status {"root":"project|system"}
                - git_diff_stat {"root":"project|system"}
                - git_diff {"root":"project|system","path":"optional/relative/path"}
                - git_log {"root":"project|system","limit":5}
                - git_show {"root":"project|system","ref":"HEAD"}
                - git_fetch {"root":"project|system"} MUTATING
                - git_pull_ff_only {"root":"project|system"} MUTATING

                Docker/runtime tools:
                - docker_ps {}
                - docker_compose_ps {"root":"project|system"}
                - docker_compose_logs {"root":"project|system","service":"optional","tail":120}
                - docker_compose_up {"root":"project|system"} MUTATING
                - docker_compose_build {"root":"project|system","service":"optional"} MUTATING

                HTTP/local service tools:
                - http_get_local {"service":"backend|ml","path":"/health or /api/... or /docs"}

                Verification tools:
                - detected_checks {"root":"project|system"}
                - run_detected_checks {"root":"project|system"} MUTATING/EXECUTION

                Eneik production tools:
                - orchestrate_project {} MUTATING
                - dispatch_project {} MUTATING
                - maintenance_stuck {} MUTATING
                - add_wishlist {"content":"English wishlist text","sourceRoleTag":"BARCAN-TAG-09"} MUTATING
                - project_memory_append {"note":"English durable project memory note grounded in current evidence"} MUTATING

                Generic tool:
                - run_command {"root":"project|system","command":["git","status","--short"],"timeoutSeconds":30} MUTATING/EXECUTION
                """;
    }

    private List<ToolObservation> defaultReadOnlyEvidence(ProjectEntity project, ProjectOperationalContext context) {
        return List.of(
                contextFact(context, "project_context", null),
                contextFact(context, "list_tasks", "tasks"),
                contextFact(context, "list_wishlist", "wishlist"),
                contextFact(context, "list_jules_sessions", "julesSessions"),
                contextFact(context, "list_pr_reviews", "databasePrReviews"),
                contextFact(context, "list_accounts", "accountsAvailableForProject"),
                contextFact(context, "list_conflicts", "conflicts"),
                contextFact(context, "list_bottlenecks", "bottlenecks")
        );
    }

    private ToolObservation executeToolCall(ProjectEntity project,
                                            ProjectOperationalContext context,
                                            String userMessage,
                                            OperatorToolCall call) {
        String tool = call.tool();
        JsonNode args = call.args();
        try {
            return switch (tool) {
                case "project_context" -> contextFact(context, tool, null);
                case "list_tasks" -> contextFact(context, tool, "tasks");
                case "list_wishlist" -> contextFact(context, tool, "wishlist");
                case "list_jules_sessions" -> contextFact(context, tool, "julesSessions");
                case "list_pr_reviews" -> contextFact(context, tool, "databasePrReviews");
                case "list_accounts" -> contextFact(context, tool, "accountsAvailableForProject");
                case "list_conflicts" -> contextFact(context, tool, "conflicts");
                case "list_bottlenecks" -> contextFact(context, tool, "bottlenecks");
                case "project_memory_read" -> projectMemoryRead(project);
                case "repo_profile" -> repoProfile(rootFor(project, args));
                case "workspace_tree" -> tree(rootFor(project, args));
                case "find_files" -> findFiles(rootFor(project, args), args);
                case "read_file" -> readIfExists(rootFor(project, args), textArg(args, "path", ""));
                case "read_many_files" -> readManyFiles(rootFor(project, args), args);
                case "search_code" -> {
                    String query = textArg(args, "query", "");
                    yield search(rootFor(project, args), query.isBlank() ? searchTerms(userMessage.toLowerCase(Locale.ROOT)) : List.of(query));
                }
                case "git_status" -> run("git_status", rootFor(project, args), Duration.ofSeconds(8), List.of("git", "status", "--short"));
                case "git_diff_stat" -> run("git_diff_stat", rootFor(project, args), Duration.ofSeconds(8), List.of("git", "diff", "--stat"));
                case "git_diff" -> gitDiff(project, args);
                case "git_log" -> run("git_log", rootFor(project, args), Duration.ofSeconds(8), List.of("git", "log", "--oneline", "-" + intArg(args, "limit", 5, 1, 30)));
                case "git_show" -> run("git_show", rootFor(project, args), Duration.ofSeconds(12), List.of("git", "show", "--stat", "--oneline", textArg(args, "ref", "HEAD")));
                case "git_fetch" -> mutating(userMessage, tool, () -> run("git_fetch", rootFor(project, args), Duration.ofSeconds(30), List.of("git", "fetch", "--all", "--prune")));
                case "git_pull_ff_only" -> mutating(userMessage, tool, () -> run("git_pull_ff_only", rootFor(project, args), Duration.ofSeconds(45), List.of("git", "pull", "--ff-only")));
                case "docker_ps" -> run("docker_ps", systemRepoRoot, Duration.ofSeconds(15), List.of("docker", "ps", "--format", "table {{.Names}}\t{{.Status}}\t{{.Ports}}"));
                case "docker_compose_ps" -> run("docker_compose_ps", dockerRoot(project, args), Duration.ofSeconds(15), dockerComposeCommand(dockerRoot(project, args), "ps"));
                case "docker_compose_logs" -> dockerComposeLogs(project, args);
                case "docker_compose_up" -> mutating(userMessage, tool, () -> run("docker_compose_up", dockerRoot(project, args), Duration.ofSeconds(90), dockerComposeCommand(dockerRoot(project, args), "up", "-d")));
                case "docker_compose_build" -> mutating(userMessage, tool, () -> dockerComposeBuild(project, args));
                case "http_get_local" -> httpGetLocal(args);
                case "detected_checks" -> testPlan(rootFor(project, args));
                case "run_detected_checks" -> mutating(userMessage, tool, () -> runDetectedChecks(rootFor(project, args)));
                case "orchestrate_project" -> mutating(userMessage, tool, () -> orchestrateProject(project));
                case "dispatch_project" -> mutating(userMessage, tool, () -> dispatchProject(project));
                case "maintenance_stuck" -> mutating(userMessage, tool, this::maintenanceStuck);
                case "add_wishlist" -> mutating(userMessage, tool, () -> addWishlist(project, args));
                case "project_memory_append" -> mutating(userMessage, tool, () -> projectMemoryAppend(project, args));
                case "run_command" -> runGenericCommand(project, args, userMessage);
                default -> new ToolObservation(tool, "unknown_tool", "Tool is not registered. Reason requested by Gemini: " + call.reason());
            };
        } catch (Exception e) {
            return new ToolObservation(tool, "error", e.getMessage());
        }
    }

    private ToolObservation projectMemoryRead(ProjectEntity project) {
        Path file = projectMemoryFile(project);
        if (!Files.isRegularFile(file)) {
            return new ToolObservation("project_memory_read", "empty",
                    "No durable project memory exists yet for project " + project.getId() + ".");
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            String text = new String(bytes, 0, Math.min(bytes.length, MAX_FILE_BYTES), StandardCharsets.UTF_8);
            return new ToolObservation("project_memory_read", "ok", trim(text));
        } catch (Exception e) {
            return new ToolObservation("project_memory_read", "error", e.getMessage());
        }
    }

    private ToolObservation projectMemoryAppend(ProjectEntity project, JsonNode args) {
        String note = textArg(args, "note", textArg(args, "content", ""));
        if (note.isBlank()) {
            return new ToolObservation("project_memory_append", "blocked", "note is required.");
        }
        Path file = projectMemoryFile(project);
        if (!file.startsWith(memoryRoot)) {
            return new ToolObservation("project_memory_append", "blocked", "Memory path escaped memory root.");
        }
        try {
            Files.createDirectories(memoryRoot);
            if (!Files.exists(file)) {
                Files.writeString(file,
                        "# Project Operator Memory\n\nProject: " + project.getName() + "\nProjectId: " + project.getId() + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
            }
            String entry = "\n- " + Instant.now() + " - " + sanitizeMemoryNote(note) + "\n";
            Files.writeString(file, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            return new ToolObservation("project_memory_append", "ok", "Appended durable memory note to " + file);
        } catch (Exception e) {
            return new ToolObservation("project_memory_append", "error", e.getMessage());
        }
    }

    private ToolObservation repoProfile(Path root) {
        if (!Files.isDirectory(root)) {
            return new ToolObservation("repo_profile", "missing", "Directory does not exist: " + root);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("root=").append(root).append("\n");
        builder.append("composeFile=").append(hasComposeFile(root)).append("\n");
        builder.append("manifestFiles:\n");
        for (String file : List.of(
                "README.md",
                "package.json",
                "pom.xml",
                "build.gradle",
                "settings.gradle",
                "pyproject.toml",
                "requirements.txt",
                "docker-compose.yml",
                "compose.yml",
                "Dockerfile"
        )) {
            Path candidate = root.resolve(file);
            if (Files.isRegularFile(candidate)) {
                builder.append("- ").append(file).append("\n");
                builder.append(compactFileSnippet(candidate, 2_500)).append("\n");
            }
        }
        ToolObservation tree = tree(root);
        builder.append("\nworkspace_tree:\n").append(tree.output());
        return new ToolObservation("repo_profile", "ok", trim(builder.toString()));
    }

    private ToolObservation findFiles(Path root, JsonNode args) {
        String glob = textArg(args, "glob", "");
        int limit = intArg(args, "limit", 80, 1, 300);
        List<String> command = new ArrayList<>(List.of(
                "rg", "--files", "--hidden",
                "--glob", "!.git",
                "--glob", "!node_modules",
                "--glob", "!target",
                "--glob", "!build",
                "--glob", "!dist"
        ));
        if (!glob.isBlank()) {
            command.add("--glob");
            command.add(glob);
        }
        ToolObservation raw = run("find_files", root, Duration.ofSeconds(12), command);
        String[] lines = raw.output().split("\\R");
        StringBuilder limited = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (line.startsWith("$ ") || line.isBlank()) {
                continue;
            }
            limited.append(line).append('\n');
            count++;
            if (count >= limit) {
                limited.append("... [limited to ").append(limit).append(" files]\n");
                break;
            }
        }
        String status = raw.status().equals("ok") || count > 0 ? "ok" : raw.status();
        return new ToolObservation("find_files", status, trim(limited.length() == 0 ? raw.output() : limited.toString()));
    }

    private ToolObservation readManyFiles(Path root, JsonNode args) {
        JsonNode paths = args.path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            return new ToolObservation("read_many_files", "blocked", "paths array is required.");
        }
        int maxBytesPerFile = intArg(args, "maxBytesPerFile", 8_000, 500, MAX_FILE_BYTES);
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (JsonNode item : paths) {
            if (count >= 12) {
                builder.append("\n... [limited to 12 files]\n");
                break;
            }
            String relativePath = item.asText("").trim();
            if (relativePath.isBlank()) {
                continue;
            }
            Path file = root.resolve(relativePath).normalize();
            builder.append("\n## ").append(relativePath).append("\n");
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                builder.append("missing\n");
                count++;
                continue;
            }
            try {
                byte[] bytes = Files.readAllBytes(file);
                builder.append(new String(bytes, 0, Math.min(bytes.length, maxBytesPerFile), StandardCharsets.UTF_8));
                if (bytes.length > maxBytesPerFile) {
                    builder.append("\n... [file truncated]\n");
                }
            } catch (Exception e) {
                builder.append("error: ").append(e.getMessage()).append('\n');
            }
            count++;
        }
        return new ToolObservation("read_many_files", count == 0 ? "empty" : "ok", trim(builder.toString()));
    }

    private ToolObservation contextFact(ProjectOperationalContext context, String tool, String section) {
        try {
            Object value = section == null ? context.facts() : context.facts().get(section);
            return new ToolObservation(tool, value == null ? "missing" : "ok", trim(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)));
        } catch (Exception e) {
            return new ToolObservation(tool, "error", e.getMessage());
        }
    }

    private Path projectMemoryFile(ProjectEntity project) {
        String key = project.getId() == null ? sanitizeFileName(project.getName()) : project.getId().toString();
        return memoryRoot.resolve(key + ".md").normalize();
    }

    private String sanitizeMemoryNote(String note) {
        return note == null ? "" : note.replace('\r', ' ').replace('\n', ' ').strip();
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown-project";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    }

    private String compactFileSnippet(Path file, int maxBytes) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String text = new String(bytes, 0, Math.min(bytes.length, maxBytes), StandardCharsets.UTF_8);
            String suffix = bytes.length > maxBytes ? "\n... [file truncated]" : "";
            return "--- " + file.getFileName() + " ---\n" + compact(text, maxBytes) + suffix;
        } catch (Exception e) {
            return "--- " + file.getFileName() + " ---\nerror: " + e.getMessage();
        }
    }

    private ToolObservation gitDiff(ProjectEntity project, JsonNode args) {
        Path root = rootFor(project, args);
        String path = textArg(args, "path", "");
        if (path.isBlank()) {
            return run("git_diff", root, Duration.ofSeconds(15), List.of("git", "diff"));
        }
        return run("git_diff", root, Duration.ofSeconds(15), List.of("git", "diff", "--", path));
    }

    private ToolObservation dockerComposeLogs(ProjectEntity project, JsonNode args) {
        Path root = dockerRoot(project, args);
        String tail = String.valueOf(intArg(args, "tail", 120, 20, 500));
        String service = textArg(args, "service", "");
        List<String> command = new ArrayList<>(dockerComposeCommand(root, "logs", "--tail", tail));
        if (!service.isBlank()) {
            command.add(service);
        }
        return run("docker_compose_logs", root, Duration.ofSeconds(30), command);
    }

    private ToolObservation dockerComposeBuild(ProjectEntity project, JsonNode args) {
        Path root = dockerRoot(project, args);
        String service = textArg(args, "service", "");
        List<String> command = new ArrayList<>(dockerComposeCommand(root, "build"));
        if (!service.isBlank()) {
            command.add(service);
        }
        return run("docker_compose_build", root, Duration.ofSeconds(240), command);
    }

    private ToolObservation httpGetLocal(JsonNode args) {
        String service = textArg(args, "service", "backend");
        String path = textArg(args, "path", "/health");
        if (!(path.equals("/health") || path.equals("/docs") || path.startsWith("/api/") || path.startsWith("/internal/"))) {
            return new ToolObservation("http_get_local", "blocked", "Only /health, /docs, /api/*, and /internal/* paths are allowed.");
        }
        String base = "ml".equalsIgnoreCase(service) ? "http://ml:8000" : "http://localhost:8080";
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(12)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ToolObservation("http_get_local", response.statusCode() >= 200 && response.statusCode() < 300 ? "ok" : "http_" + response.statusCode(),
                    "GET " + base + path + "\n" + trim(response.body()));
        } catch (Exception e) {
            return new ToolObservation("http_get_local", "error", e.getMessage());
        }
    }

    private ToolObservation orchestrateProject(ProjectEntity project) {
        OrchestrationResultDto result = projectFlowService.orchestrate(project.getId());
        return new ToolObservation("orchestrate_project", "ok",
                "processedWishlistItems=" + result.processedWishlistItems()
                        + ", createdTasks=" + result.createdTasks().size()
                        + ", message=" + result.message());
    }

    private ToolObservation dispatchProject(ProjectEntity project) {
        projectFlowService.dispatchQueuedTasks(project.getId());
        projectFlowService.dispatchReviewTasks(project.getId());
        return new ToolObservation("dispatch_project", "ok",
                "Ran dispatchQueuedTasks + dispatchReviewTasks for project " + project.getId());
    }

    private ToolObservation maintenanceStuck() {
        claimService.reapExpiredLeases();
        claimService.detectStuckSessions(stuckThresholdMinutes);
        return new ToolObservation("maintenance_stuck", "ok",
                "Ran reapExpiredLeases + detectStuckSessions(" + stuckThresholdMinutes + " minutes).");
    }

    private ToolObservation addWishlist(ProjectEntity project, JsonNode args) {
        String content = textArg(args, "content", "");
        if (content.isBlank()) {
            return new ToolObservation("add_wishlist", "blocked", "content is required.");
        }
        String sourceRoleTag = textArg(args, "sourceRoleTag", "BARCAN-TAG-09");
        var response = projectFlowService.addWishlistItem(
                project.getId(),
                new WishlistRequestDto(project.getId(), WishlistSource.role, sourceRoleTag, content)
        );
        return new ToolObservation("add_wishlist", "ok",
                "wishlistId=" + response.id() + ", status=" + response.status() + ", sourceRoleTag=" + response.sourceRoleTag());
    }

    private ToolObservation runGenericCommand(ProjectEntity project, JsonNode args, String userMessage) {
        Path root = rootFor(project, args);
        JsonNode rawCommand = args.path("command");
        if (!rawCommand.isArray() || rawCommand.isEmpty()) {
            return new ToolObservation("run_command", "blocked", "command array is required.");
        }
        List<String> command = new ArrayList<>();
        rawCommand.forEach(node -> command.add(node.asText("")));
        command.removeIf(String::isBlank);
        if (command.isEmpty()) {
            return new ToolObservation("run_command", "blocked", "command array is empty.");
        }
        if (!isAllowedOperatorCommand(command)) {
            return new ToolObservation("run_command", "blocked", "Command is outside the allowlist: " + String.join(" ", command));
        }
        if (isMutatingCommand(command)) {
            return mutating(userMessage, "run_command", () -> run("run_command", root,
                    Duration.ofSeconds(intArg(args, "timeoutSeconds", 30, 1, 240)),
                    command));
        }
        return run("run_command", root, Duration.ofSeconds(intArg(args, "timeoutSeconds", 30, 1, 240)), command);
    }

    private ToolObservation mutating(String userMessage, String tool, ToolAction action) {
        if (!allowMutatingTools) {
            return new ToolObservation(tool, "blocked", "Mutating operator tools are disabled by configuration.");
        }
        if (!isExplicitMutatingRequest(userMessage)) {
            return new ToolObservation(tool, "blocked",
                    "Mutating tool was not run because the user request was not an explicit action command.");
        }
        try {
            return action.run();
        } catch (Exception e) {
            return new ToolObservation(tool, "error", e.getMessage());
        }
    }

    private Path rootFor(ProjectEntity project, JsonNode args) {
        String root = textArg(args, "root", "project");
        if ("system".equalsIgnoreCase(root)) {
            return systemRepoRoot;
        }
        return resolveProjectWorkspace(project);
    }

    private Path dockerRoot(ProjectEntity project, JsonNode args) {
        Path requested = rootFor(project, args);
        return hasComposeFile(requested) ? requested : systemRepoRoot;
    }

    private String textArg(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        return node.get(field).asText(fallback).trim();
    }

    private int intArg(JsonNode node, String field, int fallback, int min, int max) {
        int value = node != null && node.has(field) ? node.get(field).asInt(fallback) : fallback;
        return Math.max(min, Math.min(max, value));
    }

    private String extractJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        String clean = value.trim();
        if (clean.startsWith("```")) {
            clean = clean.replaceFirst("(?s)^```[a-zA-Z]*\\s*", "").replaceFirst("(?s)\\s*```$", "").trim();
        }
        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return clean.substring(start, end + 1);
        }
        return clean;
    }

    private boolean wantsSystemRoot(String lower) {
        return containsAny(lower,
                "eneik", "production system", "management system", "this software",
                "\u044d\u0442\u043e\u0442 \u0441\u043e\u0444\u0442", "\u044d\u0442\u043e\u0433\u043e \u0441\u043e\u0444\u0442\u0430",
                "\u0441\u0430\u043c \u0441\u043e\u0444\u0442", "\u0441\u0438\u0441\u0442\u0435\u043c");
    }

    private boolean isExplicitMutatingRequest(String userMessage) {
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "run ", "start ", "create ", "add ", "execute ", "build ", "pull ", "dispatch", "orchestrate",
                "remember", "save ", "persist ",
                "\u0437\u0430\u043f\u0443\u0441\u0442\u0438", "\u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u044c",
                "\u0441\u0434\u0435\u043b\u0430\u0439", "\u0441\u043e\u0437\u0434\u0430\u0439", "\u0441\u043e\u0437\u0434\u0430\u0442\u044c",
                "\u0434\u043e\u0431\u0430\u0432\u044c", "\u0434\u043e\u0431\u0430\u0432\u0438\u0442\u044c",
                "\u043e\u0440\u043a\u0435\u0441\u0442\u0440", "\u0434\u0438\u0441\u043f\u0430\u0442\u0447",
                "\u043f\u043e\u0434\u043d\u0438\u043c\u0438", "\u0441\u0431\u0435\u0440\u0438", "\u0431\u0438\u043b\u0434",
                "\u0437\u0430\u043f\u043e\u043c\u043d\u0438", "\u0441\u043e\u0445\u0440\u0430\u043d\u0438");
    }

    private boolean isAllowedOperatorCommand(List<String> command) {
        String first = command.get(0);
        return List.of("git", "docker", "npm", "node", "mvn", "./mvnw", "python", "python3", "pytest", "rg", "ls", "pwd").contains(first);
    }

    private boolean isMutatingCommand(List<String> command) {
        String joined = String.join(" ", command).toLowerCase(Locale.ROOT);
        if (joined.contains(" status") || joined.contains(" diff") || joined.contains(" log")
                || joined.contains(" show") || joined.contains(" ps") || joined.contains(" logs")
                || joined.startsWith("rg ") || joined.equals("pwd") || joined.startsWith("ls")) {
            return false;
        }
        return true;
    }

    private Path resolveProjectWorkspace(ProjectEntity project) {
        if (project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
            Path fromDb = Paths.get(project.getWorkspacePath()).toAbsolutePath().normalize();
            if (isAllowedRoot(fromDb)) {
                return fromDb;
            }
        }
        String slug = project.getSlug() == null ? project.getRepositoryName() : project.getSlug();
        return workspaceRoot.resolve(slug == null ? "unknown-project" : slug).normalize();
    }

    private boolean hasComposeFile(Path root) {
        return root != null
                && Files.isDirectory(root)
                && (Files.exists(root.resolve("docker-compose.yml"))
                || Files.exists(root.resolve("compose.yml"))
                || Files.exists(root.resolve("docker-compose.yaml"))
                || Files.exists(root.resolve("compose.yaml")));
    }

    private ToolObservation tree(Path root) {
        if (!Files.isDirectory(root)) {
            return new ToolObservation("workspace_tree", "missing", "Directory does not exist: " + root);
        }
        try (Stream<Path> stream = Files.walk(root, 4)) {
            String output = stream
                    .filter(path -> !path.equals(root))
                    .filter(path -> !isExcluded(path))
                    .sorted(Comparator.naturalOrder())
                    .limit(160)
                    .map(root::relativize)
                    .map(Path::toString)
                    .reduce("", (a, b) -> a + b + "\n");
            return new ToolObservation("workspace_tree", "ok", trim(output));
        } catch (Exception e) {
            return new ToolObservation("workspace_tree", "error", e.getMessage());
        }
    }

    private ToolObservation readIfExists(Path root, String relativePath) {
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return new ToolObservation("read_" + relativePath, "missing", relativePath + " not found");
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            String text = new String(bytes, 0, Math.min(bytes.length, MAX_FILE_BYTES), StandardCharsets.UTF_8);
            return new ToolObservation("read_" + relativePath, "ok", trim(text));
        } catch (Exception e) {
            return new ToolObservation("read_" + relativePath, "error", e.getMessage());
        }
    }

    private ToolObservation search(Path root, List<String> terms) {
        if (terms.isEmpty()) {
            return new ToolObservation("code_search", "skipped", "No concrete search terms extracted from the request.");
        }
        if (!Files.isDirectory(root)) {
            return new ToolObservation("code_search", "missing", "Directory does not exist: " + root);
        }
        StringBuilder result = new StringBuilder();
        int matchedTerms = 0;
        for (String term : terms.stream().filter(value -> value != null && !value.isBlank()).limit(5).toList()) {
            List<String> command = List.of(
                    "rg", "-n", "--hidden",
                    "--glob", "!.git",
                    "--glob", "!node_modules",
                    "--glob", "!target",
                    "--glob", "!build",
                    "--glob", "!dist",
                    "--fixed-strings",
                    "--",
                    term
            );
            ToolObservation observation = run("code_search", root, Duration.ofSeconds(15), command);
            if ("ok".equals(observation.status())) {
                matchedTerms++;
            }
            result.append("## query: ").append(term).append(" [").append(observation.status()).append("]\n")
                    .append(observation.output()).append("\n\n");
            if (result.length() > MAX_TOOL_OUTPUT) {
                break;
            }
        }
        return new ToolObservation("code_search", matchedTerms == 0 ? "empty" : "ok", trim(result.toString()));
    }

    private ToolObservation testPlan(Path root) {
        List<String> checks = new ArrayList<>();
        if (Files.exists(root.resolve("package.json"))) {
            checks.add("Node: npm test --if-present; npm run build --if-present");
        }
        if (Files.exists(root.resolve("pom.xml"))) {
            checks.add("Java: ./mvnw test or mvn test");
        }
        if (Files.exists(root.resolve("requirements.txt")) || Files.exists(root.resolve("pyproject.toml"))) {
            checks.add("Python: python -m pytest");
        }
        if (Files.exists(root.resolve("docker-compose.yml")) || Files.exists(root.resolve("compose.yml"))) {
            checks.add("Docker: docker compose ps/logs/up -d when explicitly requested");
        }
        if (checks.isEmpty()) {
            return new ToolObservation("detected_checks", "empty", "No package.json, pom.xml, pyproject.toml, requirements.txt, or compose file detected at " + root);
        }
        return new ToolObservation("detected_checks", "ok", String.join("\n", checks));
    }

    private ToolObservation runDetectedChecks(Path root) {
        if (Files.exists(root.resolve("package.json"))) {
            return run("npm_test", root, Duration.ofSeconds(90), List.of("npm", "test", "--if-present"));
        }
        if (Files.exists(root.resolve("pom.xml"))) {
            if (Files.exists(root.resolve("mvnw"))) {
                return run("maven_test", root, Duration.ofSeconds(120), List.of("./mvnw", "test"));
            }
            return run("maven_test", root, Duration.ofSeconds(120), List.of("mvn", "test"));
        }
        if (Files.exists(root.resolve("requirements.txt")) || Files.exists(root.resolve("pyproject.toml"))) {
            return run("pytest", root, Duration.ofSeconds(120), List.of("python", "-m", "pytest"));
        }
        return new ToolObservation("run_detected_checks", "skipped", "No supported test runner detected.");
    }

    private ToolObservation run(String name, Path cwd, Duration timeout, List<String> command) {
        if (!Files.isDirectory(cwd)) {
            return new ToolObservation(name, "missing", "Working directory does not exist: " + cwd);
        }
        if (!isAllowedRoot(cwd)) {
            return new ToolObservation(name, "blocked", "Working directory is outside allowed operator roots: " + cwd);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(cwd.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            boolean[] truncated = new boolean[] {false};
            Thread readerThread = new Thread(() -> readProcessOutput(process, output, truncated), "project-operator-output-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                readerThread.join(1_000);
                return new ToolObservation(name, "timeout", "Command timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command)
                        + "\nPartial output:\n" + trim(output.toString()));
            }
            readerThread.join(1_000);
            String status = process.exitValue() == 0 ? "ok" : "failed_exit_" + process.exitValue();
            String renderedOutput = trim(output.toString());
            if (truncated[0]) {
                renderedOutput += "\n... [truncated]";
            }
            return new ToolObservation(name, status, "$ " + String.join(" ", command) + "\n" + renderedOutput);
        } catch (Exception e) {
            return new ToolObservation(name, "error", "$ " + String.join(" ", command) + "\n" + e.getMessage());
        }
    }

    private void readProcessOutput(Process process, StringBuilder output, boolean[] truncated) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < MAX_TOOL_OUTPUT) {
                    int remaining = MAX_TOOL_OUTPUT - output.length();
                    output.append(line, 0, Math.min(line.length(), remaining)).append('\n');
                    if (line.length() > remaining) {
                        truncated[0] = true;
                    }
                } else {
                    truncated[0] = true;
                }
            }
        } catch (Exception ignored) {
            // Tool output is best-effort evidence; the command status is reported by run().
        }
    }

    private List<String> dockerComposeCommand(Path cwd, String... args) {
        List<String> command = new ArrayList<>(List.of("docker", "compose"));
        if (!dockerComposeProjectName.isBlank() && cwd.toAbsolutePath().normalize().startsWith(systemRepoRoot)) {
            command.add("-p");
            command.add(dockerComposeProjectName);
        }
        command.addAll(List.of(args));
        return command;
    }

    private boolean isAllowedRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(workspaceRoot) || normalized.startsWith(systemRepoRoot);
    }

    private boolean isExcluded(Path path) {
        String value = path.toString().replace('\\', '/');
        return value.contains("/.git/")
                || value.contains("/node_modules/")
                || value.contains("/target/")
                || value.contains("/build/")
                || value.contains("/dist/")
                || value.contains("/coverage/")
                || value.contains("/playwright-report/")
                || value.contains("/test-results/")
                || value.contains("/.next/");
    }

    private List<String> searchTerms(String lowerRequest) {
        List<String> terms = new ArrayList<>();
        for (String candidate : List.of(
                "docker", "compose", "github", "jules", "gemini", "playwright", "test", "auth",
                "controller", "service", "repository", "task", "project", "wishlist", "management"
        )) {
            if (lowerRequest.contains(candidate)) {
                terms.add(candidate);
            }
        }
        return terms.stream().distinct().limit(6).toList();
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String valueOrUnset(String value) {
        return value == null || value.isBlank() ? "<unset>" : value;
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String clean = value.strip();
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, maxLength) + "\n... [truncated]";
    }

    private String trim(String value) {
        return compact(value, MAX_TOOL_OUTPUT);
    }

    private record ToolObservation(String tool, String status, String output) {}

    private record OperatorToolPlan(boolean valid, List<OperatorToolCall> calls, String summary) {}

    private record OperatorToolCall(String tool, JsonNode args, String reason) {}

    @FunctionalInterface
    private interface ToolAction {
        ToolObservation run() throws Exception;
    }

    private record OperatorEvidence(List<ToolObservation> observations) {
        String toPrompt() {
            StringBuilder builder = new StringBuilder();
            for (ToolObservation observation : observations) {
                builder.append("## ").append(observation.tool()).append(" [").append(observation.status()).append("]\n")
                        .append(observation.output()).append("\n\n");
            }
            return builder.toString();
        }

        String fallbackAnswer() {
            return "\u041e\u043f\u0435\u0440\u0430\u0442\u043e\u0440 \u0441\u043e\u0431\u0440\u0430\u043b evidence, \u043d\u043e \u043c\u043e\u0434\u0435\u043b\u044c \u043d\u0435 \u0432\u0435\u0440\u043d\u0443\u043b\u0430 \u043e\u0442\u0432\u0435\u0442. \u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0435\u0435 \u043d\u0430\u0431\u043b\u044e\u0434\u0435\u043d\u0438\u0435: "
                    + (observations.isEmpty() ? "\u043d\u0435\u0442 \u043d\u0430\u0431\u043b\u044e\u0434\u0435\u043d\u0438\u0439" : observations.get(observations.size() - 1).tool()
                    + " [" + observations.get(observations.size() - 1).status() + "]");
        }
    }
}
