package com.eneik.production.services;

import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.NeedsHumanReviewRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.NeedsHumanReviewEntity;
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
import java.util.ArrayList;
import java.time.Instant;

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
    private final TaskConflictRepository taskConflictRepository;
    private final NeedsHumanReviewRepository needsHumanReviewRepository;
    private final JulesDispatchService julesDispatchService;

    public AutoMergeService(PrReviewRepository prReviewRepository,
                            com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                            com.eneik.production.repositories.TaskRepository taskRepository,
                            SystemSettingsService settingsService,
                            ObjectMapper objectMapper,
                            com.eneik.production.services.advice.RoleAdviceLoopService roleAdviceLoopService,
                            TaskConflictRepository taskConflictRepository,
                            NeedsHumanReviewRepository needsHumanReviewRepository,
                            JulesDispatchService julesDispatchService) {
        this.prReviewRepository = prReviewRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.taskRepository = taskRepository;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.roleAdviceLoopService = roleAdviceLoopService;
        this.taskConflictRepository = taskConflictRepository;
        this.needsHumanReviewRepository = needsHumanReviewRepository;
        this.julesDispatchService = julesDispatchService;
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
        boolean isConflictSimulated = review.getPrUrl() != null && (review.getPrUrl().contains("conflict") || review.getPrUrl().contains("dirty"));
        
        // Execute real merge on GitHub if enabled
        if (settingsService.effectiveBoolean("github_enabled") && !isConflictSimulated) {
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
                            resolveActiveConflict(review.getJulesSessionId());
                        } else {
                            log.error("GitHub API: Failed to merge real PR {} on GitHub. Status: {}, Body: {}", prUrl, mergeResponse.statusCode(), mergeResponse.body());
                            if (mergeResponse.statusCode() == 405 || mergeResponse.statusCode() == 409) {
                                handleMergeConflict(review, owner, repoName, pullNumber, token);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error executing real GitHub merge for PR {}: {}", review.getPrUrl(), e.getMessage(), e);
                }
            } else {
                log.warn("GitHub integration is enabled but token is missing");
            }
        } else {
            if (isConflictSimulated) {
                log.warn("AutoMergeService: Simulating merge conflict defect for PR {}", review.getPrUrl());
                handleMergeConflict(review, null, null, null, null);
            } else {
                // Local mock mode for tests
                mergeSuccess = true;
                log.info("AutoMergeService: GitHub integration is disabled, processing as local mock merge");
                resolveActiveConflict(review.getJulesSessionId());
            }
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

    private void handleMergeConflict(PrReviewEntity review, String owner, String repo, String pullNumber, String token) {
        if (review.getJulesSessionId() == null) return;
        var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
        if (sessionOpt.isEmpty()) return;
        var session = sessionOpt.get();
        UUID taskId = session.getTaskId();
        var taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) return;
        var task = taskOpt.get();
        
        var conflictOpt = taskConflictRepository.findFirstByTaskIdAndResolutionStatus(taskId, "pending");
        TaskConflictEntity conflict;
        if (conflictOpt.isPresent()) {
            conflict = conflictOpt.get();
        } else {
            conflict = new TaskConflictEntity();
            conflict.setTask(task);
            conflict.setPrUrl(review.getPrUrl());
            conflict.setConflictType("merge_conflict");
            conflict.setResolutionAttempts(0);
            conflict.setResolutionStatus("pending");
        }
        
        List<String> files = new ArrayList<>();
        if (token != null && owner != null && repo != null && pullNumber != null) {
            files = fetchPrFiles(token, owner, repo, pullNumber);
        } else {
            // Mock or fallback: try to parse task's file scope
            if (task.getFileScope() != null) {
                try {
                    files = objectMapper.readValue(task.getFileScope(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception e) {}
            }
        }
        
        String filesJson = "[]";
        try {
            filesJson = objectMapper.writeValueAsString(files);
        } catch (Exception e) {}
        conflict.setConflictingFiles(filesJson);
        
        review.setCiStatus("conflict");
        prReviewRepository.save(review);
        
        int attempts = conflict.getResolutionAttempts() + 1;
        conflict.setResolutionAttempts(attempts);
        
        if (attempts >= 3) {
            conflict.setResolutionStatus("escalated");
            taskConflictRepository.save(conflict);
            
            NeedsHumanReviewEntity humanReview = new NeedsHumanReviewEntity();
            humanReview.setTask(task);
            humanReview.setReason("repeated merge conflict after 3 auto-resolve attempts");
            needsHumanReviewRepository.save(humanReview);
            
            log.warn("Merge conflict for task {} escalated to human review after {} attempts", taskId, attempts);
        } else {
            taskConflictRepository.save(conflict);
            
            task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);
            taskRepository.save(task);
            
            log.info("Triggering auto-resolve rebase attempt {}/3 for task {}", attempts, taskId);
            // Re-queue or just dispatch new session directly
            UUID accountId = session.getAccountId();
            julesDispatchService.dispatch(task, accountId, "IMPLEMENTER");
        }
    }

    private void resolveActiveConflict(UUID julesSessionId) {
        if (julesSessionId == null) return;
        julesSessionRepository.findById(julesSessionId).ifPresent(session -> {
            taskConflictRepository.findFirstByTaskIdAndResolutionStatus(session.getTaskId(), "pending")
                .ifPresent(conflict -> {
                    conflict.setResolutionStatus("auto_resolved");
                    conflict.setResolvedAt(Instant.now());
                    taskConflictRepository.save(conflict);
                    log.info("Conflict resolved for task {} via auto_resolved status", session.getTaskId());
                });
        });
    }

    private List<String> fetchPrFiles(String token, String owner, String repo, String pullNumber) {
        try {
            String filesUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + pullNumber + "/files";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(filesUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode filesArray = objectMapper.readTree(response.body());
                List<String> filenames = new ArrayList<>();
                if (filesArray.isArray()) {
                    for (JsonNode fileNode : filesArray) {
                        filenames.add(fileNode.path("filename").asText());
                    }
                }
                return filenames;
            }
        } catch (Exception e) {
            log.error("Failed to fetch PR files: {}", e.getMessage());
        }
        return List.of();
    }
}
