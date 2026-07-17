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
import java.util.Map;
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

    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final WishlistRepository wishlistRepository;
    private final FalsificationRunRepository falsificationRunRepository;
    private final SystemSettingsService settingsService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public FalsificationCycleService(ProjectRepository projectRepository,
                                     RoleRepository roleRepository,
                                     RoleCapabilityLoader roleCapabilityLoader,
                                     MLPredictionServiceClient mlPredictionServiceClient,
                                     WishlistRepository wishlistRepository,
                                     FalsificationRunRepository falsificationRunRepository,
                                     SystemSettingsService settingsService,
                                     org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.wishlistRepository = wishlistRepository;
        this.falsificationRunRepository = falsificationRunRepository;
        this.settingsService = settingsService;
        this.jdbcTemplate = jdbcTemplate;
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

    @Transactional
    public void executeCycleForProject(ProjectEntity project) {
        log.info("FalsificationCycleService: Running check for project {}", project.getName());

        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();

        int rolesCheckedCount = activeRoles.size(); // Should be 12
        int violationsFoundCount = 0;
        int followUpsCreatedCount = 0;

        String latestDiff = getLatestProjectDiff(project);

        for (RoleEntity role : activeRoles) {
            String roleTag = role.getTag();
            String refusalCriteria = "";
            try {
                com.eneik.production.dto.RoleRules rules = roleCapabilityLoader.loadRules(roleTag);
                if (rules != null) {
                    refusalCriteria = rules.refusalCriteria();
                }
            } catch (Exception e) {
                log.warn("FalsificationCycleService: Could not load rules for role {}: {}", roleTag, e.getMessage());
            }

            // 1. Refusal Criteria check
            if (refusalCriteria != null && !refusalCriteria.trim().isEmpty()) {
                Map<String, Object> rcResult = mlPredictionServiceClient.checkRefusalCriteria(latestDiff, refusalCriteria);
                boolean isCompliant = Boolean.TRUE.equals(rcResult.get("compliant"));
                String reason = String.valueOf(rcResult.get("reason"));

                // checkRefusalCriteria() deliberately fails toward compliant=false when the ML/Gemini
                // verification pipeline itself is unreachable (AutoMergeService relies on that to block a
                // merge it can't verify). But "we couldn't check" is not the same claim as "we found a real
                // violation" - treating it as one here would spawn a high-priority wishlist demanding a fix
                // for a regression that never happened, every time Gemini has an outage or hits quota.
                if (!isCompliant && reason.startsWith("VERIFICATION_SERVICE_UNAVAILABLE")) {
                    log.warn("FalsificationCycleService: Skipping refusal-criteria check for role {} in project {} - verification service unavailable, not treating as a violation",
                            roleTag, project.getName());
                } else if (!isCompliant) {
                    violationsFoundCount++;
                    log.warn("FalsificationCycleService: Code violation detected for role {} in project {}", roleTag, project.getName());

                    if (hasRecentFollowUp(project.getId(), roleTag)) {
                        log.info("FalsificationCycleService: Skipping duplicate self_falsification wishlist for role {} — a follow-up was already created within {}", roleTag, FOLLOWUP_SUPPRESSION_WINDOW);
                    } else {
                        // Create self_falsification wishlist item. Orchestrate converts it later via the single smart compiler path.
                        WishlistEntity wishlist = new WishlistEntity();
                        wishlist.setProjectId(project.getId());
                        wishlist.setSource(WishlistSource.self_falsification);
                        wishlist.setSourceRoleTag(roleTag);
                        wishlist.setContent("Compliance violation detected for role " + roleTag + ". Violates: " + rcResult.get("reason"));
                        wishlist.setStatus(WishlistStatus.pending);
                        wishlist.setLeanValue(LeanValue.essential);
                        wishlist.setTocConstraintRef("HIGH_PRIORITY_DEBT");
                        wishlist.setCompiledByRole("BARCAN-TAG-09");
                        wishlist.setJtbd("Fix role refusal criteria violation detected by falsification cycle");
                        wishlist.setSixSigmaMetric("falsification_run_rate");
                        wishlist.setDod("BARCAN-TAG-09: Falsification regression fixed");
                        wishlist.setAcceptanceCriteria("Refusal criteria check passes successfully");
                        wishlist = wishlistRepository.save(wishlist);
                        followUpsCreatedCount++;
                        log.info("FalsificationCycleService: Created self_falsification wishlist item {} for role {}", wishlist.getId(), roleTag);
                    }
                }
            }

            // 2. Methodological Falsification (Philosophical Critique) check
            String rawRules = readRawRules(role);
            if (rawRules != null && !rawRules.trim().isEmpty()) {
                List<Map<String, Object>> phResults = mlPredictionServiceClient.checkMethodologicalFalsification(latestDiff, rawRules);
                for (Map<String, Object> phRes : phResults) {
                    String status = (String) phRes.get("status");
                    if ("ПОДТВЕРЖДЕНО".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)) {
                        violationsFoundCount++;
                        log.warn("FalsificationCycleService: Methodological contradiction confirmed for role {} by philosopher {}: {}",
                                roleTag, phRes.get("philosopher"), phRes.get("thesis"));

                        if (hasRecentFollowUp(project.getId(), roleTag)) {
                            log.info("FalsificationCycleService: Skipping duplicate self_falsification wishlist for role {} — a follow-up was already created within {}", roleTag, FOLLOWUP_SUPPRESSION_WINDOW);
                            continue;
                        }

                        WishlistEntity wishlist = new WishlistEntity();
                        wishlist.setProjectId(project.getId());
                        wishlist.setSource(WishlistSource.self_falsification);
                        wishlist.setSourceRoleTag(roleTag);
                        
                        String philosopher = (String) phRes.get("philosopher");
                        String thesis = (String) phRes.get("thesis");
                        String mustBe = (String) phRes.get("must_be");
                        String performance = (String) phRes.get("performance");
                        String attractive = (String) phRes.get("attractive");
                        
                        String content = "Methodological contradiction confirmed by " + philosopher + ": " + thesis + "\n" +
                                         "Score: " + phRes.get("score") + "\n" +
                                         "[Must-Be]: " + mustBe + "\n" +
                                         "[Performance]: " + performance + "\n" +
                                         "[Attractive]: " + attractive;
                        
                        wishlist.setContent(content);
                        wishlist.setStatus(WishlistStatus.pending);
                        wishlist.setLeanValue(LeanValue.essential);
                        wishlist.setTocConstraintRef("HIGH_PRIORITY_DEBT");
                        wishlist.setCompiledByRole("BARCAN-TAG-09");
                        wishlist.setJtbd("Resolve methodological contradiction identified by " + philosopher);
                        wishlist.setSixSigmaMetric("falsification_run_rate");
                        wishlist.setDod("BARCAN-TAG-09: Falsification regression fixed");
                        wishlist.setAcceptanceCriteria("Given methodological contradiction by " + philosopher + ", When resolving, Then Must-Be requirement is fulfilled: " + mustBe);
                        wishlist = wishlistRepository.save(wishlist);
                        followUpsCreatedCount++;
                        log.info("FalsificationCycleService: Created self_falsification wishlist item {} for methodological falsification by {}", wishlist.getId(), philosopher);
                    }
                }
            }
        }

        FalsificationRunEntity run = new FalsificationRunEntity();
        run.setProjectId(project.getId());
        run.setRunAt(Instant.now());
        run.setRolesCheckedCount(rolesCheckedCount);
        run.setViolationsFoundCount(violationsFoundCount);
        run.setTasksCreatedCount(0);
        falsificationRunRepository.save(run);

        log.info("FalsificationCycleService: Completed check for project {}. Checked roles: {}, Violations: {}, Follow-up wishlist items created: {}",
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

    private String getLatestProjectDiff(ProjectEntity project) {
        StringBuilder logs = new StringBuilder();
        if (project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
            java.io.File workspaceDir = new java.io.File(project.getWorkspacePath());
            if (workspaceDir.exists() && workspaceDir.isDirectory()) {
                // read mvn-clean-test.log
                java.nio.file.Path mvnLog = java.nio.file.Paths.get(project.getWorkspacePath(), "mvn-clean-test.log");
                if (java.nio.file.Files.exists(mvnLog)) {
                    try {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(mvnLog);
                        int start = Math.max(0, lines.size() - 200);
                        logs.append("\n\n--- MVN CLEAN TEST LOG (LAST 200 LINES) ---\n");
                        for (int i = start; i < lines.size(); i++) {
                            logs.append(lines.get(i)).append("\n");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read mvn log: " + e.getMessage());
                    }
                }
                // read frontend-test.log
                java.nio.file.Path feLog = java.nio.file.Paths.get(project.getWorkspacePath(), "frontend-test.log");
                if (java.nio.file.Files.exists(feLog)) {
                    try {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(feLog);
                        int start = Math.max(0, lines.size() - 200);
                        logs.append("\n\n--- FRONTEND TEST LOG (LAST 200 LINES) ---\n");
                        for (int i = start; i < lines.size(); i++) {
                            logs.append(lines.get(i)).append("\n");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read frontend log: " + e.getMessage());
                    }
                }
            }
        }

        String diff = "mock_diff";
        // If workspacePath is set and exists, try to get actual git diff
        if (project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
            java.io.File workspaceDir = new java.io.File(project.getWorkspacePath());
            if (workspaceDir.exists() && workspaceDir.isDirectory()) {
                try {
                    // Get diff against HEAD~1 or origin/main
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
                        log.info("FalsificationCycleService: Retrieved actual Git diff for project {}", project.getName());
                        diff = diffSb.toString();
                    }
                } catch (Exception e) {
                    log.warn("FalsificationCycleService: Failed to retrieve Git diff from workspace {}: {}",
                            project.getWorkspacePath(), e.getMessage());
                }
            }
        }

        if ("mock_diff".equals(diff)) {
            // Fallback to database query:
            try {
                List<String> diffs = jdbcTemplate.query(
                    "SELECT r.diff_summary FROM pr_reviews r " +
                    "JOIN jules_sessions s ON r.jules_session_id = s.id " +
                    "JOIN tasks t ON s.task_id = t.id " +
                    "WHERE t.project_id = ? " +
                    "ORDER BY r.created_at DESC LIMIT 1",
                    (rs, rowNum) -> rs.getString("diff_summary"),
                    project.getId()
                );
                if (!diffs.isEmpty() && diffs.get(0) != null) {
                    diff = diffs.get(0);
                }
            } catch (Exception e) {
                log.warn("FalsificationCycleService: Failed to query latest project diff: {}", e.getMessage());
            }
        }

        java.util.List<String> recentActivity = com.eneik.production.services.logging.LogScopeBuffer.recent(project.getId(), 60);
        if (!recentActivity.isEmpty()) {
            logs.append("\n\n--- RECENT PROJECT OPERATIONAL ACTIVITY (last ").append(recentActivity.size()).append(" scoped log lines) ---\n");
            for (String line : recentActivity) {
                logs.append(line).append("\n");
            }
        }

        return diff + logs.toString();
    }
}
