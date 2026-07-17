package com.eneik.production.services.dashboard;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.OrchestrationCooldownException;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.video.VideoAssetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final GitHubPullRequestService gitHubPullRequestService;
    private final GoogleAiResourceService googleAiResourceService;
    private final DesignAssetService designAssetService;
    private final VideoAssetService videoAssetService;
    private final SystemSettingsService settingsService;
    private final Path workspaceRoot;
    private final Path systemRepoRoot;
    private final Path memoryRoot;
    private final boolean allowMutatingTools;
    private final String dockerComposeProjectName;
    private final String githubOrganization;
    private final int stuckThresholdMinutes;
    private final int maxToolRounds;
    private final boolean answerCriticEnabled;
    private final ObjectMapper objectMapper;

    public ProjectOperatorService(ProjectRepository projectRepository,
                                  ProjectOperationalContextService contextService,
                                  MLPredictionServiceClient mlPredictionServiceClient,
                                  ProjectFlowService projectFlowService,
                                  ClaimService claimService,
                                  GitHubPullRequestService gitHubPullRequestService,
                                  GoogleAiResourceService googleAiResourceService,
                                  DesignAssetService designAssetService,
                                  VideoAssetService videoAssetService,
                                  SystemSettingsService settingsService,
                                  @Value("${project-factory.workspace-root:./project-workspaces}") String workspaceRoot,
                                  @Value("${eneik.operator.system-repo-root:}") String systemRepoRoot,
                                  @Value("${eneik.operator.memory-root:./data/operator-memory}") String memoryRoot,
                                  @Value("${eneik.operator.allow-mutating-tools:false}") boolean allowMutatingTools,
                                  @Value("${eneik.operator.docker-compose-project-name:}") String dockerComposeProjectName,
                                  @Value("${github.org:eneikcoworking-ctrl}") String githubOrganization,
                                  @Value("${jules.stuck-threshold-minutes:30}") int stuckThresholdMinutes,
                                  @Value("${eneik.operator.max-tool-rounds:3}") int maxToolRounds,
                                  @Value("${eneik.operator.answer-critic-enabled:true}") boolean answerCriticEnabled,
                                  ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.contextService = contextService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.projectFlowService = projectFlowService;
        this.claimService = claimService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.googleAiResourceService = googleAiResourceService;
        this.designAssetService = designAssetService;
        this.videoAssetService = videoAssetService;
        this.settingsService = settingsService;
        this.workspaceRoot = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        this.systemRepoRoot = (systemRepoRoot == null || systemRepoRoot.isBlank())
                ? Paths.get(".").toAbsolutePath().normalize()
                : Paths.get(systemRepoRoot).toAbsolutePath().normalize();
        this.memoryRoot = Paths.get(memoryRoot).toAbsolutePath().normalize();
        this.allowMutatingTools = allowMutatingTools;
        this.dockerComposeProjectName = dockerComposeProjectName == null ? "" : dockerComposeProjectName.trim();
        this.githubOrganization = githubOrganization == null || githubOrganization.isBlank()
                ? "eneikcoworking-ctrl"
                : githubOrganization.trim();
        this.stuckThresholdMinutes = stuckThresholdMinutes;
        this.maxToolRounds = Math.max(1, Math.min(5, maxToolRounds));
        this.answerCriticEnabled = answerCriticEnabled;
        this.objectMapper = objectMapper;
    }

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
                - Lean and TOC help project decisions. Six Sigma is EMS production telemetry for system makers; do not make it the project operator's main problem unless the user asks about production quality.
                - Roles are lenses and ownership tags. They help choose the next expert perspective; they never limit doing necessary Eneik Management System work.
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
                - Do not say "waiting for tasks" as an operational answer unless you can name the exact task, owner account, and role. If the next step has no concrete task, create or recommend the precise English wishlist/work item that should be compiled next.
                - Missing repository, empty workspace, absent .git, unknown setup commands, or unclear backend/frontend boundaries are bootstrap work. Treat them as one of the first project tasks, not as a human operator problem.
                - Recorded failed attempts are internal defect counter evidence only. Do not present them as active project work. Do not mention those totals unless the user explicitly asks for production defect accounting.
                - Closed-but-unmerged PRs are historical scrap/COPQ evidence, not open work. Never tell the user to close closed PRs again. Only live open PRs are actionable.
                - The deep diagnostic worker routes bounded code diagnostics to Jules (repository-level investigation, local/build/test repair, and diagnostic branch artifacts); never treat it as a chat answer substitute.
                - Google AI resources are operator tools, not decoration: use grounded research for fresh facts, URL Context for cited pages, and Design Asset Service for visual assets. If a visual brief is unclear, first create a BARCAN-TAG-03 or BARCAN-TAG-09 wishlist item for content planning, then generate the asset.
                - For project planning, focus on current project state: workspace, live open PRs, queued/review work, active sessions, API/account availability, and the next dispatchable task.
                - Open unmerged PRs are WIP/integration debt, not proof of forward progress. When the user explicitly asks to close stale PRs or restart the flow, use the PR cleanup tool and then recover/dispatch fresh work.
                - Do not say you lack analytical tools while project facts contain tasks, sessions, wishlist, accounts, PRs, conflicts, or EMS metrics. If bottleneck detection is empty, analyze queue/review/done balance, session age, PR review state, account API state, and dispatchability instead.
                - You may say you executed an action only when a named mutating tool in OPERATOR_EVIDENCE has status [ok] or [partial].
                - If a command was not run, say it was not run and why.
                - If a tool is unavailable, say so and give the nearest verifiable next step.
                - If a requested mutating tool is ok or partial, report exactly what completed and what did not complete; do not ask for confirmation for the completed part.
                - Do not say the Technical Lead must convert wishlist items when orchestrate_project or start_testing_stream has already run orchestration.
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
        if (answer == null || answer.isBlank() || isAiFailureResponse(answer)) {
            return sanitizeFinalAnswer(deterministicEvidenceAnswer(project, context, evidence, userMessage));
        }
        if (looksLikeOperatorExecutionIntent(userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT))) {
            return sanitizeFinalAnswer(deterministicEvidenceAnswer(project, context, evidence, userMessage));
        }
        String reviewed = reviewAnswerWithCritic(project, context, evidence, userMessage, answer);
        if (reviewed == null || reviewed.isBlank() || isAiFailureResponse(reviewed)) {
            return sanitizeFinalAnswer(deterministicEvidenceAnswer(project, context, evidence, userMessage));
        }
        return sanitizeFinalAnswer(enforceOperatorEvidence(userMessage, evidence, reviewed));
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
                - It says the system is waiting for tasks without naming a task, owner account, and role, or without proposing/using a concrete wishlist/orchestration action.
                - It treats BARCAN roles as permission limits instead of thinking lenses and ownership tags.
                - It claims analytical capability is missing while available project facts or tool observations include tasks, sessions, PRs, accounts, EMS metrics, Six Sigma, conflicts, or wishlist evidence.

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
        if (response == null || response.isBlank() || isAiFailureResponse(response)) {
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

    private String sanitizeFinalAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String sanitized = answer.strip()
                .replace("PROJECT_FACT_PACK", "current project evidence")
                .replace("OPERATOR_EVIDENCE", "operator evidence")
                .replace("Project Fact Pack", "current project evidence")
                .replace("Operator Evidence", "operator evidence")
                .replace("the collected operator evidence", "operator evidence")
                .replace("the selected project facts", "current project evidence");
        sanitized = sanitized.replaceFirst("(?iu)^(hello|hi|hey|привет|приветствую)[!,.\\s:-]+", "");
        sanitized = sanitized.replaceFirst("(?iu)^я\\s+[-—]?\\s*(твой|ваш)?\\s*ии[-\\s]?ассистент[^.?!]*[.?!]\\s*", "");
        return sanitized.strip();
    }

    private String enforceOperatorEvidence(String userMessage, OperatorEvidence evidence, String answer) {
        Optional<ToolObservation> steward = evidence.observation("autonomous_flow_steward");
        if (steward.isEmpty()) {
            return answer;
        }
        String lower = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        boolean contradictsEvidence = lower.contains("no mutating action")
                || lower.contains("no mutating actions")
                || lower.contains("not executed")
                || lower.contains("lack of explicit command")
                || lower.contains("explicit command")
                || lower.contains("upon request")
                || lower.contains("\u043d\u0435 \u0432\u044b\u043f\u043e\u043b\u043d")
                || lower.contains("\u043d\u0435 \u0437\u0430\u043f\u0443\u0449")
                || lower.contains("\u043f\u043e \u0437\u0430\u043f\u0440\u043e\u0441\u0443");
        if (!contradictsEvidence) {
            return answer;
        }
        String output = compact(steward.get().output(), 1_700);
        return "The autonomous EMS steward has already run from project evidence. This is not waiting for an explicit command. Result:\n\n"
                + output;
    }

    private boolean isAiFailureResponse(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.contains("api error")
                || lower.contains("gemini candidate models failed")
                || lower.contains("resource_exhausted")
                || lower.contains("quota exceeded")
                || lower.contains("assistant is temporarily unavailable")
                || lower.contains("ml service connection error")
                || lower.contains("\u043e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u043e\u0431\u0440\u0430\u0449\u0435\u043d\u0438\u0438 \u043a gemini");
    }

    private String deterministicEvidenceAnswer(ProjectEntity project,
                                               ProjectOperationalContext context,
                                               OperatorEvidence evidence,
                                               String userMessage) {
        JsonNode tasks = factNode(context, "tasks");
        JsonNode accounts = factNode(context, "accountsAvailableForProject");
        JsonNode github = factNode(context, "githubPullRequestsLive");
        JsonNode sessions = factNode(context, "julesSessions");
        StringBuilder output = new StringBuilder();

        boolean executionIntent = looksLikeOperatorExecutionIntent(userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT));
        output.append(executionIntent
                ? "Action accepted and executed by the Project Operator.\n"
                : "Project Operator used deterministic evidence because the model answer was unavailable or unsafe.\n");
        output.append("Project: ").append(project.getName()).append(" (").append(project.getId()).append(")\n");
        output.append("Current project flow: queued=").append(tasks.path("countsByStatus").path("queued").asText("0"))
                .append(", review=").append(tasks.path("countsByStatus").path("review").asText("0"))
                .append(", done=").append(tasks.path("countsByStatus").path("done").asText("0"))
                .append(", spikeCompleted=").append(tasks.path("countsByStatus").path("spike_completed").asText("0"))
                .append(".\n");
        output.append("GitHub: openPRs=").append(github.path("openCount").asText("NOT_AVAILABLE"))
                .append(", closedPRs=").append(github.path("closedCount").asText("NOT_AVAILABLE"))
                .append(". Closed-unmerged PRs are historical scrap, not open work.\n");
        output.append("Jules accounts: effectiveOperational=").append(accounts.path("effectiveOperational").asText("NOT_AVAILABLE"))
                .append(", apiBlocked=").append(accounts.path("apiBlocked").asText("0"))
                .append(", dailyLimited=").append(accounts.path("dailyLimited").asText("0"))
                .append(".\n");
        output.append("Jules sessions: running=").append(sessions.path("running").asText("0"))
                .append(", stuck=").append(sessions.path("stuck").asText("0"))
                .append(", failed=").append(sessions.path("failed").asText("0"))
                .append(".\n\n");

        output.append("Executed/collected evidence:\n");
        evidence.observations().stream()
                .filter(observation -> !List.of("operator_scope", "project_memory_read").contains(observation.tool()))
                .limit(8)
                .forEach(observation -> output.append("- ")
                        .append(displayToolName(observation.tool()))
                        .append(" [").append(observation.status()).append("]: ")
                        .append(cleanUserVisibleEvidence(compact(observation.output(), 420)).replace('\n', ' '))
                        .append('\n'));

        output.append("\nNext project focus: keep moving only live project work: workspace readiness, live open PRs, queued/review dispatch, and API/account recovery.");
        return trim(output.toString());
    }

    private String displayToolName(String tool) {
        if ("recover_blocked_flow".equals(tool)) {
            return "recovery_flow";
        }
        if ("six_sigma_pareto".equals(tool)) {
            return "production_quality_telemetry";
        }
        return tool;
    }

    private String cleanUserVisibleEvidence(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("recover_blocked_flow", "recovery_flow")
                .replace("blocked work item(s)", "failed attempt(s)")
                .replace("blocked tasks", "failed attempts")
                .replace("Blocked tasks", "Failed attempts")
                .replaceAll("Recorded \\d+ failed attempt\\(s\\) as production defect evidence,\\s*", "")
                .replace("Old failed attempts are not active project blockers.", "Historical failed attempts are ignored for project execution.");
    }

    private OperatorEvidence runGeminiToolLoop(ProjectEntity project, ProjectOperationalContext context, String userMessage) {
        List<ToolObservation> observations = new ArrayList<>();
        observations.add(operatorScope(project, userMessage));
        observations.add(projectMemoryRead(project));
        Set<String> executedSignatures = new LinkedHashSet<>();
        boolean executedPlannedTool = false;
        boolean operatorExecutionIntent = looksLikeOperatorExecutionIntent(userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT));

        if (shouldRunAutonomousSteward(context)) {
            observations.add(autonomousFlowSteward(project, context));
            executedSignatures.add("autonomous_flow_steward::{}");
            executedPlannedTool = true;
        }

        ToolObservation roleToolRouting = routeRoleDrivenTools(project, context);
        if (!"idle".equals(roleToolRouting.status())) {
            observations.add(roleToolRouting);
            executedSignatures.add("role_tool_router::{}");
            executedPlannedTool = true;
        }

        for (OperatorToolCall call : routeOperatorIntents(project, userMessage)) {
            String signature = toolSignature(call);
            if (executedSignatures.contains(signature)) {
                continue;
            }
            executedSignatures.add(signature);
            observations.add(new ToolObservation("intent_router", "ok",
                    "Routed user request to deterministic operator tool: " + signature));
            observations.add(executeToolCall(project, context, userMessage, call));
            executedPlannedTool = true;
        }

        if (operatorExecutionIntent) {
            return new OperatorEvidence(observations);
        }

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
                - For explicit production-quality, defects, waste, DPMO, COPQ, or Six Sigma questions, request six_sigma_pareto and operator_waste_guard.
                - For PR review, merge readiness, conflicts, or "what can be merged" questions, request pr_review_and_merge_plan.
                - For Jules loops, repeated questions, stale sessions, unanswered activity, or "why nobody answers" questions, request jules_activity_triage. If the user asks to fix it, request close_bad_session or recover_blocked_flow.
                - For "waiting for tasks" or no concrete next task, create_atomic_wishlist then compile_dependency_graph; dispatch_next_best_task when the user asked to start/continue work.
                - Do not request mutating tools for explanation questions. Explanation questions include "how does it work", "why", "what happened", "what do you see", "recommend".
                - Request mutating tools only when the user explicitly asks to run, create, add, orchestrate, dispatch, start, build, pull, or execute.
                - If the project workspace is missing, empty, or not a Git repository and the user asks to fix/repair/clone it, request ensure_project_workspace.
                - If repository setup, local environment, .git, workspace, backend/frontend boundary, or setup commands are missing and the user asks to fix/start/continue, request ensure_project_workspace and ensure_environment_bootstrap_work.
                - If the user asks to start testing work, request start_testing_stream instead of only explaining testing theory.
                - If the user asks for deep repository diagnostics, local code repair, root-cause investigation, or a diagnostic branch, request deep_diagnostic_worker.
                - If the user asks which Google/Gemini tools are available or how resources are configured, request google_ai_resource_catalog.
                - If the user asks for current external facts, market/legal/technical research, competitor context, or fresh documentation, request google_search_research.
                - If the user gives a URL and asks to analyze it, request url_context_research.
                - If the user explicitly asks to generate, create, or produce a visual asset, banner, hero image, mockup, icon, or site image, request design_asset_generate. If the brief is vague, first request create_atomic_wishlist for BARCAN-TAG-03 content planning.
                - If the user explicitly asks to generate, create, or produce a demo video, promo video, onboarding video, walkthrough, product tour, or explainer video, request video_asset_generate.
                - If the user asks to resume, continue, unblock, recover, replace failed work, or create new corrected tasks after failed Jules work, request recover_blocked_flow. Do not ask the user to choose task IDs.
                - If the next step is described only as "waiting for tasks" and no concrete task/owner/role exists, request add_wishlist with a short English atomic work item, then orchestrate_project and dispatch_project when the user explicitly asked to act.
                - Do not use Six Sigma alone as a reason to stop project work; it is production telemetry for the EMS makers, not the project operator's execution concern.
                - If the user asks about stale, unmerged, useless, noisy, or cleanup PRs, request close_unmerged_pull_requests before recovery/dispatch.
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

    private List<OperatorToolCall> routeOperatorIntents(ProjectEntity project, String userMessage) {
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        List<OperatorToolCall> calls = new ArrayList<>();
        if (looksLikeOperatorExecutionIntent(lower)) {
            addRoutedCall(calls, "autonomous_flow_steward",
                    "User confirmed or asked to continue execution; run the project recovery/dispatch sequence without asking for another confirmation.");
        }
        if (looksLikeDeepDiagnosticIntent(lower)) {
            addRoutedCall(calls, "deep_diagnostic_worker",
                    "User asked for deep engineering diagnostics or a diagnostic branch artifact.");
        }
        if (looksLikeGoogleAiResourceIntent(lower)) {
            addRoutedCall(calls, "google_ai_resource_catalog",
                    "User asked about Gemini/Google AI resource availability or integration model.");
        }
        if (looksLikeGroundedResearchIntent(lower)) {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("question", userMessage == null ? "" : userMessage);
            calls.add(new OperatorToolCall("google_search_research", args,
                    "User asked for fresh external research or current Google-grounded facts."));
        }
        if (looksLikeUrlContextIntent(lower)) {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("question", userMessage == null ? "" : userMessage);
            args.put("url", firstUrl(userMessage == null ? "" : userMessage));
            calls.add(new OperatorToolCall("url_context_research", args,
                    "User asked to analyze a concrete URL with URL Context."));
        }
        if (looksLikeDesignAssetIntent(lower)) {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("brief", userMessage == null ? "" : userMessage);
            args.put("assetType", inferredAssetType(lower));
            args.put("quality", containsAny(lower, "pro", "brand", "бренд", "премиум") ? "pro" : "fast");
            args.put("useGoogleSearch", containsAny(lower, "current", "fresh", "google", "market", "конкур", "рынок"));
            calls.add(new OperatorToolCall("design_asset_generate", args,
                    "User explicitly asked to generate a visual design asset."));
        }
        if (looksLikeVideoAssetIntent(lower)) {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("brief", userMessage == null ? "" : userMessage);
            args.put("assetType", "project_video");
            args.put("quality", containsAny(lower, "pro", "brand", "premium", "cinematic") ? "pro" : "standard");
            args.put("useGoogleSearch", containsAny(lower, "current", "fresh", "google", "market", "competitor"));
            calls.add(new OperatorToolCall("video_asset_generate", args,
                    "User explicitly asked to generate a video asset."));
        }
        if (looksLikeWorkspaceRepairIntent(project, lower)) {
            addRoutedCall(calls, "ensure_project_workspace",
                    "User asked to repair the project workspace or the workspace is not a Git repository.");
        }
        if (looksLikeEnvironmentBootstrapIntent(lower)) {
            addRoutedCall(calls, "ensure_project_workspace",
                    "Environment/bootstrap intent requires verifying or repairing the project workspace.");
            addRoutedCall(calls, "ensure_environment_bootstrap_work",
                    "Environment and repository boundary must be converted into concrete EMS bootstrap work.");
            addRoutedCall(calls, "dispatch_project",
                    "Bootstrap work should be dispatched when the user asks the operator to act.");
        }
        if (looksLikeQualityWasteIntent(lower)) {
            addRoutedCall(calls, "six_sigma_pareto",
                    "Quality/waste intent requires project-only Six Sigma Pareto evidence.");
            addRoutedCall(calls, "operator_waste_guard",
                    "Quality/waste intent requires EMS waste guard decisions.");
        }
        if (looksLikePrCleanupIntent(lower)) {
            addRoutedCall(calls, "pr_review_and_merge_plan",
                    "PR cleanup requires live PR evidence before changing GitHub state.");
            ObjectNode args = objectMapper.createObjectNode();
            args.put("reason", "Explicit operator PR cleanup: close unmerged WIP PRs before restarting the project flow.");
            calls.add(new OperatorToolCall("close_unmerged_pull_requests", args,
                    "Unmerged WIP PRs should be closed instead of treated as forward progress."));
        }
        if (looksLikeJulesTriageIntent(lower)) {
            addRoutedCall(calls, "jules_activity_triage",
                    "Jules loop or unanswered activity requires session triage before recommendations.");
        }
        if (looksLikeStartTestingIntent(lower)) {
            addRoutedCall(calls, "start_testing_stream",
                    "User explicitly asked to start project testing tasks.");
        }
        if (looksLikeBlockedRecoveryIntent(lower)) {
            addRoutedCall(calls, "recover_blocked_flow",
                    "User explicitly asked to resume or replace failed Jules work without manual task selection.");
        }
        return calls;
    }

    private ToolObservation routeRoleDrivenTools(ProjectEntity project, ProjectOperationalContext context) {
        if (!allowMutatingTools) {
            return new ToolObservation("role_tool_router", "idle", "Role-driven tool routing is idle because mutating operator tools are disabled.");
        }
        JsonNode roles = factNode(context, "emsMetrics").path("roleDoctrineReadiness").path("roles");
        if (!roles.isArray() || roles.isEmpty()) {
            return new ToolObservation("role_tool_router", "idle", "No role doctrine readiness evidence is available.");
        }

        StringBuilder output = new StringBuilder();
        output.append("Role-driven tool router engaged.\n")
                .append("Script: role doctrine demand -> Kano/Cynefin pressure -> tool choice -> bounded action.\n")
                .append("Tool rules:\n")
                .append("- BARCAN-TAG-03/11 visual evidence demand -> Nano Banana design asset.\n")
                .append("- BARCAN-TAG-03/09/11 demo, promo, onboarding, walkthrough demand -> Veo video asset.\n")
                .append("- BARCAN-TAG-00/01/02/05/06/07/08/10 technical refusal, defect, or chaotic/complicated root-cause demand -> deep diagnostic worker (Jules).\n")
                .append("- Unknown role doctrine evidence -> short English role attack wishlist, then dependency compilation.\n")
                .append("- Existing source-role pending wishlist without owner work -> compile dependency graph and dispatch next best task.\n\n");

        int actions = 0;
        int maxActions = 3;
        boolean compileAndDispatch = false;

        for (JsonNode role : roles) {
            if (actions >= maxActions) {
                break;
            }
            String stance = role.path("stance").asText("");
            if (!Set.of("refuses", "objects", "unknown").contains(stance)) {
                continue;
            }
            String roleTag = role.path("roleTag").asText("");
            String demand = roleDemandText(context, role);
            String demandLower = demand.toLowerCase(Locale.ROOT);
            long ownerOpen = role.path("ownerTasksOpen").asLong(0);
            long sourcePending = role.path("sourceWishlistPending").asLong(0);
            long sourceTotal = role.path("sourceWishlistTotal").asLong(0);
            long ownerTotal = role.path("ownerTasksTotal").asLong(0);

            if ("unknown".equals(stance) && sourceTotal == 0 && ownerTotal == 0) {
                String routeKey = routeKey(project, roleTag, "role_attack", demand);
                if (!alreadyRouted(project, routeKey)) {
                    ObjectNode args = objectMapper.createObjectNode();
                    args.put("sourceRoleTag", roleTag);
                    args.put("content", roleAttackWishlist(role));
                    appendObservation(output, createAtomicWishlist(project, args));
                    rememberRoute(project, routeKey, "Created missing-evidence role attack wishlist for " + roleTag);
                    compileAndDispatch = true;
                    actions++;
                }
                continue;
            }

            if (sourcePending > 0 && ownerOpen == 0) {
                String routeKey = routeKey(project, roleTag, "compile_pending_role_wishlist", demand);
                if (!alreadyRouted(project, routeKey)) {
                    output.append("## role_compile_route [ok]\n")
                            .append("Role ").append(roleTag)
                            .append(" has source-role pending wishlist and no owner-role open work. Dependency graph compilation is required.\n\n");
                    rememberRoute(project, routeKey, "Compiled pending source-role wishlist for " + roleTag);
                    compileAndDispatch = true;
                    actions++;
                }
            }

            if (shouldRouteVideoAsset(roleTag, demandLower)) {
                String routeKey = routeKey(project, roleTag, "video_asset_generate", demand);
                if (!alreadyRouted(project, routeKey)) {
                    ObjectNode args = objectMapper.createObjectNode();
                    args.put("brief", roleToolBrief(role, demand, "Produce a short useful project video asset."));
                    args.put("assetType", "project_video");
                    args.put("quality", "standard");
                    args.put("useGoogleSearch", shouldUseSearchForDemand(demandLower));
                    appendObservation(output, videoAssetGenerate(project, context, args, roleToolBrief(role, demand, "Produce a video asset.")));
                    rememberRoute(project, routeKey, "Routed " + roleTag + " demand to Veo video asset generation.");
                    actions++;
                    continue;
                }
            }

            if (shouldRouteDesignAsset(roleTag, demandLower)) {
                String routeKey = routeKey(project, roleTag, "design_asset_generate", demand);
                if (!alreadyRouted(project, routeKey)) {
                    ObjectNode args = objectMapper.createObjectNode();
                    args.put("brief", roleToolBrief(role, demand, "Produce visual evidence or a UI asset that resolves this role objection."));
                    args.put("assetType", inferredAssetType(demandLower));
                    args.put("quality", "refuses".equals(stance) || containsAny(demandLower, "brand", "premium") ? "pro" : "fast");
                    args.put("useGoogleSearch", shouldUseSearchForDemand(demandLower));
                    appendObservation(output, designAssetGenerate(project, context, args, roleToolBrief(role, demand, "Produce a visual asset.")));
                    rememberRoute(project, routeKey, "Routed " + roleTag + " demand to Nano Banana design asset generation.");
                    actions++;
                    continue;
                }
            }

            if (shouldRouteDeepDiagnostic(role, demandLower)) {
                String routeKey = routeKey(project, roleTag, "deep_diagnostic_worker", demand);
                if (!alreadyRouted(project, routeKey)) {
                    ObjectNode args = objectMapper.createObjectNode();
                    args.put("mission", roleToolBrief(role, demand,
                            "Diagnose and repair the smallest root cause in an autonomous branch. Run available tests and open/push the branch when possible."));
                    appendObservation(output, deepDiagnosticWorker(project, context, args.path("mission").asText(), args));
                    rememberRoute(project, routeKey, "Routed " + roleTag + " demand to the deep diagnostic worker.");
                    actions++;
                }
            }
        }

        if (compileAndDispatch) {
            appendObservation(output, compileDependencyGraph(project));
            appendObservation(output, dispatchNextBestTask(project));
        }

        if (actions == 0) {
            return new ToolObservation("role_tool_router", "idle", "No new role-driven tool action was required or all matching routes were already handled.");
        }
        return new ToolObservation("role_tool_router", "ok", trim(output.toString()));
    }

    private String roleDemandText(ProjectOperationalContext context, JsonNode role) {
        String roleTag = role.path("roleTag").asText("");
        StringBuilder builder = new StringBuilder();
        builder.append("role=").append(roleTag)
                .append(", doctrine=").append(role.path("doctrineName").asText(""))
                .append(", focus=").append(role.path("doctrineFocus").asText(""))
                .append(", stance=").append(role.path("stance").asText(""))
                .append(", kano=").append(role.path("kanoPressure").asText(""))
                .append(", cynefin=").append(role.path("cynefinBias").asText(""))
                .append(", topObjection=").append(role.path("topObjection").asText(""));
        for (JsonNode evidence : role.path("evidence")) {
            builder.append("; evidence=").append(evidence.asText(""));
        }
        JsonNode wishlist = factNode(context, "wishlist").path("items");
        if (wishlist.isArray()) {
            int count = 0;
            for (JsonNode item : wishlist) {
                if (!roleTag.equals(item.path("sourceRoleTag").asText(""))) {
                    continue;
                }
                builder.append("\nsourceWishlist status=").append(item.path("status").asText(""))
                        .append(" content=").append(item.path("content").asText(""))
                        .append(" jtbd=").append(item.path("jtbd").asText(""))
                        .append(" dod=").append(item.path("dod").asText(""));
                count++;
                if (count >= 3) {
                    break;
                }
            }
        }
        return compact(builder.toString(), 3_000);
    }

    private String roleAttackWishlist(JsonNode role) {
        return """
                Role council attack: %s (%s).
                JTBD: When the current project lacks evidence for this doctrine, I want the role to falsify the smallest risky assumption, so the project cannot be accepted without role-specific proof.
                Kano: %s.
                Cynefin: %s.
                DoD: one short English finding exists with evidence, owner role, and the smallest next project action; no duplicate role fan-out.
                Verification: role stance can move from unknown to object/satisfied based on concrete project evidence.
                """.formatted(
                role.path("roleTag").asText("BARCAN-TAG-09"),
                role.path("doctrineName").asText("role doctrine"),
                role.path("kanoPressure").asText("performance"),
                role.path("cynefinBias").asText("complex")
        ).trim();
    }

    private String roleToolBrief(JsonNode role, String demand, String mission) {
        return """
                EMS role-driven tool request.
                Role: %s
                Doctrine: %s
                Focus: %s
                Stance: %s
                Kano pressure: %s
                Cynefin: %s
                Mission: %s
                Demand evidence:
                %s
                """.formatted(
                role.path("roleTag").asText(""),
                role.path("doctrineName").asText(""),
                role.path("doctrineFocus").asText(""),
                role.path("stance").asText(""),
                role.path("kanoPressure").asText(""),
                role.path("cynefinBias").asText(""),
                mission,
                compact(demand, 2_000)
        ).trim();
    }

    private boolean shouldRouteVideoAsset(String roleTag, String demandLower) {
        return Set.of("BARCAN-TAG-03", "BARCAN-TAG-09", "BARCAN-TAG-11").contains(roleTag)
                && containsAny(demandLower,
                "video", "veo", "demo", "promo", "walkthrough", "onboarding", "storyboard", "motion",
                "screen recording", "product tour", "explainer");
    }

    private boolean shouldRouteDesignAsset(String roleTag, String demandLower) {
        return Set.of("BARCAN-TAG-03", "BARCAN-TAG-11").contains(roleTag)
                && containsAny(demandLower,
                "visual", "banner", "hero", "image", "icon", "mockup", "wireframe", "layout", "contrast",
                "accessibility", "ui", "ux", "screen", "perception", "design", "asset");
    }

    private boolean shouldRouteDeepDiagnostic(JsonNode role, String demandLower) {
        String roleTag = role.path("roleTag").asText("");
        boolean technicalRole = Set.of(
                "BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-02", "BARCAN-TAG-05",
                "BARCAN-TAG-06", "BARCAN-TAG-07", "BARCAN-TAG-08", "BARCAN-TAG-10"
        ).contains(roleTag);
        boolean hardSignal = "refuses".equals(role.path("stance").asText(""))
                || role.path("ownerTasksBlocked").asLong(0) > 0
                || role.path("defectWork").asLong(0) > 0
                || containsAny(demandLower,
                "root cause", "repository", "workspace", "environment", "build", "test", "security",
                "contract", "api", "migration", "database", "auth", "compliance", "failing");
        return technicalRole && hardSignal;
    }

    private boolean shouldUseSearchForDemand(String demandLower) {
        return containsAny(demandLower,
                "current", "fresh", "latest", "market", "competitor", "law", "legal", "compliance",
                "regulation", "documentation", "external");
    }

    private String routeKey(ProjectEntity project, String roleTag, String tool, String demand) {
        String hash = Integer.toHexString(Math.abs(compact(demand, 800).hashCode()));
        return "role-tool-router:" + project.getId() + ":" + roleTag + ":" + tool + ":" + hash;
    }

    private boolean alreadyRouted(ProjectEntity project, String routeKey) {
        Path file = projectMemoryFile(project);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(routeKey);
        } catch (Exception e) {
            return false;
        }
    }

    private void rememberRoute(ProjectEntity project, String routeKey, String summary) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("note", routeKey + " - " + summary);
        projectMemoryAppend(project, args);
    }

    private void addRoutedCall(List<OperatorToolCall> calls, String tool, String reason) {
        boolean alreadyAdded = calls.stream().anyMatch(call -> call.tool().equals(tool));
        if (!alreadyAdded) {
            calls.add(new OperatorToolCall(tool, objectMapper.createObjectNode(), reason));
        }
    }

    private boolean looksLikeWorkspaceRepairIntent(ProjectEntity project, String lower) {
        if (!containsAny(lower, "fix", "repair", "clone", "workspace", "not a git", "git repo",
                "\u0438\u0441\u043f\u0440\u0430\u0432", "\u043f\u043e\u0447\u0438\u043d", "\u0441\u043a\u043b\u043e\u043d\u0438\u0440",
                "\u0440\u0435\u043f\u043e\u0437\u0438\u0442\u043e\u0440", "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441")) {
            return false;
        }
        Path workspace = resolveProjectWorkspace(project);
        return !Files.isDirectory(workspace.resolve(".git"));
    }

    private boolean looksLikeOperatorExecutionIntent(String lower) {
        String compact = lower == null ? "" : lower.trim();
        if (Set.of(
                "ok",
                "yes",
                "confirm",
                "confirmed",
                "go",
                "go ahead",
                "run it",
                "execute",
                "start",
                "continue",
                "\u0434\u0430",
                "\u043e\u043a",
                "\u043e\u043a\u0435\u0439",
                "\u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0430\u044e",
                "\u0437\u0430\u043f\u0443\u0441\u043a\u0430\u0439",
                "\u0437\u0430\u043f\u0443\u0441\u0442\u0438",
                "\u0438\u043d\u0438\u0446\u0438\u0438\u0440\u0443\u0439",
                "\u0432\u044b\u043f\u043e\u043b\u043d\u044f\u0439",
                "\u0434\u0435\u043b\u0430\u0439",
                "\u043d\u0430\u0447\u0438\u043d\u0430\u0439"
        ).contains(compact)) {
            return true;
        }
        return containsAny(compact,
                "go ahead", "run the plan", "execute the plan", "continue the plan", "start recovery",
                "\u0434\u0432\u0438\u0433\u0430\u0435\u043c\u0441\u044f \u0434\u0430\u043b\u044c\u0448\u0435",
                "\u0434\u0432\u0438\u0433\u0430\u0439\u0441\u044f \u0434\u0430\u043b\u044c\u0448\u0435",
                "\u0438\u0434\u0435\u043c \u0434\u0430\u043b\u044c\u0448\u0435",
                "\u043f\u043e\u0435\u0445\u0430\u043b\u0438",
                "\u0437\u0430\u043f\u0443\u0441\u043a\u0430\u0439",
                "\u0438\u043d\u0438\u0446\u0438\u0438\u0440\u0443",
                "\u0432\u044b\u043f\u043e\u043b\u043d",
                "\u043f\u0440\u043e\u0434\u043e\u043b\u0436");
    }

    private boolean looksLikeStartTestingIntent(String lower) {
        boolean testing = containsAny(lower, "test", "testing", "qa", "\u0442\u0435\u0441\u0442", "\u0442\u0435\u0441\u0442\u0438\u0440", "\u043a\u0443\u0430");
        boolean start = containsAny(lower, "start", "begin", "create", "add", "run", "launch",
                "\u043d\u0430\u0447\u043d", "\u0437\u0430\u043f\u0443\u0441\u0442", "\u0441\u043e\u0437\u0434", "\u0434\u043e\u0431\u0430\u0432");
        return testing && start;
    }

    private boolean looksLikeBlockedRecoveryIntent(String lower) {
        boolean blocked = containsAny(lower, "blocked", "stuck", "failed", "rejected", "loop", "circuit",
                "\u0437\u0430\u0431\u043b\u043e\u043a", "\u0437\u0430\u0432\u0438\u0441", "\u0437\u0430\u0441\u0442\u0440",
                "\u043e\u0442\u043a\u043b\u043e\u043d", "\u0442\u0443\u043f\u0438\u043a", "\u043a\u0440\u0443\u0433");
        boolean recover = containsAny(lower, "resume", "continue", "recover", "unblock", "restart", "replace", "new task", "new tasks",
                "\u043f\u0440\u043e\u0434\u043e\u043b\u0436", "\u0432\u043e\u0437\u043e\u0431\u043d", "\u0440\u0430\u0437\u0431\u043b\u043e\u043a",
                "\u043f\u0435\u0440\u0435\u0437\u0430\u043f", "\u043d\u043e\u0432\u044b", "\u0437\u0430\u0434\u0430\u0447");
        return blocked && recover;
    }

    private boolean looksLikeEnvironmentBootstrapIntent(String lower) {
        boolean environment = containsAny(lower,
                "environment", "workspace", "local", "setup", "bootstrap", "scaffold", "repository", "repo", ".git",
                "backend", "frontend", "run command", "test command",
                "\u043e\u043a\u0440\u0443\u0436\u0435\u043d", "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441", "\u043b\u043e\u043a\u0430\u043b",
                "\u0440\u0435\u043f\u043e\u0437\u0438\u0442", "\u0441\u043a\u0430\u0444\u0444\u043e\u043b\u0434", "\u0431\u044d\u043a\u0435\u043d\u0434",
                "\u0444\u0440\u043e\u043d\u0442\u0435\u043d\u0434", "\u043a\u043b\u043e\u043d", "\u0433\u0438\u0442");
        boolean action = containsAny(lower,
                "fix", "repair", "start", "begin", "create", "add", "continue", "prepare", "make",
                "\u0438\u0441\u043f\u0440\u0430\u0432", "\u043f\u043e\u0447\u0438\u043d", "\u043d\u0430\u0447", "\u0441\u043e\u0437\u0434",
                "\u0434\u043e\u0431\u0430\u0432", "\u043f\u043e\u0434\u0433\u043e\u0442\u043e\u0432", "\u0440\u0430\u0437\u0431\u0435\u0440",
                "\u0440\u0430\u0437\u043e\u0431\u0440", "\u0434\u0435\u043b\u0430\u0439", "\u0441\u0434\u0435\u043b\u0430\u0439");
        return environment && action;
    }

    private boolean looksLikeQualityWasteIntent(String lower) {
        return containsAny(lower,
                "six sigma", "dpmo", "sigma", "defect", "defects", "quality", "copq", "waste", "scrap",
                "\u0431\u0440\u0430\u043a", "\u0434\u0435\u0444\u0435\u043a\u0442", "\u043a\u0430\u0447\u0435\u0441\u0442\u0432",
                "\u0448\u0435\u0441\u0442\u044c \u0441\u0438\u0433\u043c", "\u0440\u0430\u0441\u0445\u043e\u0434", "\u043f\u043e\u0442\u0435\u0440");
    }

    private boolean looksLikePrCleanupIntent(String lower) {
        boolean pr = containsAny(lower,
                "pr", "pull request", "pull-request", "merge request",
                "\u043f\u0440", "\u043f\u0443\u043b\u0440\u0435\u043a\u0432\u0435\u0441\u0442", "\u043f\u0443\u043b\u043b\u0440\u0435\u043a\u0432\u0435\u0441\u0442",
                "\u043f\u0443\u043b \u0440\u0435\u043a\u0432\u0435\u0441\u0442");
        boolean cleanup = containsAny(lower,
                "close", "cleanup", "stale", "unmerged", "not merged", "useless", "noise", "wip",
                "\u0437\u0430\u043a\u0440\u043e", "\u0437\u0430\u043a\u0440\u044b", "\u043d\u0435\u0441\u043c\u0435\u0440\u0436",
                "\u043c\u0443\u0441\u043e\u0440", "\u043b\u0438\u0448\u043d", "\u0448\u0443\u043c", "\u0431\u0435\u0441\u0441\u043c\u044b\u0441");
        return pr && cleanup;
    }

    private boolean looksLikeJulesTriageIntent(String lower) {
        return containsAny(lower,
                "jules", "session", "loop", "stuck", "unanswered", "activity", "nobody answers", "bad session",
                "\u0434\u0436\u0443\u043b", "\u0441\u0435\u0441\u0441", "\u0437\u0430\u0432\u0438\u0441", "\u0442\u0443\u043f\u0438\u043a",
                "\u043a\u0440\u0443\u0433", "\u043d\u0435 \u043e\u0442\u0432\u0435\u0447", "\u043d\u0438\u043a\u0442\u043e \u043d\u0435 \u043e\u0442\u0432\u0435\u0447");
    }

    private boolean looksLikeDeepDiagnosticIntent(String lower) {
        return containsAny(lower,
                "diagnostic branch", "deep diagnostic", "deep diagnose",
                "root cause branch", "engineering executor", "run local repair", "push diagnostic branch",
                "\u0430\u043d\u0442\u0438\u0433\u0440\u0430\u0432\u0438\u0442", "\u0433\u043b\u0443\u0431\u043e\u043a\u0430\u044f \u0434\u0438\u0430\u0433\u043d\u043e\u0441",
                "\u0434\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u0447\u0435\u0441\u043a", "\u0441\u043f\u0435\u0446\u0438\u0430\u043b\u044c\u043d\u0443\u044e \u0432\u0435\u0442\u043a",
                "\u0441\u043f\u0435\u0446 \u0432\u0435\u0442\u043a", "\u043b\u043e\u043a\u0430\u043b\u044c\u043d\u043e \u0438\u0441\u043f\u0440\u0430\u0432",
                "\u043f\u0443\u0448\u0438\u0442\u044c \u0432\u0435\u0442\u043a", "\u043f\u0440\u0438\u0447\u0438\u043d\u0443 \u0432 \u043a\u043e\u0434\u0435");
    }

    private boolean looksLikeGoogleAiResourceIntent(String lower) {
        return containsAny(lower,
                "google ai resource", "gemini resource", "ai resources", "available models", "model catalog",
                "nano banana", "veo", "grounding", "url context",
                "\u0440\u0435\u0441\u0443\u0440\u0441\u044b \u0434\u0436\u0435\u043c\u0438\u043d\u0438", "\u0440\u0435\u0441\u0443\u0440\u0441\u044b gemini",
                "\u0438\u043d\u0441\u0442\u0440\u0443\u043c\u0435\u043d\u0442\u044b google", "\u043c\u043e\u0434\u0435\u043b\u0438 gemini");
    }

    private boolean looksLikeGroundedResearchIntent(String lower) {
        boolean research = containsAny(lower,
                "research", "current facts", "fresh facts", "market", "competitor", "latest", "documentation",
                "google search", "grounded",
                "\u0438\u0441\u0441\u043b\u0435\u0434", "\u0440\u044b\u043d\u043e\u043a", "\u043a\u043e\u043d\u043a\u0443\u0440", "\u0430\u043a\u0442\u0443\u0430\u043b",
                "\u0441\u0432\u0435\u0436", "\u0434\u043e\u043a\u0443\u043c\u0435\u043d\u0442");
        boolean asksExternal = containsAny(lower, "google", "web", "internet", "external", "outside",
                "\u0433\u0443\u0433\u043b", "\u0438\u043d\u0442\u0435\u0440\u043d\u0435\u0442", "\u0432\u043d\u0435\u0448");
        return research && asksExternal;
    }

    private boolean looksLikeUrlContextIntent(String lower) {
        return lower.contains("http://") || lower.contains("https://") || containsAny(lower, "url context", "analyze url", "\u0441\u0441\u044b\u043b\u043a");
    }

    private boolean looksLikeDesignAssetIntent(String lower) {
        boolean visual = containsAny(lower,
                "banner", "hero image", "site image", "visual asset", "mockup", "icon", "illustration", "image",
                "generate image", "design asset", "nano banana",
                "\u0431\u0430\u043d\u043d\u0435\u0440", "\u043a\u0430\u0440\u0442\u0438\u043d", "\u0438\u0437\u043e\u0431\u0440\u0430\u0436",
                "\u043c\u043e\u043a\u0430\u043f", "\u0438\u043a\u043e\u043d", "\u0434\u0438\u0437\u0430\u0439\u043d");
        boolean action = containsAny(lower,
                "generate", "create", "produce", "make", "render",
                "\u0441\u0433\u0435\u043d\u0435\u0440", "\u0441\u043e\u0437\u0434", "\u0441\u0434\u0435\u043b", "\u043d\u0430\u0440\u0438\u0441");
        return visual && action;
    }

    private boolean looksLikeVideoAssetIntent(String lower) {
        boolean video = containsAny(lower,
                "video", "veo", "demo video", "promo video", "onboarding video", "walkthrough",
                "product tour", "explainer", "motion", "storyboard",
                "\u0432\u0438\u0434\u0435\u043e", "\u0440\u043e\u043b\u0438\u043a", "\u0434\u0435\u043c\u043e", "\u043f\u0440\u043e\u043c\u043e",
                "\u043e\u043d\u0431\u043e\u0440\u0434", "\u0442\u0443\u0440", "\u0441\u0446\u0435\u043d\u0430\u0440");
        boolean action = containsAny(lower,
                "generate", "create", "produce", "make", "render",
                "\u0441\u0433\u0435\u043d\u0435\u0440", "\u0441\u043e\u0437\u0434", "\u0441\u0434\u0435\u043b", "\u043d\u0430\u0440\u0438\u0441",
                "\u0441\u043c\u043e\u043d\u0442\u0438\u0440", "\u0441\u0434\u0435\u043b\u0430\u0439");
        return video && action;
    }

    private String firstUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("https?://\\S+").matcher(value);
        if (matcher.find()) {
            return matcher.group().replaceAll("[),.;]+$", "");
        }
        return "";
    }

    private String inferredAssetType(String lower) {
        if (containsAny(lower, "hero", "\u0445\u0438\u0440\u043e")) {
            return "hero";
        }
        if (containsAny(lower, "banner", "\u0431\u0430\u043d\u043d\u0435\u0440")) {
            return "banner";
        }
        if (containsAny(lower, "icon", "\u0438\u043a\u043e\u043d")) {
            return "icon";
        }
        if (containsAny(lower, "mockup", "\u043c\u043e\u043a\u0430\u043f")) {
            return "mockup";
        }
        return "visual_asset";
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
                - project_bootstrap_audit {}
                - jules_activity_triage {}
                - six_sigma_pareto {}
                - pr_review_and_merge_plan {}
                - operator_waste_guard {}
                - autonomous_flow_steward {}
                - project_decision_memory {"note":"optional English decision note; omit to read memory"}
                - project_memory_read {}
                - google_ai_resource_catalog {}
                - google_search_research {"question":"English research question"}
                - url_context_research {"url":"https://example.com","question":"English URL analysis question"}

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
                - recover_blocked_flow {} MUTATING
                - close_unmerged_pull_requests {"reason":"English cleanup reason"} MUTATING
                - create_atomic_wishlist {"content":"English atomic wishlist text","sourceRoleTag":"BARCAN-TAG-09"} MUTATING
                - compile_dependency_graph {} MUTATING
                - dispatch_next_best_task {} MUTATING
                - close_bad_session {"sessionId":"optional UUID","reason":"English closure reason"} MUTATING
                - postmortem_to_wishlist {"sessionId":"UUID","reason":"English postmortem reason"} MUTATING
                - run_project_checks {} MUTATING/EXECUTION
                - maintenance_stuck {} MUTATING
                - add_wishlist {"content":"English wishlist text","sourceRoleTag":"BARCAN-TAG-09"} MUTATING
                - ensure_environment_bootstrap_work {} MUTATING
                - project_memory_append {"note":"English durable project memory note grounded in current evidence"} MUTATING
                - ensure_project_workspace {} MUTATING
                - start_testing_stream {} MUTATING
                - deep_diagnostic_worker {"mission":"optional English bounded diagnostic mission"} MUTATING/REMOTE_EXECUTION
                - design_asset_generate {"brief":"English visual brief","assetType":"hero|banner|mockup|icon|visual_asset","quality":"fast|pro","useGoogleSearch":false} MUTATING/REMOTE_EXECUTION
                - video_asset_generate {"brief":"English video brief","assetType":"demo|promo|onboarding|walkthrough|project_video","quality":"standard|pro","useGoogleSearch":false} MUTATING/REMOTE_EXECUTION

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
                case "project_bootstrap_audit" -> projectBootstrapAudit(project);
                case "jules_activity_triage" -> julesActivityTriage(context);
                case "six_sigma_pareto" -> sixSigmaPareto(context);
                case "pr_review_and_merge_plan" -> prReviewAndMergePlan(context);
                case "operator_waste_guard" -> operatorWasteGuard(context);
                case "autonomous_flow_steward" -> autonomousFlowSteward(project, context);
                case "project_decision_memory" -> projectDecisionMemory(project, args, userMessage);
                case "project_memory_read" -> projectMemoryRead(project);
                case "google_ai_resource_catalog" -> googleAiResourceCatalog();
                case "google_search_research" -> googleSearchResearch(context, args, userMessage);
                case "url_context_research" -> urlContextResearch(context, args, userMessage);
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
                case "recover_blocked_flow" -> mutating(userMessage, tool, () -> recoverBlockedFlow(project));
                case "close_unmerged_pull_requests" -> mutating(userMessage, tool, () -> closeUnmergedPullRequests(project, args));
                case "create_atomic_wishlist" -> mutating(userMessage, tool, () -> createAtomicWishlist(project, args));
                case "compile_dependency_graph" -> mutating(userMessage, tool, () -> compileDependencyGraph(project));
                case "dispatch_next_best_task" -> mutating(userMessage, tool, () -> dispatchNextBestTask(project));
                case "close_bad_session" -> mutating(userMessage, tool, () -> closeBadSession(project, args));
                case "postmortem_to_wishlist" -> mutating(userMessage, tool, () -> postmortemToWishlist(project, args));
                case "run_project_checks" -> mutating(userMessage, tool, () -> runDetectedChecks(resolveProjectWorkspace(project)));
                case "maintenance_stuck" -> mutating(userMessage, tool, this::maintenanceStuck);
                case "add_wishlist" -> mutating(userMessage, tool, () -> addWishlist(project, args));
                case "ensure_environment_bootstrap_work" -> mutating(userMessage, tool, () -> ensureEnvironmentBootstrapWork(project));
                case "project_memory_append" -> mutating(userMessage, tool, () -> projectMemoryAppend(project, args));
                case "ensure_project_workspace" -> mutating(userMessage, tool, () -> ensureProjectWorkspace(project));
                case "start_testing_stream" -> mutating(userMessage, tool, () -> startTestingStream(project));
                case "deep_diagnostic_worker" -> mutating(userMessage, tool, () -> deepDiagnosticWorker(project, context, userMessage, args));
                case "design_asset_generate" -> mutating(userMessage, tool, () -> designAssetGenerate(project, context, args, userMessage));
                case "video_asset_generate" -> mutating(userMessage, tool, () -> videoAssetGenerate(project, context, args, userMessage));
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

    private ToolObservation projectBootstrapAudit(ProjectEntity project) {
        Path root = resolveProjectWorkspace(project);
        StringBuilder output = new StringBuilder();
        output.append("Project bootstrap audit for ").append(project.getName()).append('\n');
        output.append("workspace=").append(root).append('\n');
        output.append("gitRepository=").append(Files.isDirectory(root.resolve(".git"))).append('\n');
        output.append("readme=").append(Files.isRegularFile(root.resolve("README.md"))).append('\n');
        output.append("backendBoundary=").append(hasAny(root, "pom.xml", "build.gradle", "src/main", "backend")).append('\n');
        output.append("frontendBoundary=").append(hasAny(root, "package.json", "frontend/package.json", "src/App.svelte", "src/main.ts")).append('\n');
        output.append("ci=").append(hasAny(root, ".github/workflows/ci.yml", ".github/workflows")).append('\n');
        output.append("\n## detected_checks\n").append(testPlan(root).output()).append('\n');
        output.append("\n## repo_profile\n").append(repoProfile(root).output());
        return new ToolObservation("project_bootstrap_audit", Files.isDirectory(root) ? "ok" : "missing", trim(output.toString()));
    }

    private boolean hasAny(Path root, String... relativePaths) {
        for (String relativePath : relativePaths) {
            if (Files.exists(root.resolve(relativePath))) {
                return true;
            }
        }
        return false;
    }

    private ToolObservation julesActivityTriage(ProjectOperationalContext context) {
        JsonNode sessions = factNode(context, "julesSessions");
        StringBuilder output = new StringBuilder();
        output.append("Jules activity triage for selected project.\n");
        output.append("countsByStatus=").append(sessions.path("countsByStatus")).append('\n');
        int active = 0;
        for (JsonNode session : sessions.path("items")) {
            String status = session.path("status").asText("");
            if (!List.of("queued", "running", "revising", "stuck").contains(status)) {
                continue;
            }
            active++;
            output.append("- session=").append(session.path("id").asText(""))
                    .append(", external=").append(session.path("externalSessionId").asText(""))
                    .append(", status=").append(status)
                    .append(", role=").append(session.path("roleTag").asText(""))
                    .append(", account=").append(session.path("accountName").asText(""))
                    .append(", task=").append(session.path("taskId").asText(""))
                    .append(", updatedAt=").append(session.path("updatedAt").asText(""))
                    .append('\n');
            if (active >= 12) {
                output.append("... limited to 12 active sessions\n");
                break;
            }
        }
        if (active == 0) {
            output.append("No active Jules sessions require activity triage.\n");
        } else {
            output.append("Action rule: if a session has repeated non-actionable dialogue, stale status, or no concrete next action, use close_bad_session then postmortem_to_wishlist.");
        }
        return new ToolObservation("jules_activity_triage", "ok", trim(output.toString()));
    }

    private ToolObservation sixSigmaPareto(ProjectOperationalContext context) {
        JsonNode sixSigma = factNode(context, "sixSigmaControl");
        if (sixSigma.isMissingNode() || sixSigma.isNull()) {
            return new ToolObservation("six_sigma_pareto", "missing", "Six Sigma control facts are not available for the selected project.");
        }
        StringBuilder output = new StringBuilder();
        output.append("Six Sigma EMS production telemetry\n");
        output.append("scope=production-system-feedback, not a project execution stop condition\n");
        output.append("status=").append(sixSigma.path("statusLabel").asText("NOT AVAILABLE")).append('\n');
        output.append("dpmo=").append(sixSigma.path("dpmo").asText("NOT AVAILABLE")).append('\n');
        output.append("sigmaLevel=").append(sixSigma.path("sigmaLevel").asText("NOT AVAILABLE")).append('\n');
        output.append("copqProxy=").append(sixSigma.path("copqProxy").asText("NOT AVAILABLE")).append('\n');
        output.append("recommendedAction=").append(sixSigma.path("recommendedAction").asText("NOT AVAILABLE")).append("\n\n");
        output.append("CTQ Pareto:\n");
        int count = 0;
        for (JsonNode row : sixSigma.path("ctqPareto")) {
            output.append("- ").append(row.path("ctq").asText(row.path("name").asText("unknown")))
                    .append(": defects=").append(row.path("defects").asText("0"))
                    .append(", dpmo=").append(row.path("dpmo").asText("0"))
                    .append(", source=").append(row.path("source").asText("quality_gate"))
                    .append('\n');
            count++;
        }
        if (count == 0) {
            output.append("- No CTQ defects in current evidence.\n");
        }
        return new ToolObservation("six_sigma_pareto", "ok", trim(output.toString()));
    }

    private ToolObservation prReviewAndMergePlan(ProjectOperationalContext context) {
        JsonNode github = factNode(context, "githubPullRequestsLive");
        JsonNode reviews = factNode(context, "databasePrReviews");
        StringBuilder output = new StringBuilder();
        output.append("PR review and merge plan for selected project.\n");
        output.append("githubAvailable=").append(github.path("available").asText("false"))
                .append(", open=").append(github.path("openCount").asText("0"))
                .append(", closed=").append(github.path("closedCount").asText("0"))
                .append(", error=").append(github.path("error").asText(""))
                .append('\n');
        output.append("reviews approved=").append(reviews.path("approved").asText("0"))
                .append(", rejected=").append(reviews.path("rejected").asText("0"))
                .append(", merged=").append(reviews.path("merged").asText("0"))
                .append(", approvedButNotMerged=").append(reviews.path("approvedButNotMerged").asText("0"))
                .append("\n\n");
        output.append("Open PRs:\n");
        int count = 0;
        for (JsonNode pr : github.path("open")) {
            output.append("- #").append(pr.path("number").asText(""))
                    .append(" ").append(pr.path("title").asText(""))
                    .append(" by ").append(pr.path("author").asText(""))
                    .append(" ").append(pr.path("url").asText(""))
                    .append('\n');
            count++;
        }
        if (count == 0) {
            output.append("- No live open PRs are available in GitHub evidence.\n");
        }
        output.append("Action rule: merge only approved, conflict-free PRs with passing checks; otherwise create a fresh atomic wishlist item for the top current project risk.");
        return new ToolObservation("pr_review_and_merge_plan", "ok", trim(output.toString()));
    }

    private ToolObservation operatorWasteGuard(ProjectOperationalContext context) {
        JsonNode sixSigma = factNode(context, "sixSigmaControl");
        JsonNode capacity = factNode(context, "julesUniversalRoleCapacity");
        JsonNode tasks = factNode(context, "tasks");
        StringBuilder output = new StringBuilder();
        output.append("EMS waste guard\n");
        output.append("sixSigmaStatus=").append(sixSigma.path("statusLabel").asText("NOT AVAILABLE"))
                .append(", copqProxy=").append(sixSigma.path("copqProxy").asText("NOT AVAILABLE"))
                .append(", sharedSlotsFree=").append(capacity.path("sharedSlotsFree").asText("NOT AVAILABLE"))
                .append('\n');
        output.append("taskCounts=").append(tasks.path("countsByStatus")).append('\n');
        output.append("Guardrail decisions:\n");
        output.append("- If sharedSlotsFree > 0 and queued tasks exist, dispatch flow is the next target; do not claim capacity shortage.\n");
        output.append("- Six Sigma is maker-side production telemetry; do not stop project execution only because of it.\n");
        output.append("- If no task can be named with owner and role, create_atomic_wishlist then compile_dependency_graph.\n");
        output.append("- If a Jules session loops or becomes non-actionable, close_bad_session and let postmortem_to_wishlist produce fresh work.");
        return new ToolObservation("operator_waste_guard", "ok", trim(output.toString()));
    }

    private boolean shouldRunAutonomousSteward(ProjectOperationalContext context) {
        if (!allowMutatingTools) {
            return false;
        }
        JsonNode tasks = factNode(context, "tasks");
        JsonNode github = factNode(context, "githubPullRequestsLive");
        JsonNode sessions = factNode(context, "julesSessions");
        JsonNode accounts = factNode(context, "accountsAvailableForProject");

        long queued = tasks.path("countsByStatus").path("queued").asLong(0);
        long review = tasks.path("countsByStatus").path("review").asLong(0);
        long openPullRequests = github.path("openCount").asLong(0);
        long stuckSessions = sessions.path("stuck").asLong(0);
        long failedSessions = sessions.path("failed").asLong(0);
        long apiBlockedAccounts = accounts.path("apiBlocked").asLong(0);

        return queued > 0
                || review > 0
                || stuckSessions > 0
                || apiBlockedAccounts > 0
                || (openPullRequests > 0 && failedSessions > 0);
    }

    private ToolObservation autonomousFlowSteward(ProjectEntity project, ProjectOperationalContext context) {
        if (!allowMutatingTools) {
            return new ToolObservation("autonomous_flow_steward", "blocked",
                    "Autonomous EMS steward is disabled because mutating operator tools are disabled.");
        }

        StringBuilder output = new StringBuilder();
        JsonNode tasks = factNode(context, "tasks");
        JsonNode github = factNode(context, "githubPullRequestsLive");
        JsonNode sessions = factNode(context, "julesSessions");
        JsonNode accounts = factNode(context, "accountsAvailableForProject");
        long openPullRequests = github.path("openCount").asLong(0);

        output.append("Autonomous EMS steward engaged for selected project.\n")
                .append("trigger: queuedTasks=").append(tasks.path("countsByStatus").path("queued").asText("0"))
                .append(", reviewTasks=").append(tasks.path("countsByStatus").path("review").asText("0"))
                .append(", openPullRequests=").append(openPullRequests)
                .append(", stuckSessions=").append(sessions.path("stuck").asText("0"))
                .append(", apiBlockedAccounts=").append(accounts.path("apiBlocked").asText("0"))
                .append('\n');

        if (openPullRequests > 0) {
            appendObservation(output, closeUnmergedPullRequests(project, objectMapper.createObjectNode()
                    .put("reason", "Autonomous EMS steward WIP cleanup: close open unmerged PRs before rebuilding the project flow.")));
        }

        appendObservation(output, ensureEnvironmentBootstrapWork(project));

        try {
            appendObservation(output, recoverBlockedFlow(project));
        } catch (Exception e) {
            output.append("## recover_blocked_flow [error]\n").append(e.getMessage()).append("\n\n");
        }

        try {
            appendObservation(output, compileDependencyGraph(project));
        } catch (Exception e) {
            output.append("## compile_dependency_graph [error]\n").append(e.getMessage()).append("\n\n");
        }

        try {
            appendObservation(output, dispatchNextBestTask(project));
        } catch (Exception e) {
            output.append("## dispatch_next_best_task [error]\n").append(e.getMessage()).append("\n\n");
        }

        return new ToolObservation("autonomous_flow_steward", "ok", trim(output.toString()));
    }

    private void appendObservation(StringBuilder output, ToolObservation observation) {
        output.append("## ").append(observation.tool()).append(" [").append(observation.status()).append("]\n")
                .append(observation.output())
                .append("\n\n");
    }

    private JsonNode factNode(ProjectOperationalContext context, String key) {
        Object value = context.facts().get(key);
        return value == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : objectMapper.valueToTree(value);
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

    private ToolObservation recoverBlockedFlow(ProjectEntity project) {
        int recovered = projectFlowService.recoverBlockedWork(project.getId());
        projectFlowService.dispatchQueuedTasks(project.getId());
        projectFlowService.dispatchReviewTasks(project.getId());
        return new ToolObservation("recover_blocked_flow", "ok",
                "Checked recovery flow, created/reused fresh recovery work where applicable, then ran dispatchQueuedTasks + dispatchReviewTasks for project "
                        + project.getId()
                        + ". Historical failed attempts are not active project work.");
    }

    private ToolObservation closeUnmergedPullRequests(ProjectEntity project, JsonNode args) {
        String reason = textArg(args, "reason",
                "EMS WIP cleanup: close open unmerged PRs before rebuilding the project flow.");
        GitHubPullRequestService.PullRequestCloseReport report =
                gitHubPullRequestService.closeOpenPullRequests(project, reason);
        if (!report.available()) {
            return new ToolObservation("close_unmerged_pull_requests", "blocked",
                    "GitHub PR cleanup is unavailable for " + report.owner() + "/" + report.repo()
                            + ": " + report.error());
        }

        StringBuilder output = new StringBuilder();
        output.append("Closed open unmerged PRs for ")
                .append(report.owner()).append('/').append(report.repo())
                .append(". requested=").append(report.requested())
                .append(", closed=").append(report.closed())
                .append(", reason=").append(reason)
                .append('\n');
        for (GitHubPullRequestService.PullRequestCloseResult result : report.results()) {
            output.append("- #").append(result.number())
                    .append(" ").append(result.status())
                    .append(" HTTP ").append(result.statusCode())
                    .append(" ").append(result.url());
            if (!result.message().isBlank()) {
                output.append(" :: ").append(compact(result.message(), 260));
            }
            output.append('\n');
        }
        return new ToolObservation("close_unmerged_pull_requests",
                report.closed() == report.requested() ? "ok" : "partial",
                trim(output.toString()));
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

    private ToolObservation createAtomicWishlist(ProjectEntity project, JsonNode args) {
        String content = textArg(args, "content", "");
        if (content.isBlank()) {
            return new ToolObservation("create_atomic_wishlist", "blocked",
                    "content is required. The content must be a short English EMS work item with JTBD, owner role, Kano, Cynefin, DoD, and verification boundary.");
        }
        String sourceRoleTag = textArg(args, "sourceRoleTag", "BARCAN-TAG-09");
        if (!sourceRoleTag.matches("BARCAN-TAG-(0[0-9]|1[0-1])")) {
            sourceRoleTag = "BARCAN-TAG-09";
        }
        String normalized = """
                EMS atomic wishlist item.
                Source role: %s
                Rule: this item must compile into one short Jules session per owner role, with no duplicated role fan-out.

                %s
                """.formatted(sourceRoleTag, content.trim());
        var response = projectFlowService.addWishlistItem(
                project.getId(),
                new WishlistRequestDto(project.getId(), WishlistSource.role, sourceRoleTag, normalized)
        );
        return new ToolObservation("create_atomic_wishlist", "ok",
                "wishlistId=" + response.id()
                        + ", status=" + response.status()
                        + ", sourceRoleTag=" + response.sourceRoleTag()
                        + ", next=compile_dependency_graph");
    }

    private ToolObservation compileDependencyGraph(ProjectEntity project) {
        StringBuilder output = new StringBuilder();
        Optional<UUID> bootstrap = projectFlowService.ensureEnvironmentBootstrapWork(project.getId());
        output.append("environmentBootstrapTask=")
                .append(bootstrap.map(UUID::toString).orElse("already_present"))
                .append('\n');
        try {
            OrchestrationResultDto orchestration = projectFlowService.orchestrate(project.getId());
            output.append("processedWishlistItems=").append(orchestration.processedWishlistItems())
                    .append(", createdTasks=").append(orchestration.createdTasks().size())
                    .append(", message=").append(orchestration.message());
            return new ToolObservation("compile_dependency_graph", "ok", trim(output.toString()));
        } catch (OrchestrationCooldownException e) {
            output.append("orchestrationCooldownSeconds=").append(e.getRetryAfterSeconds())
                    .append("\nGraph compile was partially completed if bootstrap was created; retry after cooldown.");
            return new ToolObservation("compile_dependency_graph", "partial", trim(output.toString()));
        }
    }

    private ToolObservation dispatchNextBestTask(ProjectEntity project) {
        projectFlowService.dispatchQueuedTasks(project.getId());
        projectFlowService.dispatchReviewTasks(project.getId());
        return new ToolObservation("dispatch_next_best_task", "ok",
                "Ran dependency-aware queued/review dispatch for the selected project. The backend chooses the next eligible task by queue priority, dependency constraints, file-scope conflicts, and universal Jules capacity.");
    }

    private ToolObservation closeBadSession(ProjectEntity project, JsonNode args) {
        UUID sessionId = uuidArg(args, "sessionId");
        String reason = textArg(args, "reason",
                "operator_bad_session_closed: repeated loop, stale work, irrelevant activity, or no concrete next action");
        Map<String, Object> result = projectFlowService.closeBadJulesSession(project.getId(), sessionId, reason);
        try {
            return new ToolObservation("close_bad_session", "closed".equals(result.get("status")) ? "ok" : "empty",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (Exception e) {
            return new ToolObservation("close_bad_session", "ok", String.valueOf(result));
        }
    }

    private ToolObservation postmortemToWishlist(ProjectEntity project, JsonNode args) {
        UUID sessionId = uuidArg(args, "sessionId");
        if (sessionId == null) {
            return new ToolObservation("postmortem_to_wishlist", "blocked", "sessionId is required.");
        }
        String reason = textArg(args, "reason", "operator_postmortem: convert bad Jules session into fresh atomic recovery work");
        Optional<UUID> wishlistId = projectFlowService.createSessionPostmortemWishlist(project.getId(), sessionId, reason);
        return new ToolObservation("postmortem_to_wishlist", "ok",
                wishlistId.map(id -> "wishlistId=" + id + ", status=pending")
                        .orElse("Postmortem wishlist already exists for session " + sessionId));
    }

    private ToolObservation ensureEnvironmentBootstrapWork(ProjectEntity project) {
        Optional<UUID> createdTaskId = projectFlowService.ensureEnvironmentBootstrapWork(project.getId());
        if (createdTaskId.isPresent()) {
            return new ToolObservation("ensure_environment_bootstrap_work", "ok",
                    "Created EMS environment/bootstrap task " + createdTaskId.get()
                            + " for repository boundary, setup commands, and backend/frontend execution contract.");
        }
        return new ToolObservation("ensure_environment_bootstrap_work", "ok",
                "EMS environment/bootstrap task already exists for this project; no duplicate was created.");
    }

    private ToolObservation projectDecisionMemory(ProjectEntity project, JsonNode args, String userMessage) {
        String note = textArg(args, "note", textArg(args, "content", ""));
        if (note.isBlank()) {
            return projectMemoryRead(project);
        }
        return mutating(userMessage, "project_decision_memory", () -> projectMemoryAppend(project, args));
    }

    private ToolObservation ensureProjectWorkspace(ProjectEntity project) {
        Path workspace = resolveProjectWorkspace(project);
        String repoUrl = projectRepositoryUrl(project);
        if (repoUrl.isBlank()) {
            return new ToolObservation("ensure_project_workspace", "blocked",
                    "Repository URL is not available for project " + project.getId()
                            + ". repositoryName=" + valueOrUnset(project.getRepositoryName()));
        }
        if (!workspace.startsWith(workspaceRoot)) {
            return new ToolObservation("ensure_project_workspace", "blocked",
                    "Workspace path is outside the configured workspace root: " + workspace);
        }
        try {
            Files.createDirectories(workspaceRoot);
            StringBuilder output = new StringBuilder();
            output.append("repoUrl=").append(repoUrl).append('\n');
            output.append("workspace=").append(workspace).append('\n');
            Optional<String> githubToken = githubToken();
            if (repoUrl.startsWith("https://github.com/")) {
                output.append("githubAuth=").append(githubToken.isPresent() ? "token configured" : "token missing").append('\n');
            }

            if (Files.isDirectory(workspace.resolve(".git"))) {
                ToolObservation fetch = runGitWithOptionalToken("ensure_project_workspace_fetch", workspace, Duration.ofSeconds(45),
                        List.of("git", "fetch", "--all", "--prune"), githubToken);
                ToolObservation status = run("ensure_project_workspace_status", workspace, Duration.ofSeconds(10),
                        List.of("git", "status", "--short"));
                project.setWorkspacePath(workspace.toString());
                projectRepository.save(project);
                return new ToolObservation("ensure_project_workspace", "ok",
                        output + "Workspace already contains .git. Refreshed remotes.\n"
                                + fetch.output() + "\n" + status.output());
            }

            if (Files.exists(workspace) && !isDirectoryEmpty(workspace)) {
                Path backup = workspace.resolveSibling(workspace.getFileName() + "-nongit-backup-" + Instant.now().toString().replaceAll("[^0-9T]", ""));
                if (!backup.startsWith(workspaceRoot)) {
                    return new ToolObservation("ensure_project_workspace", "blocked", "Backup path escaped workspace root: " + backup);
                }
                Files.move(workspace, backup);
                output.append("Moved non-git workspace to backup=").append(backup).append('\n');
            } else if (Files.exists(workspace) && isDirectoryEmpty(workspace)) {
                Files.delete(workspace);
                output.append("Removed empty non-git workspace directory.\n");
            }

            Path parent = workspace.getParent();
            Files.createDirectories(parent);
            ToolObservation clone = runGitWithOptionalToken("ensure_project_workspace_clone", parent, Duration.ofSeconds(120),
                    List.of("git", "clone", repoUrl, workspace.getFileName().toString()), githubToken);
            if (!"ok".equals(clone.status())) {
                return new ToolObservation("ensure_project_workspace", clone.status(), output + clone.output());
            }
            project.setWorkspacePath(workspace.toString());
            projectRepository.save(project);
            ToolObservation status = run("ensure_project_workspace_status", workspace, Duration.ofSeconds(10),
                    List.of("git", "status", "--short"));
            return new ToolObservation("ensure_project_workspace", "ok",
                    output + "Cloned repository into project workspace and saved workspacePath.\n"
                            + clone.output() + "\n" + status.output());
        } catch (Exception e) {
            return new ToolObservation("ensure_project_workspace", "error", e.getMessage());
        }
    }

    private ToolObservation startTestingStream(ProjectEntity project) {
        StringBuilder output = new StringBuilder();
        ToolObservation workspace = ensureProjectWorkspace(project);
        output.append("## ensure_project_workspace [").append(workspace.status()).append("]\n")
                .append(workspace.output()).append("\n\n");

        Path root = resolveProjectWorkspace(project);
        ToolObservation checks = testPlan(root);
        output.append("## detected_checks [").append(checks.status()).append("]\n")
                .append(checks.output()).append("\n\n");

        String content = testingStreamWishlist(project, checks);
        try {
            var wishlist = projectFlowService.addWishlistItem(
                    project.getId(),
                    new WishlistRequestDto(project.getId(), WishlistSource.role, "BARCAN-TAG-06", content)
            );
            output.append("Created QA wishlist item: ").append(wishlist.id())
                    .append(" status=").append(wishlist.status()).append('\n');

            try {
                OrchestrationResultDto orchestration = projectFlowService.orchestrate(project.getId());
                output.append("Ran orchestration: processedWishlistItems=")
                        .append(orchestration.processedWishlistItems())
                        .append(", createdTasks=").append(orchestration.createdTasks().size())
                        .append(", message=").append(orchestration.message()).append('\n');
            } catch (Exception e) {
                output.append("Orchestration was not completed: ").append(e.getMessage()).append('\n');
                return new ToolObservation("start_testing_stream", "partial", trim(output.toString()));
            }

            try {
                projectFlowService.dispatchQueuedTasks(project.getId());
                projectFlowService.dispatchReviewTasks(project.getId());
                output.append("Ran dispatchQueuedTasks + dispatchReviewTasks.\n");
            } catch (Exception e) {
                output.append("Dispatch was not completed: ").append(e.getMessage()).append('\n');
                return new ToolObservation("start_testing_stream", "partial", trim(output.toString()));
            }
            return new ToolObservation("start_testing_stream", "ok", trim(output.toString()));
        } catch (Exception e) {
            output.append("Failed to create QA wishlist item: ").append(e.getMessage()).append('\n');
            return new ToolObservation("start_testing_stream", "error", trim(output.toString()));
        }
    }

    private ToolObservation deepDiagnosticWorker(ProjectEntity project,
                                                 ProjectOperationalContext context,
                                                 String userMessage,
                                                 JsonNode args) {
        String mission = textArg(args, "mission", "");
        String request = mission.isBlank() ? userMessage : mission;

        StringBuilder output = new StringBuilder();
        try {
            String content = deepDiagnosticWishlist(project, request);
            var wishlist = projectFlowService.addWishlistItem(
                    project.getId(),
                    new WishlistRequestDto(project.getId(), WishlistSource.role, "BARCAN-TAG-00", content)
            );
            output.append("Created deep-diagnostic wishlist item: ").append(wishlist.id())
                    .append(" status=").append(wishlist.status()).append('\n');

            try {
                OrchestrationResultDto orchestration = projectFlowService.orchestrate(project.getId());
                output.append("Ran orchestration: processedWishlistItems=")
                        .append(orchestration.processedWishlistItems())
                        .append(", createdTasks=").append(orchestration.createdTasks().size())
                        .append(", message=").append(orchestration.message()).append('\n');
            } catch (Exception e) {
                output.append("Orchestration was not completed: ").append(e.getMessage()).append('\n');
                return new ToolObservation("deep_diagnostic_worker", "partial", trim(output.toString()));
            }

            try {
                projectFlowService.dispatchQueuedTasks(project.getId());
                output.append("Dispatched the resulting task(s) to Jules for diagnosis.\n");
            } catch (Exception e) {
                output.append("Dispatch was not completed: ").append(e.getMessage()).append('\n');
                return new ToolObservation("deep_diagnostic_worker", "partial", trim(output.toString()));
            }
            return new ToolObservation("deep_diagnostic_worker", "ok", trim(output.toString()));
        } catch (Exception e) {
            output.append("Failed to create deep-diagnostic wishlist item: ").append(e.getMessage()).append('\n');
            return new ToolObservation("deep_diagnostic_worker", "error", trim(output.toString()));
        }
    }

    private String deepDiagnosticWishlist(ProjectEntity project, String request) {
        return """
                Run a deep engineering diagnostic for the current project.
                Decompose this into short English Jules tasks owned by the role best suited to the root cause
                (BARCAN-TAG-00 by default for defect/quality-gate work, or a more specific role if the request
                names one). Each task must be atomic, role-owned, and finishable in one short Jules session.
                Diagnose and, when feasible, repair the smallest root cause; prove the fix with the project's
                real tests. If the code cannot be changed safely, produce an evidence-backed diagnosis instead
                of a speculative change.
                For every task include JTBD, Kano, Cynefin, DoD, acceptance criteria, and a concrete
                verification command.
                """ + "\nProject: " + project.getName() + "\nRequest: " + (request == null || request.isBlank()
                ? "Run a deep diagnostic for the next stalled project work." : request);
    }

    private ToolObservation googleAiResourceCatalog() {
        try {
            return new ToolObservation(
                    "google_ai_resource_catalog",
                    "ok",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(googleAiResourceService.resourceMatrix())
            );
        } catch (Exception e) {
            return new ToolObservation("google_ai_resource_catalog", "error", e.getMessage());
        }
    }

    private ToolObservation googleSearchResearch(ProjectOperationalContext context, JsonNode args, String userMessage) {
        String question = textArg(args, "question", userMessage == null ? "" : userMessage);
        var result = googleAiResourceService.googleSearchResearch(question, context.promptJson());
        StringBuilder output = new StringBuilder();
        output.append("Google Search research result\n")
                .append("status=").append(result.status()).append('\n')
                .append("model=").append(valueOrUnset(result.model())).append("\n\n")
                .append(result.outputText().isBlank() ? result.rawPreview() : result.outputText());
        return new ToolObservation(
                "google_search_research",
                result.available() ? result.status() : "missing",
                trim(output.toString())
        );
    }

    private ToolObservation urlContextResearch(ProjectOperationalContext context, JsonNode args, String userMessage) {
        String url = textArg(args, "url", firstUrl(userMessage == null ? "" : userMessage));
        String question = textArg(args, "question", userMessage == null ? "" : userMessage);
        if (url.isBlank()) {
            return new ToolObservation("url_context_research", "blocked", "url is required.");
        }
        var result = googleAiResourceService.urlContextResearch(url, question, context.promptJson());
        StringBuilder output = new StringBuilder();
        output.append("URL Context research result\n")
                .append("url=").append(url).append('\n')
                .append("status=").append(result.status()).append('\n')
                .append("model=").append(valueOrUnset(result.model())).append("\n\n")
                .append(result.outputText().isBlank() ? result.rawPreview() : result.outputText());
        return new ToolObservation(
                "url_context_research",
                result.available() ? result.status() : "missing",
                trim(output.toString())
        );
    }

    private ToolObservation designAssetGenerate(ProjectEntity project,
                                                ProjectOperationalContext context,
                                                JsonNode args,
                                                String userMessage) {
        String brief = textArg(args, "brief", userMessage == null ? "" : userMessage);
        String assetType = textArg(args, "assetType", "visual_asset");
        String quality = textArg(args, "quality", "fast");
        boolean useGoogleSearch = args != null && args.has("useGoogleSearch") && args.get("useGoogleSearch").asBoolean(false);
        var result = designAssetService.generateAsset(project, context, brief, assetType, quality, useGoogleSearch);
        StringBuilder output = new StringBuilder();
        output.append("Design Asset Service result\n")
                .append("status=").append(result.status()).append('\n')
                .append("model=").append(valueOrUnset(result.model())).append('\n')
                .append("imagePath=").append(valueOrUnset(result.imagePath())).append('\n')
                .append("metadataPath=").append(valueOrUnset(result.metadataPath())).append('\n')
                .append("mimeType=").append(valueOrUnset(result.mimeType())).append("\n\n")
                .append(result.message());
        return new ToolObservation(
                "design_asset_generate",
                result.available() ? result.status() : "missing",
                trim(output.toString())
        );
    }

    private ToolObservation videoAssetGenerate(ProjectEntity project,
                                               ProjectOperationalContext context,
                                               JsonNode args,
                                               String userMessage) {
        String brief = textArg(args, "brief", userMessage == null ? "" : userMessage);
        String assetType = textArg(args, "assetType", "project_video");
        String quality = textArg(args, "quality", "standard");
        boolean useGoogleSearch = args != null && args.has("useGoogleSearch") && args.get("useGoogleSearch").asBoolean(false);
        var result = videoAssetService.generateAsset(project, context, brief, assetType, quality, useGoogleSearch);
        StringBuilder output = new StringBuilder();
        output.append("Video Asset Service result\n")
                .append("status=").append(result.status()).append('\n')
                .append("model=").append(valueOrUnset(result.model())).append('\n')
                .append("videoPath=").append(valueOrUnset(result.videoPath())).append('\n')
                .append("metadataPath=").append(valueOrUnset(result.metadataPath())).append('\n')
                .append("mimeType=").append(valueOrUnset(result.mimeType())).append("\n\n")
                .append(result.message());
        return new ToolObservation(
                "video_asset_generate",
                result.available() ? result.status() : "missing",
                trim(output.toString())
        );
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

    private String projectRepositoryUrl(ProjectEntity project) {
        String direct = firstNonBlank(project.getRepositoryUrl(), project.getRepoUrl());
        if (!direct.isBlank()) {
            return direct;
        }
        String repositoryName = project.getRepositoryName();
        if (repositoryName == null || repositoryName.isBlank()) {
            repositoryName = project.getSlug();
        }
        if (repositoryName == null || repositoryName.isBlank()) {
            return "";
        }
        if (repositoryName.contains("/")) {
            return "https://github.com/" + repositoryName;
        }
        return "https://github.com/" + githubOrganization + "/" + repositoryName;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isDirectoryEmpty(Path directory) throws java.io.IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.findAny().isEmpty();
        }
    }

    private String testingStreamWishlist(ProjectEntity project, ToolObservation checks) {
        boolean hasRunnableChecks = "ok".equals(checks.status());
        String readinessClause = hasRunnableChecks
                ? "Use the detected test/build commands as verification anchors."
                : "If frontend/backend code is not present yet, create only repository-aware test readiness, smoke harness, and QA checklist tasks; do not invent unimplemented product behavior.";
        return """
                Start a risk-based QA testing stream for the current project.
                Decompose this wishlist into short English Jules tasks owned primarily by BARCAN-TAG-06, with supporting DevOps or frontend/backend roles only when the task truly belongs there.
                Each task must be atomic, role-owned, and finishable in one short Jules session.
                Use ISTQB's seven testing principles pragmatically: testing shows defects, exhaustive testing is impossible, early testing, defect clustering, pesticide paradox, context dependence, and absence-of-errors fallacy.
                Required slices:
                1. Inspect the repository and define the minimum test strategy for implemented behavior only.
                2. Add or verify a smoke test command that can run in CI.
                3. Add API contract tests only for implemented backend endpoints.
                4. Add UI happy-path or E2E tests only for implemented frontend flows.
                5. Add a regression checklist and document the exact verification command.
                For every task include JTBD, Kano, Cynefin, DoD, acceptance criteria, and a concrete verification command or NOT AVAILABLE when no code exists yet.
                """ + "\nProject: " + project.getName() + "\n" + readinessClause;
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

    private UUID uuidArg(JsonNode node, String field) {
        String raw = textArg(node, field, "");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        if (looksLikeOperatorExecutionIntent(lower)) {
            return true;
        }
        if (looksLikeDeepDiagnosticIntent(lower)) {
            return true;
        }
        if (looksLikeDesignAssetIntent(lower)) {
            return true;
        }
        return containsAny(lower,
                "run ", "start ", "create ", "add ", "execute ", "build ", "pull ", "dispatch", "orchestrate", "generate ", "produce ",
                "continue", "recover", "unblock", "restart", "replace", "remember", "save ", "persist ", "fix", "repair", "clone", "close ",
                "\u0437\u0430\u043f\u0443\u0441\u0442\u0438", "\u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u044c",
                "\u043d\u0430\u0447\u043d", "\u043d\u0430\u0447\u0438", "\u043f\u0440\u043e\u0434\u043e\u043b\u0436", "\u0432\u043e\u0437\u043e\u0431\u043d",
                "\u0441\u0434\u0435\u043b\u0430\u0439", "\u0441\u043e\u0437\u0434\u0430\u0439", "\u0441\u043e\u0437\u0434\u0430\u0442\u044c",
                "\u0434\u043e\u0431\u0430\u0432\u044c", "\u0434\u043e\u0431\u0430\u0432\u0438\u0442\u044c",
                "\u043e\u0440\u043a\u0435\u0441\u0442\u0440", "\u0434\u0438\u0441\u043f\u0430\u0442\u0447",
                "\u043f\u043e\u0434\u043d\u0438\u043c\u0438", "\u0441\u0431\u0435\u0440\u0438", "\u0431\u0438\u043b\u0434",
                "\u0437\u0430\u043f\u043e\u043c\u043d\u0438", "\u0441\u043e\u0445\u0440\u0430\u043d\u0438",
                "\u0438\u0441\u043f\u0440\u0430\u0432", "\u043f\u043e\u0447\u0438\u043d", "\u0440\u0430\u0437\u0431\u043b\u043e\u043a",
                "\u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432", "\u0437\u0430\u043c\u0435\u043d\u0438", "\u0440\u0430\u0437\u0433\u0440\u0435\u0431",
                "\u0440\u0430\u0437\u0431\u0435\u0440", "\u0440\u0430\u0437\u043e\u0431\u0440",
                "\u0441\u043a\u043b\u043e\u043d\u0438\u0440", "\u0437\u0430\u043a\u0440\u043e", "\u0437\u0430\u043a\u0440\u044b");
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
        return run(name, cwd, timeout, command, Map.of(), List.of());
    }

    private ToolObservation run(String name,
                                Path cwd,
                                Duration timeout,
                                List<String> command,
                                Map<String, String> environment,
                                List<String> redactions) {
        if (!Files.isDirectory(cwd)) {
            return new ToolObservation(name, "missing", "Working directory does not exist: " + cwd);
        }
        if (!isAllowedRoot(cwd)) {
            return new ToolObservation(name, "blocked", "Working directory is outside allowed operator roots: " + cwd);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(cwd.toFile());
            if (environment != null && !environment.isEmpty()) {
                builder.environment().putAll(environment);
            }
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
                return new ToolObservation(name, "timeout",
                        redact("Command timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command)
                                + "\nPartial output:\n" + trim(output.toString()), redactions));
            }
            readerThread.join(1_000);
            String status = process.exitValue() == 0 ? "ok" : "failed_exit_" + process.exitValue();
            String renderedOutput = redact(trim(output.toString()), redactions);
            if (truncated[0]) {
                renderedOutput += "\n... [truncated]";
            }
            return new ToolObservation(name, status,
                    "$ " + redact(String.join(" ", command), redactions) + "\n" + renderedOutput);
        } catch (Exception e) {
            return new ToolObservation(name, "error",
                    "$ " + redact(String.join(" ", command), redactions) + "\n" + redact(e.getMessage(), redactions));
        }
    }

    private ToolObservation runGitWithOptionalToken(String name,
                                                    Path cwd,
                                                    Duration timeout,
                                                    List<String> command,
                                                    Optional<String> githubToken) {
        if (githubToken.isEmpty()) {
            return run(name, cwd, timeout, command);
        }

        Path askPass = null;
        try {
            askPass = Files.createTempFile("eneik-git-askpass-", isWindowsHost() ? ".cmd" : ".sh");
            Files.writeString(askPass, askPassScript(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            askPass.toFile().setExecutable(true, true);

            Map<String, String> environment = new HashMap<>();
            environment.put("GIT_ASKPASS", askPass.toString());
            environment.put("GIT_TERMINAL_PROMPT", "0");
            environment.put("GIT_USERNAME", "x-access-token");
            environment.put("GIT_PASSWORD", githubToken.get());

            return run(name, cwd, timeout, command, environment, List.of(githubToken.get()));
        } catch (Exception e) {
            return new ToolObservation(name, "error",
                    "$ " + String.join(" ", command) + "\nUnable to configure authenticated Git command: " + e.getMessage());
        } finally {
            if (askPass != null) {
                try {
                    Files.deleteIfExists(askPass);
                } catch (Exception ignored) {
                    // Temporary askpass cleanup is best-effort.
                }
            }
        }
    }

    private Optional<String> githubToken() {
        if (settingsService == null) {
            return Optional.empty();
        }
        try {
            String token = settingsService.effectiveValue("github_token");
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(token.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String askPassScript() {
        if (isWindowsHost()) {
            return """
                    @echo off
                    echo %* | findstr /i "Username" >nul
                    if %ERRORLEVEL%==0 (
                      echo %GIT_USERNAME%
                    ) else (
                      echo %GIT_PASSWORD%
                    )
                    """;
        }
        return """
                #!/bin/sh
                case "$1" in
                  *Username*) printf '%s\\n' "${GIT_USERNAME:-x-access-token}" ;;
                  *) printf '%s\\n' "$GIT_PASSWORD" ;;
                esac
                """;
    }

    private boolean isWindowsHost() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String redact(String value, List<String> redactions) {
        if (value == null || redactions == null || redactions.isEmpty()) {
            return value;
        }
        String result = value;
        for (String secret : redactions) {
            if (secret != null && !secret.isBlank()) {
                result = result.replace(secret, "<redacted>");
            }
        }
        return result;
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
        Optional<ToolObservation> observation(String tool) {
            return observations.stream()
                    .filter(item -> item.tool().equals(tool))
                    .reduce((first, second) -> second);
        }

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
