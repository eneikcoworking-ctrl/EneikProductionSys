package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.dashboard.AcceptanceReadinessDto;
import com.eneik.production.dto.dashboard.CommandDashboardDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/projects/{projectId}/command-dashboard")
public class CommandDashboardController {

    private final JdbcTemplate jdbcTemplate;

    public CommandDashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public CommandDashboardDto getDashboard(@PathVariable UUID projectId) {
        Map<String, String> dataSourcesStatus = new HashMap<>();

        List<Map<String, Object>> wishlist = fetchData(projectId, "wishlist", dataSourcesStatus);
        List<Map<String, Object>> tasks = fetchData(projectId, "tasks", dataSourcesStatus);
        List<Map<String, Object>> julesSessions = fetchData(projectId, "jules_sessions", dataSourcesStatus);
        List<Map<String, Object>> prReviews = fetchData(projectId, "pr_reviews", dataSourcesStatus);
        List<Map<String, Object>> githubAccessStatus = fetchData(projectId, "github_access_status", dataSourcesStatus);
        List<Map<String, Object>> linearIssueMetadata = fetchData(projectId, "linear_issue_metadata", dataSourcesStatus);

        AcceptanceReadinessDto readiness = calculateReadiness(projectId, tasks, prReviews, githubAccessStatus, dataSourcesStatus);

        return new CommandDashboardDto(
            wishlist, tasks, julesSessions, prReviews, githubAccessStatus, linearIssueMetadata,
            readiness, dataSourcesStatus
        );
    }

    private List<Map<String, Object>> fetchData(UUID projectId, String tableName, Map<String, String> statusMap) {
        if (!tableExists(tableName)) {
            statusMap.put(tableName, "data source not yet available");
            return null;
        }
        try {
            // Using project_id for filtering where applicable.
            // Note: some tables might not have project_id, but the task says to fetch for the project.
            // I'll check if project_id column exists.
            if (columnExists(tableName, "project_id")) {
                return jdbcTemplate.queryForList("SELECT * FROM " + tableName + " WHERE project_id = ?", projectId);
            } else if (tableName.equals("tasks")) {
                 return jdbcTemplate.queryForList("SELECT * FROM tasks WHERE project_id = ?", projectId);
            } else {
                // Fallback for tables without explicit project link in schema provided but requested in context
                return jdbcTemplate.queryForList("SELECT * FROM " + tableName);
            }
        } catch (Exception e) {
            statusMap.put(tableName, "error: " + e.getMessage());
            return null;
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = ? OR table_name = ?",
            Integer.class, tableName.toLowerCase(), tableName.toUpperCase()
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE (table_name = ? OR table_name = ?) AND (column_name = ? OR column_name = ?)",
            Integer.class, tableName.toLowerCase(), tableName.toUpperCase(), columnName.toLowerCase(), columnName.toUpperCase()
        );
        return count != null && count > 0;
    }

    private AcceptanceReadinessDto calculateReadiness(UUID projectId,
                                                    List<Map<String, Object>> tasks,
                                                    List<Map<String, Object>> prReviews,
                                                    List<Map<String, Object>> githubAccess,
                                                    Map<String, String> dataSourcesStatus) {

        Boolean allTasksDone = null;
        Boolean allQualityGatesPassed = null;
        Boolean allPrsMerged = null;
        Boolean githubAccessHealthy = null;
        List<String> unmetConditions = new ArrayList<>();

        // Tasks readiness
        if (dataSourcesStatus.containsKey("tasks")) {
            allTasksDone = null;
            allQualityGatesPassed = null;
        } else if (tasks != null) {
            allTasksDone = tasks.stream().noneMatch(t -> {
                String status = (String) t.get("status");
                return !Set.of("done", "review").contains(status.toLowerCase());
            });
            allQualityGatesPassed = tasks.stream().allMatch(t -> Boolean.TRUE.equals(t.get("quality_gate_passed")));
        }

        // PRs readiness
        if (dataSourcesStatus.containsKey("pr_reviews")) {
            allPrsMerged = null;
        } else if (prReviews != null) {
            allPrsMerged = prReviews.stream().noneMatch(pr -> {
                String ciStatus = (String) pr.get("ci_status");
                return ciStatus != null && Set.of("pending", "failing").contains(ciStatus.toLowerCase());
            });
        }

        // GitHub access
        if (dataSourcesStatus.containsKey("github_access_status")) {
            githubAccessHealthy = null;
        } else if (githubAccess != null && !githubAccess.isEmpty()) {
            Map<String, Object> lastStatus = githubAccess.get(githubAccess.size() - 1);
            githubAccessHealthy = !Boolean.FALSE.equals(lastStatus.get("has_repo_access")) && !Boolean.FALSE.equals(lastStatus.get("ci_status"));
        }

        if (Boolean.FALSE.equals(allTasksDone)) unmetConditions.add("Some tasks are not done or in review");
        if (Boolean.FALSE.equals(allQualityGatesPassed)) unmetConditions.add("Some quality gates failed");
        if (Boolean.FALSE.equals(allPrsMerged)) unmetConditions.add("Some PRs have pending or failing CI");
        if (Boolean.FALSE.equals(githubAccessHealthy)) unmetConditions.add("GitHub access or CI is unhealthy");

        String readiness;
        if (allTasksDone == null || allQualityGatesPassed == null || allPrsMerged == null || githubAccessHealthy == null) {
            readiness = "unknown";
        } else if (allTasksDone && allQualityGatesPassed && allPrsMerged && githubAccessHealthy) {
            readiness = "ready";
        } else {
            readiness = "not ready";
        }

        return new AcceptanceReadinessDto(readiness, allTasksDone, allQualityGatesPassed, allPrsMerged, githubAccessHealthy, unmetConditions);
    }
}
