package com.eneik.production.controllers.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.dto.monitor.PrDataDto;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.monitor.PrReviewPipelineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validated GitHub Webhook Ingress (ALVA_NOE_01_PERCEPTION_ACTION_LOOP [D011] /
 * DEREK_PARFIT_02_CAUSAL_PROCESS_TRACE [D013]).
 *
 * <p>Receives bottom-up GitHub signals (pull_request opened/synchronized), validates
 * payload authenticity against configured secret, binds PR truth to project/task lineage,
 * records review entry, and triggers AI Reviewer dispatch without relying on push timers.
 */
@RestController
@RequestMapping("/api/webhooks/github")
public class GithubWebhookController {
    private static final Logger log = LoggerFactory.getLogger(GithubWebhookController.class);
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final PrReviewPipelineService prReviewPipelineService;
    private final JulesDispatchService julesDispatchService;
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;
    private final ClaimService claimService;
    private final ObjectMapper objectMapper;
    private final GithubConfig githubConfig;
    private final ProjectRepository projectRepository;
    private final JulesSessionRepository julesSessionRepository;

    public GithubWebhookController(PrReviewPipelineService prReviewPipelineService,
                                   JulesDispatchService julesDispatchService,
                                   TaskRepository taskRepository,
                                   AccountRepository accountRepository,
                                   ClaimService claimService,
                                   ObjectMapper objectMapper,
                                   GithubConfig githubConfig,
                                   ProjectRepository projectRepository,
                                   JulesSessionRepository julesSessionRepository) {
        this.prReviewPipelineService = prReviewPipelineService;
        this.julesDispatchService = julesDispatchService;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.claimService = claimService;
        this.objectMapper = objectMapper;
        this.githubConfig = githubConfig;
        this.projectRepository = projectRepository;
        this.julesSessionRepository = julesSessionRepository;
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader) {

        if (eventType == null || eventType.isBlank()) {
            return ResponseEntity.badRequest().body("Missing X-GitHub-Event header");
        }

        if (payload == null || payload.isBlank()) {
            return ResponseEntity.badRequest().body("Missing webhook payload");
        }

        // Validate webhook secret signature if secret is configured
        String secret = githubConfig != null ? githubConfig.getWebhookSecret() : null;
        if (secret != null && !secret.isBlank()) {
            if (!isValidSignature(payload, signatureHeader, secret)) {
                log.warn("Rejected GitHub webhook with invalid signature header: {}", signatureHeader);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
            }
        }

        log.info("Received GitHub Webhook: {}", eventType);

        if ("ping".equalsIgnoreCase(eventType)) {
            return ResponseEntity.ok("pong");
        }

        if ("pull_request".equalsIgnoreCase(eventType)) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                String action = root.path("action").asText("");
                if ("opened".equalsIgnoreCase(action) || "synchronize".equalsIgnoreCase(action) || "reopened".equalsIgnoreCase(action)) {
                    String prUrl = root.path("pull_request").path("html_url").asText("");
                    String branch = root.path("pull_request").path("head").path("ref").asText("");
                    String repoName = root.path("repository").path("name").asText("");

                    if (prUrl.isBlank() || repoName.isBlank()) {
                        return ResponseEntity.badRequest().body("Missing required repository or PR URL");
                    }

                    log.info("PR Event '{}': url={}, repo={}, branch={}. Dispatching review pipeline.",
                            action, prUrl, repoName, branch);

                    // Extract truthful PR Data
                    PrDataDto prData = new PrDataDto();
                    prData.setCiStatus("pending");
                    int additions = root.path("pull_request").path("additions").asInt(0);
                    int deletions = root.path("pull_request").path("deletions").asInt(0);
                    prData.setLinesChanged(additions + deletions);
                    prData.setFilesChanged(root.path("pull_request").path("changed_files").asInt(0));
                    prData.setChangedFiles(Collections.emptyList());

                    // 1. Record review entry
                    PrReviewEntity review = prReviewPipelineService.onPrOpened(prUrl, UUID.randomUUID(), prData);

                    // 2. Resolve task by lineage
                    Optional<TaskEntity> taskOpt = resolveTaskForPullRequest(prUrl, branch, repoName);

                    if (taskOpt.isPresent()) {
                        TaskEntity task = taskOpt.get();
                        try {
                            claimService.complete(task.getId());
                        } catch (Exception e) {
                            log.warn("Could not complete implementer claim for task {}: {}", task.getId(), e.getMessage());
                        }

                        accountRepository.lockNextIdleAccountForProject(task.getProject().getId())
                                .ifPresentOrElse(account -> {
                                    julesDispatchService.dispatch(task, account.getId(), "REVIEWER");
                                }, () -> {
                                    log.warn("GithubWebhookController: no idle and enabled account available for project {} (review dispatch deferred)",
                                            task.getProject().getId());
                                });
                        return ResponseEntity.ok("Review Dispatched for task " + task.getId());
                    } else {
                        log.info("No claimed task found matching PR {}, review registered without active task binding", prUrl);
                        return ResponseEntity.ok("Review Registered (no task binding)");
                    }
                }
                return ResponseEntity.ok("Action " + action + " ignored");
            } catch (Exception e) {
                log.error("Error processing PR webhook", e);
                return ResponseEntity.internalServerError().body("Error processing PR webhook: " + e.getMessage());
            }
        }

        return ResponseEntity.ok("Ignored");
    }

    private Optional<TaskEntity> resolveTaskForPullRequest(String prUrl, String branch, String repoName) {
        // Lineage 1: Check if an active JulesSession is bound to this PR URL
        List<JulesSessionEntity> sessions = julesSessionRepository.findByPrUrlIn(List.of(prUrl));
        if (sessions != null && !sessions.isEmpty()) {
            for (JulesSessionEntity session : sessions) {
                if (session.getTaskId() != null) {
                    Optional<TaskEntity> opt = taskRepository.findById(session.getTaskId());
                    if (opt.isPresent()) {
                        return opt;
                    }
                }
            }
        }

        // Lineage 2: Try to extract taskId from branch name
        if (branch != null && !branch.isBlank()) {
            Matcher matcher = UUID_PATTERN.matcher(branch);
            if (matcher.find()) {
                try {
                    UUID branchTaskId = UUID.fromString(matcher.group());
                    Optional<TaskEntity> opt = taskRepository.findById(branchTaskId);
                    if (opt.isPresent()) {
                        return opt;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Lineage 3: Scoped project task lookup by repository name (no global findAll)
        Optional<ProjectEntity> projectOpt = projectRepository.findFirstByRepositoryNameIgnoreCase(repoName);
        if (projectOpt.isPresent()) {
            List<TaskEntity> claimed = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(
                    projectOpt.get().getId(), TaskStatus.claimed);
            if (!claimed.isEmpty()) {
                return Optional.of(claimed.get(0));
            }
        }

        return Optional.empty();
    }

    private boolean isValidSignature(String payload, String signatureHeader, String secret) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to compute webhook signature", e);
            return false;
        }
    }
}
