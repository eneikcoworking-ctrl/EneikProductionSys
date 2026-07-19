package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FalsificationCycleService {
    private static final Logger log = LoggerFactory.getLogger(FalsificationCycleService.class);

    /**
     * A still-unresolved violation must not spawn a fresh self_falsification wishlist item every single
     * night forever: once one has fired for a role in this window, later runs skip it (regardless of
     * whether the earlier item is still pending or has already been compiled into a task) and let the
     * existing follow-up run its course.
     */
    private static final Duration FOLLOWUP_SUPPRESSION_WINDOW = Duration.ofDays(3);

    private static final int MAX_MERGED_PRS_PER_AUDIT = 5;
    private static final int MAX_DIFF_CHARS_PER_PR = 6000;

    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final WishlistRepository wishlistRepository;
    private final FalsificationRunRepository falsificationRunRepository;
    private final SystemSettingsService settingsService;
    private final com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.services.ProjectFlowService projectFlowService;
    private final ClientDeliverableReadinessService readinessService;

    @org.springframework.beans.factory.annotation.Value("${falsification.readiness-threshold:0.9}")
    private double readinessThreshold;

    public FalsificationCycleService(ProjectRepository projectRepository,
                                     RoleRepository roleRepository,
                                     RoleCapabilityLoader roleCapabilityLoader,
                                     WishlistRepository wishlistRepository,
                                     FalsificationRunRepository falsificationRunRepository,
                                     SystemSettingsService settingsService,
                                     com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService,
                                     @org.springframework.context.annotation.Lazy com.eneik.production.services.ProjectFlowService projectFlowService,
                                     ClientDeliverableReadinessService readinessService) {
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.wishlistRepository = wishlistRepository;
        this.falsificationRunRepository = falsificationRunRepository;
        this.settingsService = settingsService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.projectFlowService = projectFlowService;
        this.readinessService = readinessService;
    }

    @Scheduled(cron = "${falsification-cycle.cron:0 0 2 * * ?}")
    public void runDailyFalsificationCycle() {
        if (!settingsService.effectiveBoolean("falsification_cycle_enabled")) {
            log.info("FalsificationCycleService: Falsification cycle is disabled via feature flag.");
            return;
        }

        log.info("FalsificationCycleService: Starting daily falsification cycle check...");
        List<ProjectEntity> projects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.active)
                .toList();

        for (ProjectEntity project : projects) {
            try {
                executeCycleForProject(project);
            } catch (Exception e) {
                log.error("FalsificationCycleService: Failed for project {}: {}", project.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean hasRecentFollowUp(UUID projectId, String roleTag) {
        return wishlistRepository.existsByProjectIdAndSourceRoleTagAndSourceAndCreatedAtAfter(
                projectId, roleTag, WishlistSource.self_falsification,
                Instant.now().minus(FOLLOWUP_SUPPRESSION_WINDOW));
    }

    // Deliberately Gemini-free: refusal-criteria and methodological-falsification checks used to call
    // Gemini directly, once per active role per project, every cycle. Now dispatches a single Jules
    // eneikdru audit session per project (ProjectFlowService.dispatchFalsificationAudit) that reads the
    // real current diff and every active role's real charter file, then writes one JSON report;
    // completion (applyAuditViolations below) is driven asynchronously by JulesDispatchService once that
    // session opens its report PR. This cycle only fires every few hours, so it comfortably shares the
    // reserved eneikdru account with wishlist compilation instead of contending with real
    // product-implementation dispatch.
    public void executeCycleForProject(ProjectEntity project) {
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        if (readiness.ratio() < readinessThreshold) {
            // Auditing before there's a real object to audit just spends a reserved eneikdru session on
            // whatever process/design artifacts happen to be in main yet (confirmed live in
            // test-twenty-eighth: the first cycle ran against zero merged product code and found only
            // metadata-formatting nitpicks). Wait until most of what the client actually asked for has
            // really shipped (merged, not just review-approved - see ClientDeliverableReadinessService)
            // before spending capacity looking for violations in it.
            log.info("FalsificationCycleService: Project {} not ready for falsification yet ({}/{} client deliverable(s) merged, "
                            + "{}% < {}% threshold); skipping this cycle instead of auditing an incomplete project",
                    project.getName(), readiness.mergedDeliverables(), readiness.totalDeliverables(),
                    Math.round(readiness.ratio() * 100), Math.round(readinessThreshold * 100));
            return;
        }

        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();

        RecentChanges recentChanges = getRecentCodeChangesForAudit(project);
        if (recentChanges.text().isBlank()) {
            // No real code to audit yet is an honest, valid state (brand-new project, GitHub disabled,
            // nothing merged, or - Lean - nothing NEW merged since the last audit) - dispatching a Jules
            // session against an empty/stale prompt would just waste capacity and risk it inventing
            // violations to have something to report. Skip and retry next cycle instead of faking a diff
            // (this is the same bug this method already fixed once: the old fallback silently substituted
            // an unrelated PR-review remark string for a real diff).
            log.info("FalsificationCycleService: No new merged PR diffs (or local workspace diff) available for project {} since the last audit; skipping this cycle",
                    project.getName());
            return;
        }

        String prompt = buildAuditPrompt(activeRoles, recentChanges.text());

        UUID taskId = projectFlowService.dispatchFalsificationAudit(project, prompt, recentChanges.highestPrNumber());
        if (taskId == null) {
            log.warn("FalsificationCycleService: Could not dispatch falsification audit for project {}", project.getName());
            return;
        }
        log.info("FalsificationCycleService: Dispatched falsification audit task {} for project {} covering {} active role(s)",
                taskId, project.getName(), activeRoles.size());
    }

    private String buildAuditPrompt(List<RoleEntity> activeRoles, String latestDiff) {
        StringBuilder charters = new StringBuilder();
        for (RoleEntity role : activeRoles) {
            String rawRules = readRawRules(role);
            if (rawRules == null || rawRules.isBlank()) {
                continue;
            }
            charters.append("\n\n=== ROLE ").append(role.getTag()).append(" CHARTER ===\n").append(rawRules);
        }

        return """
                You are the falsification auditor for this project (BARCAN-TAG-09 role). Audit the CURRENT
                real code and recent activity below against every role charter provided. Do NOT implement,
                fix, or change any product code, and do not run builds or tests - this task only produces an
                audit report.

                Report only violations you can point to concretely in the diff/logs below - never invent a
                violation to have something to report, and never omit a real one. An empty violations list
                is a completely valid, honest result if nothing is actually wrong.

                For each role charter, check two things:
                1. Refusal criteria: does the current code/diff violate that role's stated REFUSAL CRITERIA?
                2. Methodological falsification: applying that charter's philosophical framing, is there a
                   confirmed systemic contradiction (not a stylistic nitpick)?

                Deliverable: create a new branch and open a PR that contains ONLY one file,
                `.eneik/falsification-report.json`, with EXACTLY this shape and no other files changed:
                {"violations": [
                  {"roleTag": "BARCAN-TAG-02", "type": "refusal_criteria", "reason": "concrete reason tied to the diff"},
                  {"roleTag": "BARCAN-TAG-07", "type": "methodological", "philosopher": "name", "thesis": "...",
                   "score": "3", "mustBe": "...", "performance": "...", "attractive": "..."}
                ]}
                Use "violations": [] if you find nothing wrong. Do not write, modify, or delete any other file.

                Recent diff and operational activity to audit:
                %s

                Role charters to audit against:
                %s
                """.formatted(latestDiff, charters);
    }

    public record AuditViolation(
            String roleTag,
            String type,
            String reason,
            String philosopher,
            String thesis,
            String score,
            String mustBe,
            String performance,
            String attractive
    ) {
    }

    @Transactional
    public void applyAuditViolations(ProjectEntity project, List<AuditViolation> violations, Integer highestPrNumberAudited) {
        int rolesCheckedCount = (int) roleRepository.findAll().stream().filter(RoleEntity::isActive).count();
        int violationsFoundCount = 0;
        int followUpsCreatedCount = 0;

        for (AuditViolation violation : violations) {
            String roleTag = violation.roleTag();
            if (roleTag == null || roleTag.isBlank()) {
                continue;
            }
            violationsFoundCount++;

            if (hasRecentFollowUp(project.getId(), roleTag)) {
                log.info("FalsificationCycleService: Skipping duplicate self_falsification wishlist for role {} — a follow-up was already created within {}", roleTag, FOLLOWUP_SUPPRESSION_WINDOW);
                continue;
            }

            WishlistEntity wishlist = new WishlistEntity();
            wishlist.setProjectId(project.getId());
            wishlist.setSource(WishlistSource.self_falsification);
            wishlist.setSourceRoleTag(roleTag);
            wishlist.setStatus(WishlistStatus.pending);
            wishlist.setLeanValue(LeanValue.essential);
            wishlist.setTocConstraintRef("HIGH_PRIORITY_DEBT");
            wishlist.setCompiledByRole("BARCAN-TAG-09");
            wishlist.setSixSigmaMetric("falsification_run_rate");
            wishlist.setDod("BARCAN-TAG-09: Falsification regression fixed");

            if ("methodological".equalsIgnoreCase(violation.type())) {
                String philosopher = violation.philosopher();
                String thesis = violation.thesis();
                String content = "Methodological contradiction confirmed by " + philosopher + ": " + thesis + "\n" +
                        "Score: " + violation.score() + "\n" +
                        "[Must-Be]: " + violation.mustBe() + "\n" +
                        "[Performance]: " + violation.performance() + "\n" +
                        "[Attractive]: " + violation.attractive();
                wishlist.setContent(content);
                wishlist.setJtbd("Resolve methodological contradiction identified by " + philosopher);
                wishlist.setAcceptanceCriteria("Given methodological contradiction by " + philosopher
                        + ", When resolving, Then Must-Be requirement is fulfilled: " + violation.mustBe());
                log.warn("FalsificationCycleService: Methodological contradiction confirmed for role {} by philosopher {}: {}",
                        roleTag, philosopher, thesis);
            } else {
                wishlist.setContent("Compliance violation detected for role " + roleTag + ". Violates: " + violation.reason());
                wishlist.setJtbd("Fix role refusal criteria violation detected by falsification cycle");
                wishlist.setAcceptanceCriteria("Refusal criteria check passes successfully");
                log.warn("FalsificationCycleService: Code violation detected for role {}: {}", roleTag, violation.reason());
            }

            wishlist = wishlistRepository.save(wishlist);
            followUpsCreatedCount++;
            log.info("FalsificationCycleService: Created self_falsification wishlist item {} for role {}", wishlist.getId(), roleTag);
        }

        // Never regress the dedup watermark: if this particular run only found local-workspace content
        // (no GitHub PR numbers involved), incoming is null - keep whatever the last real PR-based run
        // recorded rather than resetting it and re-auditing everything again next cycle.
        Integer previousHighest = falsificationRunRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())
                .map(FalsificationRunEntity::getHighestPrNumberAudited)
                .orElse(null);
        Integer watermark = highestPrNumberAudited == null ? previousHighest
                : (previousHighest == null ? highestPrNumberAudited : Math.max(previousHighest, highestPrNumberAudited));

        FalsificationRunEntity run = new FalsificationRunEntity();
        run.setProjectId(project.getId());
        run.setRunAt(Instant.now());
        run.setRolesCheckedCount(rolesCheckedCount);
        run.setViolationsFoundCount(violationsFoundCount);
        run.setTasksCreatedCount(followUpsCreatedCount);
        run.setHighestPrNumberAudited(watermark);
        falsificationRunRepository.save(run);

        log.info("FalsificationCycleService: Completed audit for project {}. Checked roles: {}, Violations: {}, Follow-up wishlist items created: {}",
                project.getName(), rolesCheckedCount, violationsFoundCount, followUpsCreatedCount);
    }

    private String readRawRules(RoleEntity role) {
        if (role.getRulesPath() == null || role.getRulesPath().isBlank()) {
            return "";
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(role.getRulesPath());
            if (java.nio.file.Files.exists(path)) {
                return java.nio.file.Files.readString(path);
            }
        } catch (Exception e) {
            log.warn("FalsificationCycleService: Failed to read raw rules for role {}: {}", role.getTag(), e.getMessage());
        }
        return "";
    }

    private boolean checkActuatorHealth(ProjectEntity project) {
        try {
            String simulatedHealth = settingsService.effectiveValue("simulated_actuator_health");
            if ("DOWN".equalsIgnoreCase(simulatedHealth) || "500".equals(simulatedHealth)) {
                return false; // critical regression
            }

            String healthUrl = "http://localhost:8080/actuator/health";
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("UP");
        } catch (Exception e) {
            return false; // connection failed -> critical regression
        }
    }

    /**
     * Was "getLatestProjectDiff" - renamed because its old fallback was a category error, not just a
     * missing-data gap: when neither a local workspace nor a real Git diff was available (the normal case
     * for GitHub-based projects, which never keep a synced local clone), it queried
     * {@code pr_reviews.diff_summary} and handed that to the falsification auditor labelled as "the diff
     * to audit". That column has been repurposed system-wide to hold review VERDICT TEXT ("CORE
     * ARCHITECTURE VERIFIED. APPROVED...", "REVIEW REJECTED...") rather than an actual diff - so the
     * auditor was reading someone else's approval remark and being told it was the code. Confirmed live in
     * the test-twenty-fifth experiment: the fetched "diff" was a one-line PR-review-fallback remark, not a
     * single line of real code.
     *
     * Real code now comes from the actual GitHub API: the unified diffs of the most recently merged PRs
     * for this project (GitHubPullRequestService.fetchDiffText, the same method the PR-review fallback
     * uses to see real diffs). Falls back to a real local `git diff` only for projects still running
     * without GitHub. If neither yields anything, returns blank - the caller skips the cycle honestly
     * instead of auditing nothing.
     */
    public record RecentChanges(String text, Integer highestPrNumber) {
        static RecentChanges empty() {
            return new RecentChanges("", null);
        }
    }

    private RecentChanges getRecentCodeChangesForAudit(ProjectEntity project) {
        StringBuilder changes = new StringBuilder();
        Integer highestPrNumberThisBatch = null;

        // Lean: don't re-fetch and re-audit PRs already covered by a previous run - real GitHub API calls
        // and a real Jules session spent auditing code that hasn't changed since it was last checked.
        Integer lastAuditedPrNumber = falsificationRunRepository.findTopByProjectIdOrderByRunAtDesc(project.getId())
                .map(FalsificationRunEntity::getHighestPrNumberAudited)
                .orElse(null);

        var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
        if (snapshot.available()) {
            List<com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest> recentMerges =
                    snapshot.closed().stream()
                            .filter(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::merged)
                            .filter(pr -> lastAuditedPrNumber == null || pr.number() > lastAuditedPrNumber)
                            .sorted(java.util.Comparator.comparingInt(
                                    com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::number).reversed())
                            .limit(MAX_MERGED_PRS_PER_AUDIT)
                            .toList();
            for (var pr : recentMerges) {
                java.util.Optional<String> diff = gitHubPullRequestService.fetchDiffText(project, pr.number());
                if (diff.isPresent() && !diff.get().isBlank()) {
                    changes.append("\n\n=== PR #").append(pr.number()).append(" \"").append(pr.title()).append("\" (merged) ===\n");
                    changes.append(truncate(diff.get(), MAX_DIFF_CHARS_PER_PR));
                    if (highestPrNumberThisBatch == null || pr.number() > highestPrNumberThisBatch) {
                        highestPrNumberThisBatch = pr.number();
                    }
                }
            }
            if (!recentMerges.isEmpty()) {
                log.info("FalsificationCycleService: Fetched real diffs for {} newly merged PR(s) for project {} (since PR #{})",
                        recentMerges.size(), project.getName(), lastAuditedPrNumber == null ? "none audited yet" : String.valueOf(lastAuditedPrNumber));
            }
        }

        if (changes.isEmpty() && project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
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
                        log.info("FalsificationCycleService: Retrieved local Git diff for project {}", project.getName());
                        changes.append("\n\n=== Local workspace diff (HEAD~1) ===\n").append(truncate(diffSb.toString(), MAX_DIFF_CHARS_PER_PR));
                    }
                } catch (Exception e) {
                    log.warn("FalsificationCycleService: Failed to retrieve Git diff from workspace {}: {}",
                            project.getWorkspacePath(), e.getMessage());
                }
            }
        }

        if (changes.isEmpty()) {
            return RecentChanges.empty();
        }

        java.util.List<String> recentActivity = com.eneik.production.services.logging.LogScopeBuffer.recent(project.getId(), 60);
        if (!recentActivity.isEmpty()) {
            changes.append("\n\n--- RECENT PROJECT OPERATIONAL ACTIVITY (last ").append(recentActivity.size()).append(" scoped log lines) ---\n");
            for (String line : recentActivity) {
                changes.append(line).append("\n");
            }
        }

        return new RecentChanges(changes.toString(), highestPrNumberThisBatch);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... [truncated at " + maxLength + " chars]";
    }
}
