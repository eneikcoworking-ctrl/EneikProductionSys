package com.eneik.production.controllers.github;

import com.eneik.production.dto.monitor.PrDataDto;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.services.monitor.PrReviewPipelineService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.ClaimService;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Collections;

@RestController
@RequestMapping("/api/webhooks/github")
public class GithubWebhookController {
    private static final Logger log = LoggerFactory.getLogger(GithubWebhookController.class);

    private final PrReviewPipelineService prReviewPipelineService;
    private final JulesDispatchService julesDispatchService;
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;
    private final ClaimService claimService;
    private final ObjectMapper objectMapper;

    public GithubWebhookController(PrReviewPipelineService prReviewPipelineService,
                                   JulesDispatchService julesDispatchService,
                                   TaskRepository taskRepository,
                                   AccountRepository accountRepository,
                                   ClaimService claimService,
                                   ObjectMapper objectMapper) {
        this.prReviewPipelineService = prReviewPipelineService;
        this.julesDispatchService = julesDispatchService;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.claimService = claimService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, @RequestHeader("X-GitHub-Event") String eventType) {
        log.info("Received GitHub Webhook: {}", eventType);

        if ("pull_request".equals(eventType)) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                String action = root.path("action").asText();
                if ("opened".equals(action)) {
                    String prUrl = root.path("pull_request").path("html_url").asText();
                    String branch = root.path("pull_request").path("head").path("ref").asText();

                    // Derive task ID from branch name if possible, or use a heuristic.
                    // For verification, we assume the branch name contains task ID or we find by PR URL.
                    // In this MVP, we find the first running task for the repo.
                    String repoName = root.path("repository").path("name").asText();

                    log.info("PR Opened: {}. Dispatching AI Reviewer.", prUrl);

                    // Simulate PR Data extraction
                    PrDataDto prData = new PrDataDto();
                    prData.setCiStatus("pending");
                    prData.setLinesChanged(root.path("pull_request").path("additions").asInt());
                    prData.setFilesChanged(root.path("pull_request").path("changed_files").asInt());
                    prData.setChangedFiles(Collections.emptyList());

                    // 1. Record review entry
                    PrReviewEntity review = prReviewPipelineService.onPrOpened(prUrl, UUID.randomUUID(), prData);

                    // 2. Dispatch AI Reviewer
                    // For the test, we find a task associated with this repo and dispatch a reviewer mode session.
                    taskRepository.findAll().stream()
                            .filter(t -> t.getProject().getRepositoryName().equals(repoName))
                            .filter(t -> t.getStatus() == com.eneik.production.models.persistence.TaskStatus.claimed)
                            .findFirst()
                            .ifPresent(task -> {
                                // Important: Before reviewer starts, the implementer's claim must be completed
                                try {
                                    claimService.complete(task.getId());
                                } catch (Exception e) {
                                    log.warn("Could not complete implementer claim for task {}: {}", task.getId(), e.getMessage());
                                }

                                accountRepository.lockNextIdleAccountForProject(task.getProject().getId())
                                        .ifPresent(account -> {
                                            julesDispatchService.dispatch(task, account.getId(), "REVIEWER");
                                        });
                            });

                    return ResponseEntity.ok("Review Dispatched");
                }
            } catch (Exception e) {
                log.error("Error processing PR webhook", e);
                return ResponseEntity.internalServerError().body(e.getMessage());
            }
        }

        return ResponseEntity.ok("Ignored");
    }
}
