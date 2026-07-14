package com.eneik.production.services.jules;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.JulesActivityResponseEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.RoleCapabilityLoader;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.repositories.RoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class JulesDispatchService {
    private static final Logger log = LoggerFactory.getLogger(JulesDispatchService.class);
    private static final List<String> ACTIVE_SESSION_STATUSES = List.of("running", "queued", "revising", "pr_opened", "stuck");
    private static final Duration STUCK_RECOVERY_MESSAGE_INTERVAL = Duration.ofMinutes(15);
    private static final int DESTRUCTIVE_LOOP_REPEAT_THRESHOLD = 2;
    private static final int FOLLOW_UP_CONTENT_MAX_LENGTH = 7_500;
    private static final String REVIEW_REJECTION_ACTIVITY_NAME = "system-pr-review-rejection";

    private final JulesApiClient julesApiClient;
    private final JulesSessionRepository julesSessionRepository;
    private final JulesActivityResponseRepository julesActivityResponseRepository;
    private final WishlistRepository wishlistRepository;
    private final com.eneik.production.repositories.AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final ClaimService claimService;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService;
    private final com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient;
    private final RoleRepository roleRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final String sourcePrefix;

    @Value("${jules.stuck-threshold-minutes:30}")
    private int stuckThresholdMinutes;

    @Value("${jules.max-agent-dialog-responses:8}")
    private int maxAgentDialogResponses;

    @Value("${jules.loop-close-similar-threshold:2}")
    private int loopCloseSimilarThreshold;

    @Value("${jules.stuck-close-threshold-minutes:120}")
    private int stuckCloseThresholdMinutes;

    @Value("${jules.max-active-session-minutes:180}")
    private int maxActiveSessionMinutes;

    @Value("${jules.max-loop-closures-per-run:5}")
    private int maxLoopClosuresPerRun;

    public JulesDispatchService(JulesApiClient julesApiClient,
                                JulesSessionRepository julesSessionRepository,
                                JulesActivityResponseRepository julesActivityResponseRepository,
                                WishlistRepository wishlistRepository,
                                com.eneik.production.repositories.AccountRepository accountRepository,
                                TaskRepository taskRepository,
                                TaskConflictRepository taskConflictRepository,
                                ClaimService claimService,
                                RoleCapabilityLoader roleCapabilityLoader,
                                com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService,
                                com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient,
                                RoleRepository roleRepository,
                                GitHubPullRequestService gitHubPullRequestService,
                                @Value("${jules.source-prefix:sources/github/${github.org}/}") String sourcePrefix) {
        this.julesApiClient = julesApiClient;
        this.julesSessionRepository = julesSessionRepository;
        this.julesActivityResponseRepository = julesActivityResponseRepository;
        this.wishlistRepository = wishlistRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.claimService = claimService;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.prReviewPipelineService = prReviewPipelineService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.roleRepository = roleRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.sourcePrefix = sourcePrefix;
    }

    @Transactional
    public JulesDispatchResult dispatch(TaskEntity task) {
        return dispatch(task, null);
    }

    @Transactional
    public JulesDispatchResult dispatch(TaskEntity task, UUID accountId) {
        return dispatch(task, accountId, "IMPLEMENTER");
    }

    @Transactional
    public JulesDispatchResult dispatch(TaskEntity task, UUID accountId, String mode) {
        List<JulesSessionEntity> existing = julesSessionRepository.findByTaskId(task.getId());
        for (JulesSessionEntity s : existing) {
            if ("skipped".equals(s.getExternalSessionId())) {
                julesSessionRepository.delete(s);
                continue;
            }
            if (ACTIVE_SESSION_STATUSES.contains(s.getStatus())) {
                log.info("Task {} already dispatched (status: {}), skipping duplicate", task.getId(), s.getStatus());
                return new JulesDispatchResult(true, s.getExternalSessionId(), "already dispatched, skipping duplicate");
            }
        }

        JulesSessionEntity session = dispatchInternal(task, accountId, mode);
        return new JulesDispatchResult(
                "running".equals(session.getStatus()) || "queued".equals(session.getStatus()),
                session.getExternalSessionId(),
                "skipped".equals(session.getExternalSessionId()) ? "Jules integration disabled" : "Dispatched to Jules"
        );
    }

    @Transactional
    public JulesSessionEntity dispatch(UUID taskId, UUID accountId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        // Ensure task is claimed if being dispatched directly via controller
        if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.queued && accountId != null) {
            claimService.claimSpecificTask(taskId, accountId);
        }

        dispatch(task, accountId);
        return julesSessionRepository.findByTaskId(taskId).stream()
                .filter(s -> accountId == null || accountId.equals(s.getAccountId()))
                .findFirst().orElse(null);
    }

    private JulesSessionEntity dispatchInternal(TaskEntity task, UUID accountId, String mode) {
        if (accountId != null) {
            if ("REVIEWER".equalsIgnoreCase(mode)) {
                claimService.claimReviewer(task.getId(), accountId);
            }
        }

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setAccountId(accountId);
        session.setStatus("queued");

        ProjectEntity project = task.getProject();
        if (project == null) {
            session.setStatus("failed");
            return julesSessionRepository.save(session);
        }

        String repoUrl = sourcePrefix + project.getRepositoryName();
        String description = task.getDescription();
        var conflictOpt = taskConflictRepository.findFirstByTaskIdAndResolutionStatus(task.getId(), "pending");
        if (conflictOpt.isPresent()) {
            var conflict = conflictOpt.get();
            String dod = "";
            if (task.getPayload() != null && task.getPayload().has("dod")) {
                dod = task.getPayload().get("dod").asText();
            }
            String conflictingFiles = conflict.getConflictingFiles();
            if (conflictingFiles == null || conflictingFiles.trim().isEmpty()) {
                conflictingFiles = "[]";
            }
            description = "Rebase your branch onto the current main and resolve merge conflicts. Original task: [" + dod + "]. Conflict is in: " + conflictingFiles + ".";
            log.info("Modified prompt for task {} because of merge conflict. New prompt: {}", task.getId(), description);
        } else if ("REVIEWER".equalsIgnoreCase(mode)) {
            description = "[REVIEWER MODE]\nAudit the following code changes against docs/AI_REVIEW_GUIDELINES.md.\n" + description;
        }

        StringBuilder roleContextBuilder = new StringBuilder();
        roleContextBuilder.append("Role: ").append(task.getRole().getTag()).append("\n");
        roleContextBuilder.append("Description: ").append(task.getRole().getDescription()).append("\n");
        roleContextBuilder.append("\n## Jules Execution Contract\n");
        roleContextBuilder.append("- Proceed autonomously from the task description, JTBD, Acceptance Criteria, DoD, and file scope.\n");
        roleContextBuilder.append("- Do not pause for broad optional confirmation when the Acceptance Criteria already imply a safe next step.\n");
        roleContextBuilder.append("- If a detail is ambiguous, use the smallest reversible implementation assumption, document it in the PR summary, and keep working.\n");
        roleContextBuilder.append("- Ask at most one concise blocker question only when continuing would create a concrete contradiction or security/data-loss risk.\n");
        roleContextBuilder.append("- Do not commit generated reports, screenshots, trace zips, test-results, playwright-report, node_modules, or local environment files.\n");
        roleContextBuilder.append("- Keep this session atomic: deliver one small service/component/fix and open the PR. Do not expand into new features, broad architecture rewrites, or extra verification branches.\n");
        roleContextBuilder.append("- If the work requires more than one atomic change, complete the smallest safe slice and describe the remaining slices in the PR summary instead of doing them in this branch.\n");
        roleContextBuilder.append("- Hard stop: after repeated blocker feedback or eight back-and-forth replies, the orchestrator may close this session and create new short follow-up wishlist items.\n");
        if ("BARCAN-TAG-06".equals(task.getRole().getTag())) {
            roleContextBuilder.append("- QA default: if you ask whether to continue verification, continue with required test ratios and deeper AC verification; document assumptions instead of waiting.\n");
        }

        appendCompactRoleGuide(roleContextBuilder, task.getRole().getTag());

        try {
            RoleRules rules = roleCapabilityLoader.loadRules(task.getRole().getTag());
            if (rules != null) {
                if (shouldIncludeVerboseRoleRuleSections() && rules.forbidden() != null && !rules.forbidden().isEmpty()) {
                    roleContextBuilder.append("\n## Forbidden (Запрещено)\n");
                    for (String f : rules.forbidden()) {
                        roleContextBuilder.append("- ").append(f).append("\n");
                    }
                }
                if (shouldIncludeVerboseRoleRuleSections() && rules.refusalCriteria() != null && !rules.refusalCriteria().isBlank()) {
                    roleContextBuilder.append("\n").append(rules.refusalCriteria()).append("\n");
                }
                if (shouldIncludeVerboseRoleRuleSections() && rules.deonticStatus() != null && !rules.deonticStatus().isBlank()) {
                    roleContextBuilder.append("\n").append(rules.deonticStatus()).append("\n");
                }
                if (shouldIncludeVerboseRoleRuleSections() && rules.outputFormat() != null && !rules.outputFormat().isBlank()) {
                    roleContextBuilder.append("\n## Output Format / Definition of Done\n").append(rules.outputFormat()).append("\n");
                }
                if (rules.reviewRequiredBy() != null && !rules.reviewRequiredBy().isBlank()) {
                    roleContextBuilder.append("\n## Mandatory Review By\n").append(rules.reviewRequiredBy()).append("\n");
                }
            }

            if ("REVIEWER".equalsIgnoreCase(mode)) {
                try {
                    String guidelines = java.nio.file.Files.readString(java.nio.file.Paths.get("docs/AI_REVIEW_GUIDELINES.md"));
                    roleContextBuilder.append("\n## AI REVIEW GUIDELINES\n").append(guidelines).append("\n");
                } catch (Exception e) {
                    log.warn("Could not load AI review guidelines: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load extended rules for role {}: {}", task.getRole().getTag(), e.getMessage());
        }

        String roleContext = roleContextBuilder.toString();

        String apiKey = null;
        if (accountId != null) {
            apiKey = accountRepository.findById(accountId)
                    .map(com.eneik.production.models.persistence.AccountEntity::getApiKey)
                    .orElse(null);
        }

        String externalId = apiKey != null
                ? julesApiClient.createSession(repoUrl, description, roleContext, apiKey)
                : julesApiClient.createSession(repoUrl, description, roleContext);

        if ("skipped".equals(externalId)) {
            session.setStatus("queued");
            session.setExternalSessionId("skipped");
        } else if (externalId == null) {
            session.setStatus("failed");
        } else {
            session.setExternalSessionId(externalId);
            session.setStatus("running");
        }

        return julesSessionRepository.save(session);
    }

    private void appendCompactRoleGuide(StringBuilder roleContextBuilder, String roleTag) {
        roleContextBuilder.append("\n## Compact Role Guide\n");
        roleContextBuilder.append("- Use English only in code comments, PR text, and dialogue.\n");
        roleContextBuilder.append("- Treat the task JTBD, Acceptance Criteria, DoD, and file scope as stronger than role lore.\n");
        roleContextBuilder.append("- Apply Kano as a scope guard: Must-Be first, Performance only when explicit, Delighters only as follow-up wishlist.\n");
        roleContextBuilder.append("- Apply Cynefin as a delivery guard: clear/complicated work needs a direct implementation path, complex work needs one safe probe.\n");
        roleContextBuilder.append("- Role focus: ").append(compactRoleFocus(roleTag)).append("\n");
    }

    private boolean shouldIncludeVerboseRoleRuleSections() {
        return false;
    }

    private String compactRoleFocus(String roleTag) {
        return switch (roleTag) {
            case "BARCAN-TAG-00" -> "protect architecture, code quality, and merge safety for the smallest requested slice.";
            case "BARCAN-TAG-01" -> "define or adjust solution structure only where the current slice requires it.";
            case "BARCAN-TAG-02" -> "implement backend API/data behavior with focused tests and no frontend expansion.";
            case "BARCAN-TAG-03" -> "produce only the UI/UX design decision, interaction state, or design-system adjustment required by this slice.";
            case "BARCAN-TAG-04" -> "implement or verify the ML/data-science logic required by this slice with reproducible checks.";
            case "BARCAN-TAG-05" -> "change only deployment, runtime, CI, or observability items required to run the slice.";
            case "BARCAN-TAG-06" -> "verify acceptance criteria with the smallest useful unit/integration/E2E coverage; do not create broad test suites.";
            case "BARCAN-TAG-07" -> "check and fix concrete security risks without broad compliance rewrites.";
            case "BARCAN-TAG-08" -> "change only the database, schema, or data pipeline behavior required by this slice.";
            case "BARCAN-TAG-09" -> "decompose wishlist context into short, role-owned, dependency-aware work only.";
            case "BARCAN-TAG-10" -> "verify explicit legal, fiscal, privacy, or policy constraints with cited assumptions.";
            case "BARCAN-TAG-11" -> "implement the smallest Svelte/browser UI interaction required by the task and follow docs/DESIGN_SYSTEM.md.";
            default -> "complete one atomic, verifiable implementation slice and stop.";
        };
    }


    @Transactional
    public JulesSessionEntity pollStatus(UUID sessionId) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if ("skipped".equals(session.getExternalSessionId()) || session.getExternalSessionId() == null) {
            return session;
        }

        String apiKey = apiKeyForSession(session);

        String rawStatus = apiKey != null
                ? julesApiClient.getSessionStatus(session.getExternalSessionId(), apiKey)
                : julesApiClient.getSessionStatus(session.getExternalSessionId());
        if (rawStatus != null) {
            String oldStatus = session.getStatus();
            // If we are waiting for Jules to revise, ignore 'SUCCEEDED' from API to avoid infinite loop
            String mappedStatus = mapExternalStatus(rawStatus);
            boolean shouldSendStuckRecovery = "stuck".equals(mappedStatus) && shouldSendStuckRecovery(session);
            TaskEntity taskForSession = taskRepository.findById(session.getTaskId()).orElse(null);

            if ("revising".equals(oldStatus) && "pr_opened".equals(mappedStatus)) {
                // Ignore SUCCEEDED status from API if we haven't seen it go back to RUNNING yet
                mappedStatus = "revising";
            } else if ("revising".equals(oldStatus) && "running".equals(mappedStatus)) {
                // Jules picked it up and is running again
                log.info("Jules session {} resumed running after revision request.", session.getId());
            }

            if ("pr_opened".equals(mappedStatus)) {
                String realPrUrl = apiKey != null
                        ? julesApiClient.getSessionPrUrl(session.getExternalSessionId(), apiKey)
                        : julesApiClient.getSessionPrUrl(session.getExternalSessionId());
                if (realPrUrl != null && !realPrUrl.isBlank()) {
                    session.setPrUrl(realPrUrl);
                    log.info("Jules API: Retrieved real PR URL for session {}: {}", session.getId(), realPrUrl);
                }
            }

            if (taskForSession != null
                    && !"pr_opened".equals(mappedStatus)
                    && (session.getPrUrl() == null || session.getPrUrl().isBlank())
                    && !"revising".equals(oldStatus)) {
                Optional<GitHubPullRequestService.GitHubPullRequest> detectedPr = detectOpenPullRequestFromGitHub(session, taskForSession);
                if (detectedPr.isPresent()) {
                    GitHubPullRequestService.GitHubPullRequest pr = detectedPr.get();
                    session.setPrUrl(pr.url());
                    log.info("GitHub PR lookup: linked Jules session {} to PR {} via branch {}", session.getExternalSessionId(), pr.url(), pr.headRef());
                }
                if (session.getPrUrl() != null && !session.getPrUrl().isBlank()) {
                    mappedStatus = "pr_opened";
                }
            }

            session.setStatus(mappedStatus);
            session.setLastStatusCheckAt(Instant.now());
            session = julesSessionRepository.save(session);

            if (taskForSession != null && shouldScanActivitiesForQuestions(mappedStatus)) {
                answerAgentQuestions(session, taskForSession, apiKey);
            }

            // Ensure we only trigger PR opened workflow once per true transition from running/revising
            if ("pr_opened".equals(mappedStatus) && ("running".equals(oldStatus) || "revising".equals(oldStatus))) {
                handlePrOpenedWorkflow(session);
            }

            if (shouldSendStuckRecovery && taskForSession != null) {
                sendStuckRecoveryMessageAsync(session, taskForSession, apiKey);
            }

            return session;
        }

        return session;
    }

    private Optional<GitHubPullRequestService.GitHubPullRequest> detectOpenPullRequestFromGitHub(JulesSessionEntity session, TaskEntity task) {
        if (session.getPrUrl() != null && !session.getPrUrl().isBlank()) {
            return Optional.empty();
        }
        if (task.getProject() == null || session.getExternalSessionId() == null || "skipped".equals(session.getExternalSessionId())) {
            return Optional.empty();
        }
        return gitHubPullRequestService.findOpenPullRequestBySession(task.getProject(), session.getExternalSessionId());
    }

    private boolean shouldScanActivitiesForQuestions(String status) {
        return "running".equals(status)
                || "queued".equals(status)
                || "revising".equals(status)
                || "stuck".equals(status);
    }

    private void answerAgentQuestions(JulesSessionEntity session, TaskEntity task, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        if (isTerminalLocallyClosed(session)) {
            return;
        }

        JsonNode root = julesApiClient.getSessionActivities(session.getExternalSessionId(), apiKey);
        if (root != null && root.path("activitiesOverflow").asBoolean(false)) {
            closeLoopAndCreateFollowUps(
                    session,
                    task,
                    "Jules activities payload exceeded the backend safety limit before Eneik could parse new agent questions.",
                    List.of(),
                    "activity_log_overflow: Jules activity log exceeded the safe scanner limit"
            );
            return;
        }
        if (root == null || !root.path("activities").isArray()) {
            return;
        }

        for (JsonNode activity : root.path("activities")) {
            String question = extractAgentQuestion(activity);
            if (question == null || question.isBlank()) {
                continue;
            }

            String activityName = activity.path("name").asText(activity.path("id").asText("unknown"));
            String activityHash = sha256(activityName + "\n" + question);
            Optional<JulesActivityResponseEntity> existing =
                    julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(session.getId(), activityHash);
            if (existing.isPresent() && existing.get().isSent()) {
                continue;
            }

            try {
                List<JulesActivityResponseEntity> responseHistory =
                        julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
                long previousSimilarQuestions = countPreviousSimilarQuestions(responseHistory, question);
                long previousResponses = responseHistory.stream()
                        .filter(record -> record.getResponse() != null && !record.getResponse().isBlank())
                        .count();

                if (shouldCloseLoop(previousResponses, previousSimilarQuestions)) {
                    JulesActivityResponseEntity record = existing.orElseGet(JulesActivityResponseEntity::new);
                    record.setJulesSessionId(session.getId());
                    record.setActivityName(truncate(activityName, 256));
                    record.setActivityHash(activityHash);
                    record.setQuestion(question);
                    String closeReason = closeReason(previousResponses, previousSimilarQuestions, question);
                    record.setResponse("Eneik circuit breaker closed this Jules session. " + closeReason);
                    record.setSent(false);
                    record.setRespondedAt(Instant.now());
                    julesActivityResponseRepository.save(record);

                    closeLoopAndCreateFollowUps(session, task, question, responseHistory, closeReason);
                    break;
                }

                String answer = buildJulesQuestionAnswer(task, question, previousSimilarQuestions);
                boolean sent = julesApiClient.sendMessage(session.getExternalSessionId(), answer, apiKey);

                JulesActivityResponseEntity record = existing.orElseGet(JulesActivityResponseEntity::new);
                record.setJulesSessionId(session.getId());
                record.setActivityName(truncate(activityName, 256));
                record.setActivityHash(activityHash);
                record.setQuestion(question);
                record.setResponse(answer);
                record.setSent(sent);
                record.setRespondedAt(sent ? Instant.now() : null);
                julesActivityResponseRepository.save(record);

                if (sent) {
                    log.info("Answered Jules agent question activity {} for session {} task {}", activityName, session.getExternalSessionId(), task.getId());
                    saveJulesDialogueLog(task.getId(), session.getExternalSessionId(), answer, "Auto-answer to Jules activity " + activityName);
                } else {
                    log.warn("Generated but failed to send Jules agent question answer for session {} activity {}", session.getExternalSessionId(), activityName);
                }
            } catch (DataIntegrityViolationException e) {
                log.info("Jules activity {} for session {} was already recorded by another poller", activityName, session.getExternalSessionId());
            } catch (Exception e) {
                log.warn("Could not answer Jules agent question activity {} for session {}: {}", activityName, session.getExternalSessionId(), e.getMessage());
            }
        }
    }

    private String extractAgentQuestion(JsonNode activity) {
        if (activity == null || !"agent".equalsIgnoreCase(activity.path("originator").asText(""))) {
            return null;
        }

        String text = null;
        if (activity.has("agentMessaged")) {
            text = findMessageText(activity.get("agentMessaged"));
        }
        if ((text == null || text.isBlank()) && activity.has("progressUpdated")) {
            text = findMessageText(activity.get("progressUpdated"));
        }

        if (text == null || text.isBlank() || !looksLikeQuestion(text)) {
            return null;
        }
        return text.trim();
    }

    private String findMessageText(JsonNode node) {
        String direct = findTextByFieldName(node, Set.of(
                "agentMessage", "message", "text", "content", "body", "markdown", "description", "title"
        ));
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return null;
    }

    private String findTextByFieldName(JsonNode node, Set<String> fieldNames) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (fieldNames.contains(entry.getKey()) && entry.getValue().isTextual()) {
                    String value = entry.getValue().asText();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
            fields = node.fields();
            while (fields.hasNext()) {
                String nested = findTextByFieldName(fields.next().getValue(), fieldNames);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = findTextByFieldName(child, fieldNames);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean looksLikeQuestion(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("?")) {
            return true;
        }
        return lower.contains("should i")
                || lower.contains("should we")
                || lower.contains("do you have")
                || lower.contains("can i")
                || lower.contains("may i")
                || lower.contains("please clarify")
                || lower.contains("need clarification")
                || lower.contains("specific requirements")
                || lower.contains("blocked")
                || lower.contains("уточните")
                || lower.contains("нужно ли")
                || lower.contains("можно ли")
                || lower.contains("какие требования")
                || lower.contains("есть ли требования");
    }

    private long countPreviousSimilarQuestions(List<JulesActivityResponseEntity> responseHistory, String question) {
        String normalized = normalizeQuestionForLoopDetection(question);
        if (normalized.isBlank()) {
            return 0L;
        }
        return responseHistory.stream()
                .filter(record -> normalized.equals(normalizeQuestionForLoopDetection(record.getQuestion())))
                .count();
    }

    private String normalizeQuestionForLoopDetection(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        if (mentionsGeneratedArtifact(lower)) {
            return "generated-artifact-remediation";
        }
        if (lower.contains("specific requirements")
                || lower.contains("business logic verification")
                || lower.contains("do you have any")
                || lower.contains("нужно ли")
                || lower.contains("есть ли")) {
            return "requirements-clarification";
        }
        return lower.length() <= 500 ? lower : lower.substring(0, 500);
    }

    private String buildJulesQuestionAnswer(TaskEntity task, String question, long previousSimilarQuestions) {
        String deterministicAnswer = objectiveJulesResolution(task, question, previousSimilarQuestions);
        if (deterministicAnswer != null && !deterministicAnswer.isBlank()) {
            return deterministicAnswer;
        }

        String fallback = fallbackJulesAnswer(task);
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "unknown-role";
        String projectRepo = task.getProject() != null ? task.getProject().getRepositoryName() : "unknown-repo";
        String payload = task.getPayload() != null ? task.getPayload().toString() : "{}";

        String systemInstruction = "You are Eneik Technical Product Owner and Staff Engineer answering a Google Jules coding agent inside an active coding session.\n"
                + "You are not the dashboard Agile coach. Answer the Jules agent's operational question directly.\n"
                + "Rules:\n"
                + "- Return only the message to send to Jules.\n"
                + "- Do not ask a follow-up question.\n"
                + "- Make a concrete decision from the supplied task facts. If information is missing, choose the safest minimal assumption and tell Jules to document it in the PR.\n"
                + "- If Jules asks whether to proceed, tell Jules to proceed with the task DoD and Acceptance Criteria unless a concrete contradiction exists.\n"
                + "- For QA tasks, keep the test pyramid target, deepen Acceptance Criteria verification, avoid committing generated reports/test-results/playwright-report, and run the relevant checks.\n"
                + "- Keep the answer concise, practical, and in English only, even if Jules' question uses another language.\n\n"
                + "Task id: " + task.getId() + "\n"
                + "Project repository: " + projectRepo + "\n"
                + "Role: " + roleTag + "\n"
                + "Task description:\n" + task.getDescription() + "\n\n"
                + "Task payload:\n" + payload;

        try {
            String aiResponse = mlPredictionServiceClient.chat(question, systemInstruction);
            if (isUsableAiAnswer(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("Gemini answer generation failed for Jules question on task {}: {}", task.getId(), e.getMessage());
        }

        return fallback;
    }

    private String objectiveJulesResolution(TaskEntity task, String question, long previousSimilarQuestions) {
        if (mentionsGeneratedArtifact(question)) {
            return generatedArtifactRemediation(task, previousSimilarQuestions);
        }
        if (previousSimilarQuestions >= DESTRUCTIVE_LOOP_REPEAT_THRESHOLD) {
            return repeatedQuestionCircuitBreaker(task, previousSimilarQuestions);
        }
        return null;
    }

    private boolean mentionsGeneratedArtifact(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("generated/local artifact")
                || lower.contains("generated artifact")
                || lower.contains("local artifact")
                || lower.contains("playwright-report")
                || lower.contains("test-results")
                || lower.contains("node_modules")
                || lower.contains("coverage/")
                || lower.contains(".next/")
                || lower.contains("trace.zip")
                || lower.contains(".webm");
    }

    private String generatedArtifactRemediation(TaskEntity task, long previousSimilarQuestions) {
        String taskId = task != null && task.getId() != null ? task.getId().toString() : "unknown";
        String marker = detectedGeneratedArtifactMarker(task != null ? task.getDescription() : null);
        String loopPrefix = previousSimilarQuestions >= DESTRUCTIVE_LOOP_REPEAT_THRESHOLD
                ? "Circuit breaker: this blocker question has repeated. Stop the discussion loop and execute the remediation exactly.\n\n"
                : "";
        return loopPrefix
                + "Task " + taskId + " is blocked by Git hygiene only: generated/local artifacts are in the PR diff"
                + ("generated/local artifacts".equals(marker) ? "." : " (" + marker + ").") + "\n"
                + "Do not change product scope. Clean the same branch, update .gitignore if needed, run this check, and resubmit:\n"
                + "git diff --name-only origin/main...HEAD | grep -E '(^|/)(playwright-report|test-results|coverage|node_modules|\\.next)/|\\.(trace|webm)$' && exit 1 || true\n"
                + "Acceptance: the command prints no artifact paths and the PR contains only source/config/test/doc changes.";
    }

    private String buildReviewRejectionMessage(TaskEntity task, String remarks) {
        String taskId = task != null && task.getId() != null ? task.getId().toString() : "unknown";
        if (mentionsGeneratedArtifact(remarks)) {
            String marker = detectedGeneratedArtifactMarker(remarks);
            return "PR review for task " + taskId + " is blocked by repository hygiene, not by product requirements.\n"
                    + "Detected generated/local artifact in the diff: " + marker + ".\n"
                    + "Fix only this blocker on the same PR branch: untrack generated artifact folders, add missing .gitignore entries, then verify:\n"
                    + "git diff --name-only origin/main...HEAD | grep -E '(^|/)(playwright-report|test-results|coverage|node_modules|\\.next)/|\\.(trace|webm)$' && exit 1 || true\n"
                    + "Stop after cleanup. Do not add new feature, design, architecture, or test-expansion work.";
        }
        return truncate("PR review for task " + taskId + " is blocked. Fix only the specific review blocker below, update the same PR branch, and resubmit. Do not expand product scope.\n\n"
                + remarks, 1_600);
    }

    private void recordSystemReviewRejection(JulesSessionEntity session, String reviewSignal, String response, boolean sent) {
        String normalized = normalizeQuestionForLoopDetection(reviewSignal);
        String activityHash = sha256(REVIEW_REJECTION_ACTIVITY_NAME + "\n" + normalized);
        Optional<JulesActivityResponseEntity> existing =
                julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(session.getId(), activityHash);
        if (existing.isPresent()) {
            return;
        }
        JulesActivityResponseEntity record = new JulesActivityResponseEntity();
        record.setJulesSessionId(session.getId());
        record.setActivityName(REVIEW_REJECTION_ACTIVITY_NAME);
        record.setActivityHash(activityHash);
        record.setQuestion(reviewSignal);
        record.setResponse(response);
        record.setSent(sent);
        record.setRespondedAt(sent ? Instant.now() : null);
        try {
            julesActivityResponseRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.info("PR review rejection for session {} was already recorded by another poller", session.getExternalSessionId());
        }
    }

    private String detectedGeneratedArtifactMarker(String text) {
        if (text == null) {
            return "generated/local artifacts";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : List.of(
                "playwright-report/",
                "test-results/",
                "coverage/",
                "node_modules/",
                ".next/",
                ".last-run.json",
                ".env",
                ".zip",
                ".png",
                ".webm",
                ".trace",
                "trace.zip")) {
            if (lower.contains(marker)) {
                return marker;
            }
        }
        return "generated/local artifacts";
    }

    private String repeatedQuestionCircuitBreaker(TaskEntity task, long previousSimilarQuestions) {
        String taskId = task != null && task.getId() != null ? task.getId().toString() : "unknown";
        return "Circuit breaker for task " + taskId + ": this is the same blocker/clarification loop for the "
                + (previousSimilarQuestions + 1)
                + "th time. Do not ask another open-ended question. Make one objective move from the task facts: if the latest review contains a concrete blocker, fix exactly that blocker and verify it with commands; otherwise proceed from the Acceptance Criteria and DoD, document the smallest safe assumption in the PR summary, and resubmit. If a fact is truly unverifiable after one attempt, mark the PR summary with BLOCKED and list the exact missing fact.";
    }

    private boolean shouldCloseLoop(long previousResponses, long previousSimilarQuestions) {
        return previousResponses >= maxAgentDialogResponses
                || previousSimilarQuestions + 1 >= loopCloseSimilarThreshold;
    }

    private String closeReason(long previousResponses, long previousSimilarQuestions, String question) {
        if (previousResponses >= maxAgentDialogResponses) {
            return "dialog_limit_exceeded: " + previousResponses + " prior orchestrator replies; max is " + maxAgentDialogResponses;
        }
        if (previousSimilarQuestions + 1 >= loopCloseSimilarThreshold) {
            return "repeated_blocker_loop: normalized blocker repeated " + (previousSimilarQuestions + 1) + " times";
        }
        if (mentionsGeneratedArtifact(question)) {
            return "repository_hygiene_loop: generated/local artifacts remain in the PR diff";
        }
        return "destructive_dialog_loop";
    }

    private boolean isTerminalLocallyClosed(JulesSessionEntity session) {
        return "loop_closed".equals(session.getStatus())
                || "failed".equals(session.getStatus())
                || "closed".equals(session.getStatus());
    }

    private void closeLoopAndCreateFollowUps(JulesSessionEntity session,
                                             TaskEntity task,
                                             String latestQuestion,
                                             List<JulesActivityResponseEntity> responseHistory,
                                             String closeReason) {
        LoopDiagnosis diagnosis = diagnoseLoop(task, latestQuestion, closeReason);
        String geminiAnalysis = geminiLoopAnalysis(task, latestQuestion, responseHistory, diagnosis, closeReason);

        session.setStatus("loop_closed");
        session.setClosedAt(Instant.now());
        session.setClosureReason(closeReason + "\n\n" + diagnosis.toText() + "\n\nGemini analysis:\n" + geminiAnalysis);
        julesSessionRepository.save(session);

        claimService.closeTaskAsBlocked(task.getId(), "Jules circuit breaker: " + closeReason);
        createCircuitBreakerWishlist(session, task, latestQuestion, diagnosis, geminiAnalysis, closeReason);
        saveJulesDialogueLog(task.getId(), session.getExternalSessionId(),
                diagnosis.toText() + "\n\nGemini analysis:\n" + geminiAnalysis,
                "Jules loop closed by Eneik circuit breaker: " + closeReason);
        log.warn("Closed Jules session {} for task {} due to {}. Follow-up wishlist generated.",
                session.getExternalSessionId(), task.getId(), closeReason);
    }

    private LoopDiagnosis diagnoseLoop(TaskEntity task, String latestQuestion, String closeReason) {
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "unknown-role";
        if (mentionsGeneratedArtifact(latestQuestion) || closeReason.contains("repository_hygiene")) {
            return new LoopDiagnosis(
                    "Repository hygiene blocker repeated; Jules kept committing generated/local artifacts instead of producing a clean PR diff.",
                    "Must-Be",
                    "clear",
                    roleTag,
                    "Clean generated artifacts from the PR branch only",
                    generatedArtifactFollowUp(task)
            );
        }
        if (closeReason.contains("activity_log_overflow")) {
            return new LoopDiagnosis(
                    "The Jules activity log exceeded the backend safety limit; the session became too noisy to inspect reliably and must not receive more prompts.",
                    "Must-Be",
                    "complex",
                    roleTag,
                    "Restart the work from the smallest observable implementation slice",
                    activityOverflowFollowUp(task, latestQuestion)
            );
        }
        if (closeReason.contains("dialog_limit")) {
            return new LoopDiagnosis(
                    "The session exceeded the safe dialogue budget for a weak coding model; the original task is too broad or ambiguous for one Jules branch.",
                    "Must-Be",
                    "complicated",
                    roleTag,
                    "Split the blocked task into one atomic implementation slice",
                    atomicSliceFollowUp(task, latestQuestion)
            );
        }
        if (closeReason.contains("stuck_session_timeout")) {
            return new LoopDiagnosis(
                    "The Jules session stayed stuck after recovery time; continuing the same external session would keep capacity blocked without objective progress.",
                    "Must-Be",
                    "complicated",
                    roleTag,
                    "Restart the blocked work as a fresh atomic session",
                    atomicSliceFollowUp(task, latestQuestion)
            );
        }
        if (closeReason.contains("active_session_age_limit")) {
            return new LoopDiagnosis(
                    "The Jules session exceeded the maximum safe age for a weak coding-model branch; poll updates were masking lack of production progress.",
                    "Must-Be",
                    "complicated",
                    roleTag,
                    "Restart the work as a fresh short session",
                    atomicSliceFollowUp(task, latestQuestion)
            );
        }
        return new LoopDiagnosis(
                "The same blocker repeated and the session stopped making objective progress.",
                "Must-Be",
                "complicated",
                roleTag,
                "Resolve the repeated blocker as a new short Jules session",
                repeatedBlockerFollowUp(task, latestQuestion)
        );
    }

    private String geminiLoopAnalysis(TaskEntity task,
                                      String latestQuestion,
                                      List<JulesActivityResponseEntity> responseHistory,
                                      LoopDiagnosis diagnosis,
                                      String closeReason) {
        String transcript = responseHistory.stream()
                .limit(8)
                .map(record -> "QUESTION: " + truncate(record.getQuestion(), 900) + "\nANSWER: " + truncate(record.getResponse(), 900))
                .reduce("", (a, b) -> a + "\n---\n" + b);
        String systemInstruction = """
                You are Gemini acting as Eneik incident analyst for a failed Jules coding session.
                Use Eneik Management System, Kano, and Cynefin.
                Return concise factual analysis only. Do not suggest continuing the same Jules session.
                Required fields:
                - Root cause
                - Kano classification
                - Cynefin domain
                - New short-session recommendation
                - Definition of Done
                """;
        String prompt = "Task id: " + task.getId() + "\n"
                + "Role: " + diagnosis.roleTag() + "\n"
                + "Close reason: " + closeReason + "\n"
                + "Deterministic Kano: " + diagnosis.kanoClass() + "\n"
                + "Deterministic Cynefin: " + diagnosis.cynefinDomain() + "\n"
                + "Task description:\n" + truncate(task.getDescription(), 2_000) + "\n\n"
                + "Latest blocker/question:\n" + truncate(latestQuestion, 1_500) + "\n\n"
                + "Recent dialogue evidence:\n" + transcript;
        try {
            String response = mlPredictionServiceClient.chat(prompt, systemInstruction);
            if (isUsableAiAnswer(response)) {
                return truncate(response.trim(), 2_400);
            }
        } catch (Exception e) {
            log.warn("Gemini loop analysis failed for Jules session task {}: {}", task.getId(), e.getMessage());
        }
        return "Root cause: " + diagnosis.rootCause()
                + "\nKano classification: " + diagnosis.kanoClass()
                + "\nCynefin domain: " + diagnosis.cynefinDomain()
                + "\nNew short-session recommendation: " + diagnosis.followUpTitle()
                + "\nDefinition of Done: " + firstLine(diagnosis.followUpBody());
    }

    private void createCircuitBreakerWishlist(JulesSessionEntity session,
                                              TaskEntity task,
                                              String latestQuestion,
                                              LoopDiagnosis diagnosis,
                                              String geminiAnalysis,
                                              String closeReason) {
        if (task.getProject() == null) {
            return;
        }
        UUID projectId = task.getProject().getId();
        String marker = "Circuit breaker source session: " + session.getId();
        boolean alreadyExists = wishlistRepository.findByProjectId(projectId).stream()
                .map(WishlistEntity::getContent)
                .anyMatch(content -> content != null && content.contains(marker));
        if (alreadyExists) {
            return;
        }

        WishlistEntity followUp = new WishlistEntity();
        followUp.setProjectId(projectId);
        followUp.setSource(WishlistSource.role_mismatch_followup);
        followUp.setSourceRoleTag(diagnosis.roleTag());
        followUp.setStatus(WishlistStatus.pending);
        followUp.setContent(truncate("""
                [Auto follow-up from Jules circuit breaker]
                Circuit breaker source session: %s
                External Jules session: %s
                Original task: %s
                Closure reason: %s

                Gemini/Kano/Cynefin analysis:
                %s

                Eneik classification:
                - Kano: %s
                - Cynefin: %s
                - Root cause: %s

                New short Jules session:
                %s

                Scope rule:
                - One branch, one atomic result, no broad redesign.
                - If more work is discovered, stop after the smallest verified slice and write the remaining work as another wishlist item.
                - Dialogue budget: no more than 8 orchestrator replies; repeated blocker means close and re-plan.

                Latest blocker evidence:
                %s
                """.formatted(
                session.getId(),
                valueOrUnset(session.getExternalSessionId()),
                task.getId(),
                closeReason,
                geminiAnalysis,
                diagnosis.kanoClass(),
                diagnosis.cynefinDomain(),
                diagnosis.rootCause(),
                diagnosis.followUpBody(),
                truncate(latestQuestion, 1_500)
        ), FOLLOW_UP_CONTENT_MAX_LENGTH));
        wishlistRepository.save(followUp);
    }

    private String generatedArtifactFollowUp(TaskEntity task) {
        return "Goal: clean the existing PR branch so it contains zero generated/local artifacts.\n"
                + "Do only repository hygiene, not product feature work.\n"
                + "Required commands:\n"
                + "1. git rm -r --cached --ignore-unmatch playwright-report test-results coverage node_modules .next apps/web/playwright-report apps/web/test-results apps/web/coverage apps/web/.next\n"
                + "2. Ensure .gitignore contains **/playwright-report/, **/test-results/, **/coverage/, **/.next/, node_modules/, *.trace, *.webm.\n"
                + "3. Verify: git diff --name-only origin/main...HEAD | grep -E '(^|/)(playwright-report|test-results|coverage|node_modules|\\.next)/|\\.(trace|webm)$' && exit 1 || true\n"
                + "DoD: the verification command prints no artifact paths, the PR contains only source/config/test/doc changes, and no new product scope is added.";
    }

    private String activityOverflowFollowUp(TaskEntity task, String latestQuestion) {
        return "Goal: replace the unbounded Jules session with one observable implementation slice.\n"
                + "First action: inspect the open PR/branch state, summarize what is actually present, and choose exactly one fix or one component to finish.\n"
                + "Scope rule: no broad rewrite, no multi-feature platform work, no additional architecture documents unless they are required to make one code change.\n"
                + "DoD: one small branch, one PR, at most two source areas, explicit verification command, and a concise handoff note.\n"
                + "Original task summary: " + truncate(task.getDescription(), 1_200) + "\n"
                + "Latest loop signal: " + truncate(latestQuestion, 800);
    }

    private String atomicSliceFollowUp(TaskEntity task, String latestQuestion) {
        return "Goal: re-plan the blocked task into one atomic Jules implementation slice.\n"
                + "Use the original task only as context; choose the smallest independently verifiable service/component/fix.\n"
                + "DoD: one small branch, one PR, at most two tightly related source areas, explicit verification command, no generated artifacts.\n"
                + "Original task summary: " + truncate(task.getDescription(), 1_200) + "\n"
                + "Latest loop signal: " + truncate(latestQuestion, 800);
    }

    private String repeatedBlockerFollowUp(TaskEntity task, String latestQuestion) {
        return "Goal: resolve only the repeated blocker from the failed Jules session.\n"
                + "Do not continue the old branch conversation. Start a fresh short session with the blocker as the sole acceptance criterion.\n"
                + "DoD: blocker is objectively gone, verification command is recorded, and any remaining feature work is written as a separate wishlist item.\n"
                + "Repeated blocker: " + truncate(latestQuestion, 1_200);
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] lines = value.strip().split("\\R", 2);
        return lines.length == 0 ? value.strip() : lines[0];
    }

    private String valueOrUnset(String value) {
        return value == null || value.isBlank() ? "<unset>" : value;
    }

    private boolean isUsableAiAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        return !lower.contains("api error")
                && !lower.contains("assistant temporarily")
                && !lower.contains("temporarily unavailable")
                && !lower.contains("произошла ошибка")
                && !lower.contains("ассистент временно")
                && !lower.contains("рђсс")
                && !lower.contains("рѕс€");
    }

    private String fallbackJulesAnswer(TaskEntity task) {
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "";
        if ("BARCAN-TAG-06".equals(roleTag)) {
            return "Proceed with reaching the required test ratios and deepening Acceptance Criteria verification as planned. Use the existing task AC and DoD as the source of truth; do not wait for extra business-logic requirements unless you find a concrete contradiction. For ambiguous details, implement the smallest verifiable assumption and document it in the PR summary. Keep generated Playwright reports, trace zips, screenshots, and test-results out of the commit, and run the relevant unit, integration, and E2E checks before submitting.";
        }
        return "Proceed using the existing task description, Acceptance Criteria, and DoD as the source of truth. Choose the smallest safe implementation assumption where details are ambiguous, document that assumption in the PR summary, and continue unless you hit a concrete contradiction or security/data-loss risk. Keep generated local artifacts out of the commit and run the relevant verification checks before submitting.";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Transactional
    public void handlePrOpenedWorkflow(JulesSessionEntity session) {
        UUID taskId = session.getTaskId();
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.claimed) {
                log.info("Jules session {} transitioned to pr_opened. Completing implementer phase for task {}.", session.getId(), taskId);
                if (claimService.hasActiveClaim(task.getId())) {
                    claimService.complete(task.getId());
                } else {
                    log.info("No active implementer claim for task {}; continuing PR review workflow", task.getId());
                }

                // Create PR Review entry using real PR URL if available, otherwise fallback to mock
                String prUrl = session.getPrUrl();
                if (prUrl == null || prUrl.isBlank()) {
                    prUrl = "https://github.com/" + task.getProject().getRepositoryName() + "/pull/mock-" + taskId;
                }
                
                com.eneik.production.dto.monitor.PrDataDto prData = new com.eneik.production.dto.monitor.PrDataDto();
                prData.setCiStatus("success");
                prData.setLinesChanged(120);
                prData.setFilesChanged(4);
                prData.setChangedFiles(java.util.Collections.emptyList());

                // Invoke the local agent code review (Antigravity Reviewer)
                Map<String, Object> reviewResult = mlPredictionServiceClient.reviewPr(task.getProject().getId(), task.getId(), prUrl);
                boolean approved = Boolean.TRUE.equals(reviewResult.get("approved"));
                String remarks = (String) reviewResult.get("remarks");
                if (remarks == null || remarks.isBlank()) {
                    remarks = "PR review rejected without detailed remarks.";
                }

                if (approved) {
                    prData.setDiffSummary("CORE ARCHITECTURE VERIFIED. APPROVED. " + remarks);
                    prReviewPipelineService.onPrOpened(prUrl, session.getId(), prData);

                    // Move task to review stage so AutoMergeService can merge it
                    task.setStatus(com.eneik.production.models.persistence.TaskStatus.review);
                    taskRepository.save(task);
                    log.info("Local agent review passed for task {}. PR approved and task moved to REVIEW status.", task.getId());

                    // Create new recommended tasks proposed by the review
                    List<Map<String, Object>> newTasks = (List<Map<String, Object>>) reviewResult.get("newTasks");
                    if (newTasks != null) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        for (Map<String, Object> nt : newTasks) {
                            String rTag = (String) nt.get("roleTag");
                            String desc = (String) nt.get("description");
                            roleRepository.findById(rTag).ifPresent(role -> {
                                TaskEntity t = new TaskEntity();
                                t.setProject(task.getProject());
                                t.setRole(role);
                                t.setDescription(desc);
                                t.setStatus(com.eneik.production.models.persistence.TaskStatus.queued);
                                
                                com.fasterxml.jackson.databind.node.ObjectNode payloadNode = mapper.createObjectNode();
                                payloadNode.put("technicalLeadTaskSpec", desc);
                                t.setPayload(payloadNode);
                                
                                taskRepository.save(t);
                                log.info("Created new local agent review recommended task for role {}: {}", rTag, desc);
                            });
                        }
                    }
                } else {
                    prData.setDiffSummary("REVIEW REJECTED. " + remarks);
                    prReviewPipelineService.onPrOpened(prUrl, session.getId(), prData);

                    List<JulesActivityResponseEntity> responseHistory =
                            julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
                    String reviewSignal = "PR review rejection: " + remarks;
                    long previousSimilarReviewRejections = countPreviousSimilarQuestions(responseHistory, reviewSignal);
                    String julesReviewMessage = buildReviewRejectionMessage(task, remarks);
                    recordSystemReviewRejection(session, reviewSignal, julesReviewMessage, false);

                    if (previousSimilarReviewRejections > 0) {
                        String closeReason = "repository_hygiene_review_repeated: same PR blocker persisted after prior remediation";
                        closeLoopAndCreateFollowUps(session, task, reviewSignal, responseHistory, closeReason);
                        return;
                    }

                    task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);
                    taskRepository.save(task);

                    session.setStatus("revising");
                    julesSessionRepository.save(session);

                    log.info("Review rejected. Transitioning session {} to revising for task {}", session.getExternalSessionId(), task.getId());
                    saveJulesDialogueLog(task.getId(), session.getExternalSessionId(), julesReviewMessage, "System generated rejection");

                    // Decouple the HTTP call to prevent holding the DB transaction
                    String externalSessionId = session.getExternalSessionId();
                    String sessionApiKey = apiKeyForSession(session);
                    UUID finalTaskId = task.getId();
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        boolean sent = sessionApiKey != null
                                ? julesApiClient.sendMessage(externalSessionId, julesReviewMessage, sessionApiKey)
                                : julesApiClient.sendMessage(externalSessionId, julesReviewMessage);
                        if (sent) {
                            log.info("Successfully sent review rejection message asynchronously to Jules session {} for task {}", externalSessionId, finalTaskId);
                        } else {
                            log.warn("Failed to send async message to Jules session {} for task {}. Task might be stuck in revising.", externalSessionId, finalTaskId);
                        }
                    });
                }
            } else if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.review) {
                log.info("Jules reviewer session {} transitioned to pr_opened. Completing reviewer phase for task {}.", session.getId(), taskId);
                if (claimService.hasActiveClaim(task.getId())) {
                    claimService.complete(task.getId());
                    log.info("Task {} marked as review completed", taskId);
                } else {
                    log.info("No active reviewer claim for task {}; leaving task status unchanged", task.getId());
                }
            }
        }
    }

    /**
     * Mapping Table:
     * External (Jules API) -> Internal
     * -------------------------------
     * "QUEUED"             -> "queued"
     * "RUNNING"            -> "running"
     * "SUCCEEDED"          -> "pr_opened"
     * "FAILED"             -> "failed"
     * "CANCELLED"          -> "failed"
     * "STUCK"              -> "stuck" (if API ever returns it)
     */
    public String mapExternalStatus(String externalStatus) {
        if (externalStatus == null) return "running";

        return switch (externalStatus.toUpperCase()) {
            case "QUEUED" -> "queued";
            case "RUNNING" -> "running";
            case "SUCCEEDED" -> "pr_opened";
            case "FAILED", "CANCELLED" -> "failed";
            case "STUCK" -> "stuck";
            default -> "running"; // Default to running if unknown but alive
        };
    }

    private String apiKeyForSession(JulesSessionEntity session) {
        if (session.getAccountId() == null) {
            return null;
        }
        return accountRepository.findById(session.getAccountId())
                .map(com.eneik.production.models.persistence.AccountEntity::getApiKey)
                .filter(key -> !key.isBlank())
                .orElse(null);
    }

    private boolean shouldSendStuckRecovery(JulesSessionEntity session) {
        Instant lastCheck = session.getLastStatusCheckAt();
        return lastCheck == null || lastCheck.isBefore(Instant.now().minus(STUCK_RECOVERY_MESSAGE_INTERVAL));
    }

    private void sendStuckRecoveryMessageAsync(JulesSessionEntity session, TaskEntity task, String apiKey) {
        String externalSessionId = session.getExternalSessionId();
        UUID taskId = task.getId();
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "unknown-role";
        String taskDescription = task.getDescription();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String fallbackPrompt = "Eneik orchestrator recovery: continue this task if possible, or open a PR with the current progress. "
                    + "If you are blocked, explain the blocker in the session. Task role: " + roleTag
                    + ". Task: " + taskDescription;
            String prompt = fallbackPrompt;

            try {
                String aiPrompt = "Create a concise direct recovery message for a Google Jules coding session that is stuck. "
                        + "Role: " + roleTag + "\nTask: " + taskDescription;
                String aiSystem = "Return only the English-language message to send to Jules. Do not include analysis or markdown.";
                String aiResponse = mlPredictionServiceClient.chat(aiPrompt, aiSystem);
                if (aiResponse != null
                        && !aiResponse.isBlank()
                        && !aiResponse.contains("API Error")
                        && !aiResponse.contains("Произошла ошибка")
                        && !aiResponse.contains("Ассистент временно недоступен")) {
                    prompt = aiResponse;
                }
            } catch (Exception e) {
                log.warn("Could not generate Gemini recovery prompt for Jules session {}: {}", externalSessionId, e.getMessage());
            }

            boolean sent = apiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, prompt, apiKey)
                    : julesApiClient.sendMessage(externalSessionId, prompt);
            if (sent) {
                log.info("Sent stuck-session recovery message to Jules session {} for task {}", externalSessionId, taskId);
                saveJulesDialogueLog(taskId, externalSessionId, prompt, "Stuck-session recovery");
            } else {
                log.warn("Failed to send stuck-session recovery message to Jules session {} for task {}", externalSessionId, taskId);
            }
        });
    }

    private void saveJulesDialogueLog(UUID taskId, String sessionId, String prompt, String remarks) {
        try {
            java.nio.file.Path dirPath = java.nio.file.Paths.get("docs/jules_dialogues");
            if (!java.nio.file.Files.exists(dirPath)) {
                java.nio.file.Files.createDirectories(dirPath);
            }
            java.nio.file.Path filePath = dirPath.resolve("task_" + taskId + ".log");
            String logEntry = String.format("--- Session: %s at %s ---\nRemarks: %s\nPrompt Sent: %s\n\n",
                                            sessionId, Instant.now().toString(), remarks, prompt);
            java.nio.file.Files.writeString(filePath, logEntry, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to save Jules dialogue log for task {}: {}", taskId, e.getMessage());
        }
    }

    private record LoopDiagnosis(
            String rootCause,
            String kanoClass,
            String cynefinDomain,
            String roleTag,
            String followUpTitle,
            String followUpBody
    ) {
        String toText() {
            return "Root cause: " + rootCause + "\n"
                    + "Kano: " + kanoClass + "\n"
                    + "Cynefin: " + cynefinDomain + "\n"
                    + "Role: " + roleTag + "\n"
                    + "Follow-up: " + followUpTitle + "\n"
                    + followUpBody;
        }
    }

    /**
     * Trigger for periodic maintenance of stuck Jules sessions.
     */
    @Scheduled(fixedRateString = "${jules.detect-stuck-rate-ms:60000}")
    public void detectStuck() {
        runSessionSafetyMaintenance();
    }

    public void runSessionSafetyMaintenance() {
        claimService.detectStuckSessions(stuckThresholdMinutes);
        closeOverdueStuckSessions();
        closeOverdueActiveSessions();
    }

    @Transactional
    public void closeOverdueStuckSessions() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(stuckCloseThresholdMinutes));
        List<JulesSessionEntity> stuckSessions = julesSessionRepository.findByStatus("stuck");
        if (stuckSessions == null || stuckSessions.isEmpty()) {
            return;
        }
        int closed = 0;
        for (JulesSessionEntity session : stuckSessions) {
            if (closed >= maxLoopClosuresPerRun) {
                break;
            }
            if (session.getUpdatedAt() == null || session.getUpdatedAt().isAfter(threshold)) {
                continue;
            }
            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null) {
                session.setStatus("loop_closed");
                session.setClosedAt(Instant.now());
                session.setClosureReason("stuck_session_timeout: task no longer exists");
                julesSessionRepository.save(session);
                continue;
            }
            List<JulesActivityResponseEntity> responseHistory =
                    julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
            String latestQuestion = responseHistory.stream()
                    .findFirst()
                    .map(JulesActivityResponseEntity::getQuestion)
                    .filter(question -> question != null && !question.isBlank())
                    .orElse("Jules session stayed stuck without new actionable activity.");
            closeLoopAndCreateFollowUps(
                    session,
                    task,
                    latestQuestion,
                    responseHistory,
                    "stuck_session_timeout: stuck for at least " + stuckCloseThresholdMinutes + " minutes"
            );
            closed++;
        }
    }

    @Transactional
    public void closeOverdueActiveSessions() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(maxActiveSessionMinutes));
        List<JulesSessionEntity> sessions = julesSessionRepository.findAll();
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        int closed = 0;
        for (JulesSessionEntity session : sessions) {
            if (closed >= maxLoopClosuresPerRun) {
                break;
            }
            if (!isAgeLimitedActiveStatus(session.getStatus())) {
                continue;
            }
            if (session.getCreatedAt() == null || session.getCreatedAt().isAfter(threshold)) {
                continue;
            }
            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null) {
                session.setStatus("loop_closed");
                session.setClosedAt(Instant.now());
                session.setClosureReason("active_session_age_limit: task no longer exists");
                julesSessionRepository.save(session);
                continue;
            }
            List<JulesActivityResponseEntity> responseHistory =
                    julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
            String latestQuestion = responseHistory.stream()
                    .findFirst()
                    .map(JulesActivityResponseEntity::getQuestion)
                    .filter(question -> question != null && !question.isBlank())
                    .orElse("Jules session exceeded the maximum safe active duration without completing the task.");
            closeLoopAndCreateFollowUps(
                    session,
                    task,
                    latestQuestion,
                    responseHistory,
                    "active_session_age_limit: active for at least " + maxActiveSessionMinutes + " minutes"
            );
            closed++;
        }
    }

    private boolean isAgeLimitedActiveStatus(String status) {
        return "queued".equals(status)
                || "running".equals(status)
                || "revising".equals(status)
                || "stuck".equals(status);
    }
}
