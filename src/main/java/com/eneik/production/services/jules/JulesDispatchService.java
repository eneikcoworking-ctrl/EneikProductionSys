package com.eneik.production.services.jules;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.RoleCapabilityLoader;
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
    private final ClaimService claimService;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final String sourcePrefix;

    @Value("${jules.stuck-threshold-minutes:30}")
    private int stuckThresholdMinutes;

    public JulesDispatchService(JulesApiClient julesApiClient,
                                JulesSessionRepository julesSessionRepository,
                                com.eneik.production.repositories.AccountRepository accountRepository,
                                TaskRepository taskRepository,
                                ClaimService claimService,
                                RoleCapabilityLoader roleCapabilityLoader,
                                @Value("${jules.source-prefix:sources/github/${github.org}/}") String sourcePrefix) {
        this.julesApiClient = julesApiClient;
        this.julesSessionRepository = julesSessionRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.claimService = claimService;
        this.roleCapabilityLoader = roleCapabilityLoader;
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
        if ("REVIEWER".equalsIgnoreCase(mode)) {
            description = "[REVIEWER MODE]\nAudit the following code changes against docs/AI_REVIEW_GUIDELINES.md.\n" + description;
        }

        StringBuilder roleContextBuilder = new StringBuilder();
        roleContextBuilder.append("Role: ").append(task.getRole().getTag()).append("\n");
        roleContextBuilder.append("Description: ").append(task.getRole().getDescription()).append("\n");

        try {
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
            session.setStatus(mappedStatus);
            session.setLastStatusCheckAt(Instant.now());

            // If API provides PR URL, it would be good to save it,
            // but the mock/api return might vary.
            // For now, focusing on the state machine.

            return julesSessionRepository.save(session);
        }

        return session;
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

    /**
     * Trigger for periodic maintenance of stuck Jules sessions.
     */
    @Scheduled(fixedRateString = "${jules.detect-stuck-rate-ms:60000}")
    public void detectStuck() {
        claimService.detectStuckSessions(stuckThresholdMinutes);
    }
}
