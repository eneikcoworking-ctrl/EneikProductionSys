package com.eneik.production.services.jules;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.JulesActivityResponseEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.TaskConflictRepository;
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

    private final JulesApiClient julesApiClient;
    private final JulesSessionRepository julesSessionRepository;
    private final JulesActivityResponseRepository julesActivityResponseRepository;
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

    public JulesDispatchService(JulesApiClient julesApiClient,
                                JulesSessionRepository julesSessionRepository,
                                JulesActivityResponseRepository julesActivityResponseRepository,
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
        if ("BARCAN-TAG-06".equals(task.getRole().getTag())) {
            roleContextBuilder.append("- QA default: if you ask whether to continue verification, continue with required test ratios and deeper AC verification; document assumptions instead of waiting.\n");
        }

        try {
            String rulesPathStr = task.getRole().getRulesPath();
            if (rulesPathStr != null) {
                java.nio.file.Path rulesPath = java.nio.file.Paths.get(rulesPathStr);
                if (java.nio.file.Files.exists(rulesPath)) {
                    String fullMarkdown = java.nio.file.Files.readString(rulesPath);
                    roleContextBuilder.append("\n## FULL ROLE CHARTER AND EXCELLENCE GUIDELINES:\n")
                                      .append(fullMarkdown).append("\n");
                }
            }

            RoleRules rules = roleCapabilityLoader.loadRules(task.getRole().getTag());
            if (rules != null) {
                if (rules.scope() != null && !rules.scope().isBlank()) {
                    roleContextBuilder.append("\n## Scope & Priorities\n").append(rules.scope()).append("\n");
                }
                if (rules.forbidden() != null && !rules.forbidden().isEmpty()) {
                    roleContextBuilder.append("\n## Forbidden (Запрещено)\n");
                    for (String f : rules.forbidden()) {
                        roleContextBuilder.append("- ").append(f).append("\n");
                    }
                }
                if (rules.refusalCriteria() != null && !rules.refusalCriteria().isBlank()) {
                    roleContextBuilder.append("\n").append(rules.refusalCriteria()).append("\n");
                }
                if (rules.deonticStatus() != null && !rules.deonticStatus().isBlank()) {
                    roleContextBuilder.append("\n").append(rules.deonticStatus()).append("\n");
                }
                if (rules.outputFormat() != null && !rules.outputFormat().isBlank()) {
                    roleContextBuilder.append("\n## Output Format / Definition of Done\n").append(rules.outputFormat()).append("\n");
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

            if (taskForSession != null && !"pr_opened".equals(mappedStatus)) {
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

        JsonNode root = julesApiClient.getSessionActivities(session.getExternalSessionId(), apiKey);
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
                String answer = buildJulesQuestionAnswer(task, question);
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

    private String buildJulesQuestionAnswer(TaskEntity task, String question) {
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
                + "- Keep the answer concise, practical, and in the same language as Jules' question.\n\n"
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

                    // The prompt is now dynamically generated by the ML service (in 'remarks').
                    // Keep task in 'claimed' status, set session status to 'revising' to avoid polling loops.
                    task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);
                    taskRepository.save(task);

                    session.setStatus("revising");
                    julesSessionRepository.save(session);

                    log.info("Review rejected. Transitioning session {} to revising for task {}", session.getExternalSessionId(), task.getId());
                    saveJulesDialogueLog(task.getId(), session.getExternalSessionId(), remarks, "System generated rejection");

                    // Decouple the HTTP call to prevent holding the DB transaction
                    String externalSessionId = session.getExternalSessionId();
                    String sessionApiKey = apiKeyForSession(session);
                    UUID finalTaskId = task.getId();
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        boolean sent = sessionApiKey != null
                                ? julesApiClient.sendMessage(externalSessionId, remarks, sessionApiKey)
                                : julesApiClient.sendMessage(externalSessionId, remarks);
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
                String aiSystem = "Return only the message to send to Jules. Do not include analysis or markdown.";
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

    /**
     * Trigger for periodic maintenance of stuck Jules sessions.
     */
    @Scheduled(fixedRateString = "${jules.detect-stuck-rate-ms:60000}")
    public void detectStuck() {
        claimService.detectStuckSessions(stuckThresholdMinutes);
    }
}
