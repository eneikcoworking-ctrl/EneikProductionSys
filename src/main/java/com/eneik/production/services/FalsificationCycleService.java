package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
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

    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final WishlistRepository wishlistRepository;
    private final TechnicalLeadCompiler technicalLeadCompiler;
    private final FalsificationRunRepository falsificationRunRepository;
    private final SystemSettingsService settingsService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public FalsificationCycleService(ProjectRepository projectRepository,
                                     RoleRepository roleRepository,
                                     RoleCapabilityLoader roleCapabilityLoader,
                                     MLPredictionServiceClient mlPredictionServiceClient,
                                     WishlistRepository wishlistRepository,
                                     TechnicalLeadCompiler technicalLeadCompiler,
                                     FalsificationRunRepository falsificationRunRepository,
                                     SystemSettingsService settingsService,
                                     org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.wishlistRepository = wishlistRepository;
        this.technicalLeadCompiler = technicalLeadCompiler;
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

    @Transactional
    public void executeCycleForProject(ProjectEntity project) {
        log.info("FalsificationCycleService: Running check for project {}", project.getName());

        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();

        int rolesCheckedCount = activeRoles.size(); // Should be 12
        int violationsFoundCount = 0;
        int tasksCreatedCount = 0;

        // Check if there is a critical regression
        boolean hasCriticalRegression = !checkActuatorHealth(project);

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

            if (refusalCriteria != null && !refusalCriteria.trim().isEmpty()) {
                Map<String, Object> rcResult = mlPredictionServiceClient.checkRefusalCriteria(latestDiff, refusalCriteria);
                boolean isCompliant = Boolean.TRUE.equals(rcResult.get("compliant"));

                if (!isCompliant) {
                    violationsFoundCount++;
                    log.warn("FalsificationCycleService: Code violation detected for role {} in project {}", roleTag, project.getName());

                    if (hasCriticalRegression) {
                        if (tasksCreatedCount < 2) {
                            // Create self_falsification wishlist item
                            WishlistEntity wishlist = new WishlistEntity();
                            wishlist.setProjectId(project.getId());
                            wishlist.setSource(WishlistSource.self_falsification);
                            wishlist.setSourceRoleTag(roleTag);
                            wishlist.setContent("Critical regression self-falsification cleanup required for role " + roleTag + ". Violates: " + rcResult.get("reason"));
                            wishlist.setStatus(WishlistStatus.pending);
                            wishlist.setLeanValue(LeanValue.essential);
                            wishlist.setTocConstraintRef("HIGH_PRIORITY_DEBT");
                            wishlist.setCompiledByRole("BARCAN-TAG-09");
                            wishlist.setJtbd("Fix critical regression detected by falsification cycle");
                            wishlist.setSixSigmaMetric("falsification_run_rate");
                            wishlist.setDod("BARCAN-TAG-09: Falsification regression fixed");
                            wishlist.setAcceptanceCriteria("Actuator health returns to UP");
                            wishlist = wishlistRepository.save(wishlist);

                            // Compile to chaotic task
                            try {
                                technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
                                tasksCreatedCount++;
                                log.info("FalsificationCycleService: Created chaotic task from self_falsification wishlist item for role {}", roleTag);
                            } catch (Exception e) {
                                log.error("FalsificationCycleService: Failed to compile wishlist item to task: {}", e.getMessage(), e);
                            }
                        } else {
                            log.info("FalsificationCycleService: Rate limit of 2 tasks per run reached, skipping task creation for role {}", roleTag);
                        }
                    } else {
                        log.info("FalsificationCycleService: Violation found for role {} but no critical regression confirmed, skipping task creation.", roleTag);
                    }
                }
            }
        }

        FalsificationRunEntity run = new FalsificationRunEntity();
        run.setProjectId(project.getId());
        run.setRunAt(Instant.now());
        run.setRolesCheckedCount(rolesCheckedCount);
        run.setViolationsFoundCount(violationsFoundCount);
        run.setTasksCreatedCount(tasksCreatedCount);
        falsificationRunRepository.save(run);

        log.info("FalsificationCycleService: Completed check for project {}. Checked roles: {}, Violations: {}, Tasks created: {}",
                project.getName(), rolesCheckedCount, violationsFoundCount, tasksCreatedCount);
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
                return diffs.get(0);
            }
        } catch (Exception e) {
            log.warn("FalsificationCycleService: Failed to query latest project diff: {}", e.getMessage());
        }
        return "mock_diff";
    }
}
