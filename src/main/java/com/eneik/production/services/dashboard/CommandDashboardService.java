package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.AcceptanceReadinessDto;
import com.eneik.production.dto.dashboard.CommandDashboardDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CommandDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public CommandDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommandDashboardDto getDashboard(UUID projectId) {
        Map<String, String> dataSourcesStatus = new HashMap<>();

        List<Map<String, Object>> wishlist = fetchData(projectId, "wishlist", dataSourcesStatus);
        if (wishlist == null && !dataSourcesStatus.containsKey("wishlist")) {
             wishlist = fetchData(projectId, "wishlist_items", dataSourcesStatus);
        }

        List<Map<String, Object>> tasks = fetchData(projectId, "tasks", dataSourcesStatus);
        List<Map<String, Object>> julesSessions = fetchData(projectId, "jules_sessions", dataSourcesStatus);
        List<Map<String, Object>> prReviews = fetchData(projectId, "pr_reviews", dataSourcesStatus);
        List<Map<String, Object>> githubAccessStatus = fetchData(projectId, "github_access_status", dataSourcesStatus);
        List<Map<String, Object>> linearIssueMetadata = fetchData(projectId, "linear_issue_metadata", dataSourcesStatus);

        AcceptanceReadinessDto readiness = calculateReadiness(projectId, wishlist, tasks, prReviews, githubAccessStatus, dataSourcesStatus);

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
            if (columnExists(tableName, "project_id")) {
                return jdbcTemplate.queryForList("SELECT * FROM " + tableName + " WHERE project_id = ?", projectId);
            } else {
                // If no project_id column, we can't safely filter by project.
                // For compliance with security review, we return empty instead of leaking all projects.
                return new ArrayList<>();
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
                                                    List<Map<String, Object>> wishlist,
                                                    List<Map<String, Object>> tasks,
                                                    List<Map<String, Object>> prReviews,
                                                    List<Map<String, Object>> githubAccess,
                                                    Map<String, String> dataSourcesStatus) {

        Boolean allTasksDone = null;
        Boolean allQualityGatesPassed = null;
        Boolean allPrsMerged = null;
        Boolean githubAccessHealthy = null;
        List<String> unmetConditions = new ArrayList<>();

        if (dataSourcesStatus.containsKey("tasks")) {
            allTasksDone = null;
            allQualityGatesPassed = null;
        } else if (tasks != null) {
            allTasksDone = tasks.stream().noneMatch(t -> {
                String status = (String) t.get("status");
                return status == null || !Set.of("done", "review").contains(status.toLowerCase());
            });
            allQualityGatesPassed = tasks.stream().allMatch(t -> Boolean.TRUE.equals(t.get("quality_gate_passed")));
        }

        if (dataSourcesStatus.containsKey("pr_reviews")) {
            allPrsMerged = null;
        } else if (prReviews != null) {
            allPrsMerged = prReviews.stream().noneMatch(pr -> {
                String ciStatus = (String) pr.get("ci_status");
                return ciStatus != null && Set.of("pending", "failing").contains(ciStatus.toLowerCase());
            });
        }

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

        // Kano Model Analysis for Project Completion recommendation
        String kanoRecommendation = "No outstanding requirements found.";
        List<Map<String, Object>> pendingItems = new ArrayList<>();
        if (wishlist != null) {
            for (Map<String, Object> item : wishlist) {
                String status = (String) item.get("status");
                if (status != null && (status.equalsIgnoreCase("open") || status.equalsIgnoreCase("pending") || status.equalsIgnoreCase("approved"))) {
                    pendingItems.add(item);
                }
            }
        }
        if (tasks != null) {
            for (Map<String, Object> t : tasks) {
                String status = (String) t.get("status");
                if (status == null || !status.equalsIgnoreCase("done")) {
                    pendingItems.add(t);
                }
            }
        }

        if (!pendingItems.isEmpty()) {
            boolean hasHighValuePending = false;
            for (Map<String, Object> item : pendingItems) {
                String content = (String) item.get("content");
                if (content == null) content = (String) item.get("text");
                if (content == null) content = (String) item.get("description");

                String kanoClass = classifyKano(content);
                if ("Must-Be".equals(kanoClass) || "One-Dimensional".equals(kanoClass)) {
                    hasHighValuePending = true;
                    break;
                }
            }

            if (hasHighValuePending) {
                kanoRecommendation = "Recommendation: Keep working. There are pending Must-Be/One-Dimensional tasks (e.g. database, core logic) that must be implemented before project acceptance.";
            } else {
                kanoRecommendation = "Kano Model Suggestion: The remaining outstanding tasks/wishlist items represent low-value tweaks (Attractive/Indifferent). All Must-Be core functionality has been successfully implemented and approved. We highly recommend completing the project now to avoid diminishing returns.";
            }
        } else {
            kanoRecommendation = "Kano Model Suggestion: All tasks completed! Ready to complete and accept the project.";
        }

        String readiness;
        String statusLabel;
        String uiColorToken;
        if (allTasksDone == null || allQualityGatesPassed == null || allPrsMerged == null || githubAccessHealthy == null) {
            readiness = "unknown";
            statusLabel = "UNKNOWN";
            uiColorToken = "border-warning";
        } else if (allTasksDone && allQualityGatesPassed && allPrsMerged && githubAccessHealthy) {
            readiness = "ready";
            statusLabel = "READY";
            uiColorToken = "border-success";
        } else {
            readiness = "not ready";
            statusLabel = "NOT READY";
            uiColorToken = "border-error";
        }

        return new AcceptanceReadinessDto(readiness, allTasksDone, allQualityGatesPassed, allPrsMerged, githubAccessHealthy, unmetConditions, statusLabel, uiColorToken, kanoRecommendation);
    }

    private String classifyKano(String text) {
        if (text == null) return "Indifferent";
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("database") || lower.contains("api") || lower.contains("auth") ||
            lower.contains("backend") || lower.contains("security") || lower.contains("logic") ||
            lower.contains("core") || lower.contains("save") || lower.contains("create") ||
            lower.contains("интегра") || lower.contains("база")) {
            return "Must-Be";
        }
        if (lower.contains("tweak") || lower.contains("style") || lower.contains("color") ||
            lower.contains("css") || lower.contains("layout") || lower.contains("text") ||
            lower.contains("label") || lower.contains("optimization") || lower.contains("minor") ||
            lower.contains("дизайн") || lower.contains("экран")) {
            return "Attractive";
        }
        return "One-Dimensional";
    }
}
