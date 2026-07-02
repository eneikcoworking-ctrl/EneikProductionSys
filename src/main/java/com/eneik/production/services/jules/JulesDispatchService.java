package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JulesDispatchService {
    private static final Logger log = LoggerFactory.getLogger(JulesDispatchService.class);

    private final JulesApiClient julesApiClient;
    private final JulesSessionRepository julesSessionRepository;
    private final TaskRepository taskRepository;
    private final String sourcePrefix;

    @Value("${jules.stuck-threshold-minutes:30}")
    private int stuckThresholdMinutes;

    public JulesDispatchService(JulesApiClient julesApiClient,
                                JulesSessionRepository julesSessionRepository,
                                TaskRepository taskRepository,
                                @Value("${jules.source-prefix:sources/github/eneikcoworking-ctrl/}") String sourcePrefix) {
        this.julesApiClient = julesApiClient;
        this.julesSessionRepository = julesSessionRepository;
        this.taskRepository = taskRepository;
        this.sourcePrefix = sourcePrefix;
    }

    @Transactional
    public JulesDispatchResult dispatch(TaskEntity task) {
        JulesSessionEntity session = dispatchInternal(task, null);
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
        return dispatchInternal(task, accountId);
    }

    private JulesSessionEntity dispatchInternal(TaskEntity task, UUID accountId) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setAccountId(accountId != null ? accountId : UUID.fromString("00000000-0000-0000-0000-000000000000")); // Fallback UUID if null
        session.setStatus("queued");

        ProjectEntity project = task.getProject();
        if (project == null) {
            session.setStatus("failed");
            return julesSessionRepository.save(session);
        }

        String repoUrl = sourcePrefix + project.getRepositoryName();
        String description = task.getDescription();
        String roleContext = "Role: " + task.getRole().getTag() + "\n" + task.getRole().getDescription();

        String externalId = julesApiClient.createSession(repoUrl, description, roleContext);

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

        String rawStatus = julesApiClient.getSessionStatus(session.getExternalSessionId());
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

    @Scheduled(fixedRateString = "${jules.detect-stuck-rate-ms:60000}")
    @Transactional
    public void detectStuck() {
        Instant threshold = Instant.now().minus(stuckThresholdMinutes, ChronoUnit.MINUTES);
        List<JulesSessionEntity> runningSessions = julesSessionRepository.findByStatus("running");

        for (JulesSessionEntity session : runningSessions) {
            if (session.getUpdatedAt().isBefore(threshold)) {
                log.warn("Session {} is stuck (no update for {} minutes)", session.getId(), stuckThresholdMinutes);
                session.setStatus("stuck");
                julesSessionRepository.save(session);
            }
        }
    }
}
