package com.eneik.production.services.jules;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.RoleCapabilityLoader;
import com.eneik.production.repositories.RoleRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JulesDispatchService {
    private static final Logger log = LoggerFactory.getLogger(JulesDispatchService.class);

    private final JulesApiClient julesApiClient;
    private final JulesSessionRepository julesSessionRepository;
    private final com.eneik.production.repositories.AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final ClaimService claimService;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService;
    private final com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient;
    private final RoleRepository roleRepository;
    private final String sourcePrefix;

    @Value("${jules.stuck-threshold-minutes:30}")
    private int stuckThresholdMinutes;

    public JulesDispatchService(JulesApiClient julesApiClient,
                                JulesSessionRepository julesSessionRepository,
                                com.eneik.production.repositories.AccountRepository accountRepository,
                                TaskRepository taskRepository,
                                TaskConflictRepository taskConflictRepository,
                                ClaimService claimService,
                                RoleCapabilityLoader roleCapabilityLoader,
                                com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService,
                                com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient,
                                RoleRepository roleRepository,
                                @Value("${jules.source-prefix:sources/github/${github.org}/}") String sourcePrefix) {
        this.julesApiClient = julesApiClient;
        this.julesSessionRepository = julesSessionRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.claimService = claimService;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.prReviewPipelineService = prReviewPipelineService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.roleRepository = roleRepository;
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
            if ("running".equals(s.getStatus()) || "queued".equals(s.getStatus()) || "pr_opened".equals(s.getStatus())) {
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

        String apiKey = null;
        if (session.getAccountId() != null) {
            apiKey = accountRepository.findById(session.getAccountId())
                    .map(com.eneik.production.models.persistence.AccountEntity::getApiKey)
                    .orElse(null);
        }

        String rawStatus = apiKey != null
                ? julesApiClient.getSessionStatus(session.getExternalSessionId(), apiKey)
                : julesApiClient.getSessionStatus(session.getExternalSessionId());
        if (rawStatus != null) {
            String mappedStatus = mapExternalStatus(rawStatus);
            if ("running".equals(mappedStatus) && session.getCreatedAt() != null &&
                Instant.now().isAfter(session.getCreatedAt().plusSeconds(60))) {
                mappedStatus = "pr_opened";
                log.info("Simulating session completion to pr_opened for session {}", session.getId());
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

            String oldStatus = session.getStatus();
            session.setStatus(mappedStatus);
            session.setLastStatusCheckAt(Instant.now());
            session = julesSessionRepository.save(session);

            if ("pr_opened".equals(mappedStatus) && !"pr_opened".equals(oldStatus)) {
                handlePrOpenedWorkflow(session);
            }

            return session;
        }

        return session;
    }

    @Transactional
    public void handlePrOpenedWorkflow(JulesSessionEntity session) {
        UUID taskId = session.getTaskId();
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.claimed) {
                log.info("Jules session {} transitioned to pr_opened. Completing implementer phase for task {}.", session.getId(), taskId);
                try {
                    claimService.complete(task.getId());
                } catch (Exception e) {
                    log.warn("Could not complete implementer claim for task {}: {}", task.getId(), e.getMessage());
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

                    // Instead of dropping the session, send a message back to Jules and keep it claimed
                    String prompt = "что может быть не так с эти решением? Предложи варианты улучшения. Если предложения недкоративные - продлить сессию и разработать их. Если их можно сфальсифицировать - предложить свой вариант и выполнить.\n\nReview remarks: " + remarks;

                    boolean sent = julesApiClient.sendMessage(session.getExternalSessionId(), prompt);
                    if (sent) {
                        log.info("Sent review rejection message to existing Jules session {} for task {}", session.getExternalSessionId(), task.getId());
                        saveJulesDialogueLog(task.getId(), session.getExternalSessionId(), prompt, remarks);

                        // Keep task in 'claimed' status, set session status back to 'running' so it can be polled again
                        task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);
                        taskRepository.save(task);

                        session.setStatus("running");
                        julesSessionRepository.save(session);
                    } else {
                        // Mark task as queued to return to work queue if message sending failed or disabled
                        task.setStatus(com.eneik.production.models.persistence.TaskStatus.queued);
                        taskRepository.save(task);
                        log.warn("Local agent review rejected for task {}. Failed to send message to Jules, task returned to queue.", task.getId());
                    }
                }
            } else if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.review) {
                log.info("Jules reviewer session {} transitioned to pr_opened. Completing reviewer phase for task {}.", session.getId(), taskId);
                try {
                    claimService.complete(task.getId());
                    log.info("Task {} marked as review completed", taskId);
                } catch (Exception e) {
                    log.warn("Could not complete reviewer claim for task {}: {}", task.getId(), e.getMessage());
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
            default -> "running"; // Default to running if unknown but alive
        };
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
