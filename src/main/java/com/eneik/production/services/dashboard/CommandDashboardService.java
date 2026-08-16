package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.AcceptanceReadinessDto;
import com.eneik.production.dto.dashboard.CommandDashboardDto;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.verdict.Verdict;
import com.eneik.production.services.verdict.VerdictGate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CommandDashboardService {

    private final JdbcTemplate jdbcTemplate;

    // Both nullable so the dashboard keeps working exactly as before in any context that does not wire
    // them - the gate is an addition to what is reported, never a new requirement for reporting at all.
    private final ProjectRepository projectRepository;
    private final VerdictGate verdictGate;

    public CommandDashboardService(JdbcTemplate jdbcTemplate,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   ProjectRepository projectRepository,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   VerdictGate verdictGate) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectRepository = projectRepository;
        this.verdictGate = verdictGate;
    }

    public CommandDashboardDto getDashboard(UUID projectId) {
        Map<String, String> dataSourcesStatus = new HashMap<>();

        List<Map<String, Object>> wishlist = fetchData(projectId, "wishlist", dataSourcesStatus);
        if (wishlist == null && !dataSourcesStatus.containsKey("wishlist")) {
             wishlist = fetchData(projectId, "wishlist_items", dataSourcesStatus);
        }

        List<Map<String, Object>> tasks = fetchData(projectId, "tasks", dataSourcesStatus);
        List<Map<String, Object>> julesSessions = fetchJulesSessions(projectId, dataSourcesStatus);
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
                return lowercaseKeys(jdbcTemplate.queryForList("SELECT * FROM " + tableName + " WHERE project_id = ?", projectId));
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

    // Phase fix (2026-07-24): jules_sessions has no project_id column of its own - only task_id (confirmed via
    // JulesSessionEntity, not via the debug SQL endpoint - that endpoint is deliberately disabled by
    // default, see project_eneik_deferred_backlog memory). The generic fetchData() correctly refuses to
    // leak all projects when a table has no project_id column, which meant this dashboard's julesSessions
    // array was permanently empty for every project regardless of real session activity. jules_sessions
    // only reaches a project through tasks.project_id, so join through it instead of routing this table
    // through the generic helper.
    private List<Map<String, Object>> fetchJulesSessions(UUID projectId, Map<String, String> statusMap) {
        if (!tableExists("jules_sessions") || !tableExists("tasks")) {
            statusMap.put("jules_sessions", "data source not yet available");
            return null;
        }
        try {
            return lowercaseKeys(jdbcTemplate.queryForList(
                    "SELECT js.* FROM jules_sessions js JOIN tasks t ON js.task_id = t.id WHERE t.project_id = ?",
                    projectId));
        } catch (Exception e) {
            statusMap.put("jules_sessions", "error: " + e.getMessage());
            return null;
        }
    }

    // Phase follow-up (2026-07-21, found via a live API check against test-twenty-ninth while chasing the
    // system-task-pollution fix): H2's JdbcTemplate.queryForList returns column keys in the driver's
    // native case (confirmed live: "STATUS", "QUALITY_GATE_PASSED", "CONTENT", not the lowercase
    // snake_case calculateReadiness()/classifyKano() actually look up). That mismatch meant every
    // t.get("status")/t.get("quality_gate_passed")/etc. call silently returned null - the Acceptance
    // Readiness card had been unconditionally reporting "not ready"/"unknown" for every project
    // regardless of real task state, since dataSourcesStatus wasn't set (so the code assumed "healthy
    // data, computed false/null" rather than "lookup failed"). Normalized once here, at the single
    // choke point all raw rows pass through, rather than patching every read site.
    private static List<Map<String, Object>> lowercaseKeys(List<Map<String, Object>> rows) {
        List<Map<String, Object>> normalized = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> lower = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                lower.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
            normalized.add(lower);
        }
        return normalized;
    }

    // Same marker JulesDispatchService/ProjectFlowService use to route compiler/review-fallback/audit
    // carrier tasks around the normal pipeline (payload.taskType). Raw JDBC returns the JSON payload
    // column as byte[] (confirmed live: base64 in the HTTP response), so it's decoded to text and
    // pattern-matched rather than parsed as structured JSON - consistent with this file's existing
    // string-heuristic style (see classifyKano) and avoids pulling a JSON parser into a class that
    // otherwise works purely off raw JDBC maps.
    private static final Set<String> SYSTEM_META_TASK_TYPES = Set.of(
            "wishlist_compiler", "falsification_audit", "pr_review_fallback", "design_review", "coverage_audit"
    );

    private boolean isSystemMetaTaskRow(Map<String, Object> task) {
        Object payload = task.get("payload");
        String text;
        if (payload instanceof byte[] bytes) {
            text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else if (payload != null) {
            text = payload.toString();
        } else {
            return false;
        }
        for (String type : SYSTEM_META_TASK_TYPES) {
            if (text.contains("\"taskType\":\"" + type + "\"")) {
                return true;
            }
        }
        return false;
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

        // Phase follow-up (2026-07-21): system/carrier tasks (compiler, review-fallback, audit, design review)
        // never produce user-facing work, so a failed/incomplete one shouldn't flip "acceptance readiness"
        // to not-ready - confirmed live on test-twenty-ninth, where 9 failed pr_review_fallback carrier
        // tasks were the sole reason allTasksDone/allQualityGatesPassed read false.
        List<Map<String, Object>> realWorkTasks = tasks == null ? null
                : tasks.stream().filter(t -> !isSystemMetaTaskRow(t)).toList();

        if (dataSourcesStatus.containsKey("tasks")) {
            allTasksDone = null;
            allQualityGatesPassed = null;
        } else if (realWorkTasks != null) {
            allTasksDone = realWorkTasks.stream().noneMatch(t -> {
                String status = (String) t.get("status");
                return status == null || !Set.of("done", "review").contains(status.toLowerCase());
            });
            allQualityGatesPassed = realWorkTasks.stream().allMatch(t -> Boolean.TRUE.equals(t.get("quality_gate_passed")));
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
        if (realWorkTasks != null) {
            for (Map<String, Object> t : realWorkTasks) {
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

        // Step 18. These four conditions were already being combined in three values - `unknown` when one
        // is unmeasurable, `not ready` when one fails, `ready` only when all hold - which is Kleene
        // conjunction written out by hand and never named. Naming it lets the verdict lattice join as one
        // more conjunct instead of being bolted on beside it:
        //
        //     report(P) = construction(P) ∧ ⋀ verdict_layer(P)
        //
        // Every one of these four is about CONSTRUCTION - tasks done, gates passed, PRs merged, GitHub
        // reachable. None is about the product running or having been shown to anyone, which is why
        // `ready` could be reached on merge counts alone (F1, F30).
        Verdict construction;
        if (allTasksDone == null || allQualityGatesPassed == null || allPrsMerged == null || githubAccessHealthy == null) {
            construction = Verdict.ABSTAIN;
        } else if (allTasksDone && allQualityGatesPassed && allPrsMerged && githubAccessHealthy) {
            construction = Verdict.PERMIT;
        } else {
            construction = Verdict.WITHHOLD;
        }

        // Off by default and scoped to one project; when it declines to act the verdict is returned
        // untouched, so the reported values stay byte-for-byte what they were before this existed.
        VerdictGate.Decision decision = verdictGate == null
                ? new VerdictGate.Decision(construction, false, List.of())
                : verdictGate.constrain(
                        projectRepository == null ? null : projectRepository.findById(projectId).orElse(null),
                        construction);
        if (decision.applied()) {
            unmetConditions.addAll(decision.reasons());
        }

        String readiness;
        String statusLabel;
        String uiColorToken;
        switch (decision.verdict()) {
            case PERMIT -> {
                readiness = "ready";
                statusLabel = "READY";
                uiColorToken = "border-success";
            }
            case WITHHOLD -> {
                readiness = "not ready";
                statusLabel = "NOT READY";
                uiColorToken = "border-error";
            }
            // ABSTAIN keeps the existing "unknown" wording, and that mapping is the point rather than a
            // convenience: an unestablished claim is not a refuted one, and the two must not be shown the
            // same way to someone deciding whether to keep working or to accept.
            default -> {
                readiness = "unknown";
                statusLabel = "UNKNOWN";
                uiColorToken = "border-warning";
            }
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
