package com.eneik.production.services;

import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.repositories.PrReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

@Service
public class AutoMergeService {
    private static final Logger log = LoggerFactory.getLogger(AutoMergeService.class);
    private static final String APPROVAL_TOKEN = "CORE ARCHITECTURE VERIFIED. APPROVED.";

    private final PrReviewRepository prReviewRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final com.eneik.production.repositories.TaskRepository taskRepository;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final com.eneik.production.services.advice.RoleAdviceLoopService roleAdviceLoopService;

    public AutoMergeService(PrReviewRepository prReviewRepository,
                            com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                            com.eneik.production.repositories.TaskRepository taskRepository,
                            SystemSettingsService settingsService,
                            ObjectMapper objectMapper,
                            com.eneik.production.services.advice.RoleAdviceLoopService roleAdviceLoopService) {
        this.prReviewRepository = prReviewRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.taskRepository = taskRepository;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.roleAdviceLoopService = roleAdviceLoopService;
    }

    @Scheduled(fixedRateString = "${automerge.rate-ms:60000}")
    public void processAutoMerge() {
        List<PrReviewEntity> pendingReviews = prReviewRepository.findAll().stream()
                .filter(r -> "success".equalsIgnoreCase(r.getCiStatus()))
                .filter(r -> !Boolean.TRUE.equals(r.getMerged()))
                .toList();

        for (PrReviewEntity review : pendingReviews) {
            if (review.getDiffSummary() != null && review.getDiffSummary().contains(APPROVAL_TOKEN)) {
                executeMerge(review);
            }
        }
    }

    @Transactional
    protected void executeMerge(PrReviewEntity review) {
        log.info("AutoMergeService: Merging PR {} due to valid approval token and green CI", review.getPrUrl());
        
        boolean mergeSuccess = false;
        
        // Execute real merge on GitHub if enabled
        if (settingsService.effectiveBoolean("github_enabled")) {
            String token = settingsService.effectiveValue("github_token");
            if (token != null && !token.isBlank()) {
                try {
                    String prUrl = review.getPrUrl();
                    String owner = null;
                    String repoName = null;
                    String pullNumber = null;

                    if (prUrl != null && !prUrl.contains("/mock-") && prUrl.replace("https://github.com/", "").split("/").length >= 4) {
                        String cleanUrl = prUrl.replace("https://github.com/", "");
                        String[] parts = cleanUrl.split("/");
                        owner = parts[0];
                        repoName = parts[1];
                        pullNumber = parts[3];
                    } else if (review.getJulesSessionId() != null) {
                        // If it's a mock URL, look up the repository from the task and find open PRs on GitHub
                        var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
                        if (sessionOpt.isPresent()) {
                            var taskOpt = taskRepository.findById(sessionOpt.get().getTaskId());
                            if (taskOpt.isPresent()) {
                                String prRepoUrl = taskOpt.get().getProject().getRepositoryUrl();
                                if (prRepoUrl != null && prRepoUrl.replace("https://github.com/", "").split("/").length >= 2) {
                                    String cleanRepoUrl = prRepoUrl.replace("https://github.com/", "");
                                    String[] repoParts = cleanRepoUrl.split("/");
                                    owner = repoParts[0];
                                    repoName = repoParts[1];
                                } else {
                                    owner = "eneikcoworking-ctrl";
                                    repoName = taskOpt.get().getProject().getRepositoryName();
                                }
                                
                                String listUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/pulls?state=open";
                                HttpClient client = HttpClient.newHttpClient();
                                HttpRequest listRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(listUrl))
                                        .header("Authorization", "Bearer " + token)
                                        .header("Accept", "application/vnd.github+json")
                                        .GET()
                                        .build();
                                HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
                                if (listResponse.statusCode() == 200) {
                                    JsonNode prs = objectMapper.readTree(listResponse.body());
                                    if (prs.isArray() && prs.size() > 0) {
                                        JsonNode targetPr = prs.get(0);
                                        prUrl = targetPr.path("html_url").asText(prUrl);
                                        pullNumber = targetPr.path("number").asText(null);
                                    }
                                }
                            }
                        }
                    }

                    if (owner != null && repoName != null && pullNumber != null) {
                        String mergeUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/pulls/" + pullNumber + "/merge";
                        HttpClient client = HttpClient.newHttpClient();
                        HttpRequest mergeRequest = HttpRequest.newBuilder()
                                .uri(URI.create(mergeUrl))
                                .header("Authorization", "Bearer " + token)
                                .header("Accept", "application/vnd.github+json")
                                .header("Content-Type", "application/json")
                                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                                .build();
                        HttpResponse<String> mergeResponse = client.send(mergeRequest, HttpResponse.BodyHandlers.ofString());
                        if (mergeResponse.statusCode() >= 200 && mergeResponse.statusCode() < 300) {
                            log.info("GitHub API: Successfully merged real PR {} on GitHub!", prUrl);
                            if (prUrl != null) {
                                review.setPrUrl(prUrl);
                            }
                            mergeSuccess = true;
                        } else {
                            log.error("GitHub API: Failed to merge real PR {} on GitHub. Status: {}, Body: {}", prUrl, mergeResponse.statusCode(), mergeResponse.body());
                        }
                    }
                } catch (Exception e) {
                    log.error("Error executing real GitHub merge for PR {}: {}", review.getPrUrl(), e.getMessage(), e);
                }
            } else {
                log.warn("GitHub integration is enabled but token is missing");
            }
        } else {
            // Local mock mode for tests
            mergeSuccess = true;
            log.info("AutoMergeService: GitHub integration is disabled, processing as local mock merge");
        }

        if (mergeSuccess) {
            review.setMerged(true);
            prReviewRepository.save(review);

            // Also mark corresponding task as done
            if (review.getJulesSessionId() != null) {
                julesSessionRepository.findById(review.getJulesSessionId()).ifPresent(session -> {
                    UUID taskId = session.getTaskId();
                    taskRepository.findById(taskId).ifPresent(task -> {
                        task.setStatus(com.eneik.production.models.persistence.TaskStatus.done);
                        taskRepository.save(task);
                        log.info("AutoMergeService: Marked task {} as DONE because its PR was merged", taskId);
                        
                        // Call advice loop here upon successful merge
                        try {
                            roleAdviceLoopService.afterTaskComplete(taskId);
                            log.info("AutoMergeService: Triggered role advice loop for task {}", taskId);
                        } catch (Exception ex) {
                            log.error("AutoMergeService: Failed to trigger role advice loop for task {}: {}", taskId, ex.getMessage(), ex);
                        }
                    });
                });
            }
        }
    }
}
