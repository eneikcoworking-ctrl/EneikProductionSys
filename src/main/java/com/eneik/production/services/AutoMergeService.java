package com.eneik.production.services;

import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.NeedsHumanReviewRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.NeedsHumanReviewEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.services.github.GitHubPullRequestService;
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
import java.util.Map;
import java.util.HashMap;

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
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final WishlistRepository wishlistRepository;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.services.video.VideoAssetService videoAssetService;
    private final com.eneik.production.services.dashboard.ProjectOperationalContextService contextService;

    public AutoMergeService(PrReviewRepository prReviewRepository,
                            com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                            com.eneik.production.repositories.TaskRepository taskRepository,
                            SystemSettingsService settingsService,
                            ObjectMapper objectMapper,
                            com.eneik.production.services.advice.RoleAdviceLoopService roleAdviceLoopService,
                            TaskConflictRepository taskConflictRepository,
                            NeedsHumanReviewRepository needsHumanReviewRepository,
                            JulesDispatchService julesDispatchService,
                            RoleCapabilityLoader roleCapabilityLoader,
                            WishlistRepository wishlistRepository,
                            MLPredictionServiceClient mlPredictionServiceClient,
                            GitHubPullRequestService gitHubPullRequestService,
                            com.eneik.production.services.video.VideoAssetService videoAssetService,
                            com.eneik.production.services.dashboard.ProjectOperationalContextService contextService) {
        this.prReviewRepository = prReviewRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.taskRepository = taskRepository;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.roleAdviceLoopService = roleAdviceLoopService;
        this.taskConflictRepository = taskConflictRepository;
        this.needsHumanReviewRepository = needsHumanReviewRepository;
        this.julesDispatchService = julesDispatchService;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.wishlistRepository = wishlistRepository;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.videoAssetService = videoAssetService;
        this.contextService = contextService;
    }

    @Scheduled(fixedRateString = "${automerge.rate-ms:60000}")
    public void processAutoMerge() {
        List<PrReviewEntity> pendingReviews = prReviewRepository.findAll().stream()
                .filter(r -> "success".equalsIgnoreCase(r.getCiStatus()))
                .filter(r -> !Boolean.TRUE.equals(r.getMerged()))
                .toList();

        for (PrReviewEntity review : pendingReviews) {
            boolean isChaotic = false;
            if (review.getJulesSessionId() != null) {
                var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
                if (sessionOpt.isPresent()) {
                    var taskOpt = taskRepository.findById(sessionOpt.get().getTaskId());
                    if (taskOpt.isPresent() && "chaotic".equalsIgnoreCase(taskOpt.get().getCynefinDomain())) {
                        isChaotic = true;
                    }
                }
            }

            if (isChaotic) {
                executeMerge(review);
            } else if (review.getDiffSummary() != null && review.getDiffSummary().contains(APPROVAL_TOKEN)) {
                executeMerge(review);
            }
        }
    }

    @Transactional
    protected void executeMerge(PrReviewEntity review) {
        log.info("AutoMergeService: Merging PR {} due to valid approval token and green CI", review.getPrUrl());
        PullRequestTarget pullRequestTarget = null;
        
        // 1. Check Cynefin complex domain spike handling and Role Philosophical Filter
        if (review.getJulesSessionId() != null) {
            var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                var taskOpt = taskRepository.findById(session.getTaskId());
                if (taskOpt.isPresent()) {
                    var task = taskOpt.get();
                    
                    if ("complex".equalsIgnoreCase(task.getCynefinDomain())) {
                        log.info("Cynefin Domain complex: Task {} spike completed. Not merging branch.", task.getId());
                        task.setStatus(com.eneik.production.models.persistence.TaskStatus.spike_completed);
                        taskRepository.save(task);
                        
                        review.setMerged(false);
                        prReviewRepository.save(review);
                        return;
                    }

                    // Role Philosophical Filter
                    String refusalCriteria = "";
                    String roleTag = task.getRole().getTag();
                    try {
                        com.eneik.production.dto.RoleRules rules = roleCapabilityLoader.loadRules(roleTag);
                        if (rules != null) {
                            refusalCriteria = rules.refusalCriteria();
                        }
                    } catch (Exception e) {
                        log.warn("Could not load refusal criteria for role {}: {}", roleTag, e.getMessage());
                    }

                    if (refusalCriteria != null && !refusalCriteria.trim().isEmpty()) {
                        String prDiff = "mock_diff";
                        boolean isConflictSimulated = review.getPrUrl() != null && (review.getPrUrl().contains("conflict") || review.getPrUrl().contains("dirty"));
                        if (settingsService.effectiveBoolean("github_enabled") && !isConflictSimulated) {
                            String token = settingsService.effectiveValue("github_token");
                            if (token != null && !token.isBlank()) {
                                pullRequestTarget = resolvePullRequestTarget(review);
                                if (pullRequestTarget != null) {
                                    prDiff = fetchPrDiff(token, pullRequestTarget.owner(), pullRequestTarget.repo(), pullRequestTarget.pullNumber());
                                }
                            }
                        } else {
                            if (review.getDiffSummary() != null && (review.getDiffSummary().contains("refusal_violation") || review.getDiffSummary().contains("violates_criteria"))) {
                                prDiff = "violates_criteria";
                            }
                        }

                        if ("mock_diff".equals(prDiff)) {
                            String localDiff = getLocalWorkspaceDiff(task.getProject());
                            if (localDiff != null && !localDiff.isBlank()) {
                                prDiff = localDiff;
                            }
                        }

                        Map<String, Object> rcResult = mlPredictionServiceClient.checkRefusalCriteria(prDiff, refusalCriteria);
                        boolean isCompliant = Boolean.TRUE.equals(rcResult.get("compliant"));
                        if (!isCompliant) {
                            String reason = (String) rcResult.get("reason");
                            log.warn("Philosophical Filter mismatch detected for task {} (role {}): {}", task.getId(), roleTag, reason);
                            
                            com.eneik.production.models.persistence.WishlistEntity wishlist = new com.eneik.production.models.persistence.WishlistEntity();
                            wishlist.setProjectId(task.getProject().getId());
                            wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.role_mismatch_followup);
                            wishlist.setSourceRoleTag(roleTag);
                            wishlist.setContent("Role mismatch cleanup required: " + reason);
                            wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
                            wishlist.setLeanValue(com.eneik.production.models.persistence.LeanValue.essential);
                            wishlistRepository.save(wishlist);
                        }
                    }
                }
            }
        }

        boolean mergeSuccess = false;
        boolean isConflictSimulated = review.getPrUrl() != null && (review.getPrUrl().contains("conflict") || review.getPrUrl().contains("dirty"));
        
        // Execute real merge on GitHub if enabled
        if (settingsService.effectiveBoolean("github_enabled") && !isConflictSimulated) {
            String token = settingsService.effectiveValue("github_token");
            if (token != null && !token.isBlank()) {
                try {
                    if (pullRequestTarget == null) {
                        pullRequestTarget = resolvePullRequestTarget(review);
                    }

                    if (pullRequestTarget == null) {
                        log.warn("AutoMergeService: Skipping merge for invalid or unresolved PR URL {}", review.getPrUrl());
                        review.setCiStatus("invalid_pr");
                        prReviewRepository.save(review);
                        return;
                    }

                    String prUrl = pullRequestTarget.url();
                    String owner = pullRequestTarget.owner();
                    String repoName = pullRequestTarget.repo();
                    String pullNumber = pullRequestTarget.pullNumber();

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

                        // Automatically generate a walkthrough video for the merged task!
                        try {
                            var context = contextService.build(task.getProject().getId(), task.getProject().getName());
                            var videoResult = videoAssetService.generateAsset(
                                    task.getProject(),
                                    context,
                                    "Feature walkthrough for task: " + task.getDescription() + ". Diff summary: " + review.getDiffSummary(),
                                    "walkthrough",
                                    "standard",
                                    false
                            );
                            if (videoResult.available()) {
                                log.info("AutoMergeService: Veo Video walkthrough successfully generated at {}", videoResult.videoPath());
                            } else {
                                log.warn("AutoMergeService: Veo Video walkthrough generation was unavailable: {}", videoResult.message());
                            }
                        } catch (Exception e) {
                            log.error("AutoMergeService: Failed to trigger Veo Video walkthrough: " + e.getMessage());
                        }
                        
                        // Create chaotic_debt wishlist entry if it was chaotic
                        if ("chaotic".equalsIgnoreCase(task.getCynefinDomain())) {
                            com.eneik.production.models.persistence.WishlistEntity debt = new com.eneik.production.models.persistence.WishlistEntity();
                            debt.setProjectId(task.getProject().getId());
                            debt.setSource(com.eneik.production.models.persistence.WishlistSource.chaotic_debt);
                            debt.setSourceRoleTag(task.getRole().getTag());
                            debt.setContent("Emergency chaotic debt cleanup: Refactor changes introduced in chaotic task " + task.getId());
                            debt.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
                            debt.setLeanValue(com.eneik.production.models.persistence.LeanValue.essential);
                            debt.setTocConstraintRef("HIGH_PRIORITY_DEBT");
                            wishlistRepository.save(debt);
                            log.info("Created chaotic_debt wishlist item for refactoring.");
                        }

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

    private String fetchPrDiff(String token, String owner, String repo, String pullNumber) {
        try {
            String url = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + pullNumber;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3.diff")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (Exception e) {
            log.error("Failed to fetch PR diff: {}", e.getMessage());
        }
        return "";
    }

    private PullRequestTarget resolvePullRequestTarget(PrReviewEntity review) {
        PullRequestTarget parsed = parseGithubPullRequestUrl(review.getPrUrl());
        if (parsed != null) {
            return parsed;
        }

        if (review.getJulesSessionId() == null) {
            return null;
        }

        return julesSessionRepository.findById(review.getJulesSessionId())
                .flatMap(session -> taskRepository.findById(session.getTaskId())
                        .flatMap(task -> gitHubPullRequestService.findOpenPullRequestBySession(task.getProject(), session.getExternalSessionId())))
                .map(pr -> parseGithubPullRequestUrl(pr.url()))
                .orElse(null);
    }

    private PullRequestTarget parseGithubPullRequestUrl(String prUrl) {
        if (prUrl == null || prUrl.isBlank() || prUrl.contains("/mock-")) {
            return null;
        }
        try {
            URI uri = URI.create(prUrl);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return null;
            }
            String[] parts = uri.getPath().replaceAll("^/+", "").split("/");
            if (parts.length >= 4 && "pull".equals(parts[2]) && parts[3].matches("\\d+")) {
                return new PullRequestTarget(prUrl, parts[0], parts[1], parts[3]);
            }
        } catch (Exception e) {
            log.warn("Could not parse GitHub pull request URL {}: {}", prUrl, e.getMessage());
        }
        return null;
    }

    private record PullRequestTarget(String url, String owner, String repo, String pullNumber) {}

    private String getLocalWorkspaceDiff(com.eneik.production.models.persistence.ProjectEntity project) {
        if (project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
            java.io.File workspaceDir = new java.io.File(project.getWorkspacePath());
            if (workspaceDir.exists() && workspaceDir.isDirectory()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("git", "diff", "HEAD~1");
                    pb.directory(workspaceDir);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
                    );
                    StringBuilder diffSb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        diffSb.append(line).append("\n");
                    }
                    process.waitFor();
                    if (process.exitValue() == 0 && diffSb.length() > 0) {
                        return diffSb.toString();
                    }
                } catch (Exception e) {
                    log.warn("AutoMergeService: Failed to retrieve Git diff from workspace {}: {}",
                            project.getWorkspacePath(), e.getMessage());
                }
            }
        }
        return null;
    }
}
