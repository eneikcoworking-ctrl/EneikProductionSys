package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.EmsDashboardMetricsDto;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmsMetricsService {

    public EmsDashboardMetricsDto build(List<TaskEntity> tasks) {
        List<TaskEntity> safeTasks = tasks == null ? List.of() : tasks;
        return new EmsDashboardMetricsDto(
                Instant.now().toString(),
                flowChart(safeTasks),
                roleKpis(safeTasks),
                defectWork(safeTasks),
                graphHealth(safeTasks),
                List.of(
                        "Role KPI is calculated from task status, quality gate state, retry load, and defect work.",
                        "Defect work includes failed, blocked, retried, and circuit-breaker recovery tasks.",
                        "Graph health uses EMS payload keys plus dependsOn edges; lower duplicate semantic keys means cleaner orchestration.",
                        "A queued task with an unfinished dependsOn task is intentionally blocked by the dependency graph, not by missing Jules capability."
                )
        );
    }

    private EmsDashboardMetricsDto.FlowChart flowChart(List<TaskEntity> tasks) {
        Map<String, List<TaskEntity>> byStage = tasks.stream()
                .collect(Collectors.groupingBy(this::flowStage, LinkedHashMap::new, Collectors.toList()));
        List<EmsDashboardMetricsDto.FlowStage> stages = new ArrayList<>();
        for (String stage : orderedStages()) {
            List<TaskEntity> stageTasks = byStage.getOrDefault(stage, List.of());
            if (stageTasks.isEmpty()) {
                continue;
            }
            long total = stageTasks.size();
            long done = stageTasks.stream().filter(this::isDoneLike).count();
            long queued = stageTasks.stream().filter(task -> task.getStatus() == TaskStatus.queued).count();
            long active = stageTasks.stream().filter(this::isActiveLike).count();
            long blocked = stageTasks.stream().filter(this::isBlockedLike).count();
            double completion = ratio(done, total);
            double weighted = weightedProgress(stageTasks);
            stages.add(new EmsDashboardMetricsDto.FlowStage(
                    stage,
                    stageLabel(stage),
                    total,
                    queued,
                    active,
                    done,
                    blocked,
                    round(completion),
                    round(weighted)
            ));
        }

        long totalTasks = tasks.size();
        long done = tasks.stream().filter(this::isDoneLike).count();
        return new EmsDashboardMetricsDto.FlowChart(
                stages,
                totalTasks,
                round(ratio(done, totalTasks)),
                round(weightedProgress(tasks))
        );
    }

    private List<EmsDashboardMetricsDto.RoleKpi> roleKpis(List<TaskEntity> tasks) {
        Map<String, List<TaskEntity>> byRole = tasks.stream()
                .collect(Collectors.groupingBy(this::roleTag, LinkedHashMap::new, Collectors.toList()));
        return byRole.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> roleKpi(entry.getKey(), entry.getValue()))
                .toList();
    }

    private EmsDashboardMetricsDto.RoleKpi roleKpi(String roleTag, List<TaskEntity> tasks) {
        long total = tasks.size();
        long queued = tasks.stream().filter(task -> task.getStatus() == TaskStatus.queued).count();
        long active = tasks.stream().filter(this::isActiveLike).count();
        long done = tasks.stream().filter(this::isDoneLike).count();
        long blocked = tasks.stream().filter(task -> task.getStatus() == TaskStatus.blocked).count();
        long failed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.failed).count();
        long defectWork = tasks.stream().filter(this::isDefectWork).count();
        long retryLoad = tasks.stream().mapToLong(task -> Math.max(0, task.getRetryCount())).sum();
        long gated = tasks.stream().filter(task -> task.isQualityGatePassed() || isDoneLike(task) || isBlockedLike(task)).count();
        long gatePassed = tasks.stream().filter(TaskEntity::isQualityGatePassed).count();

        double completion = ratio(done, total);
        double gatePassRate = gated == 0 ? 1.0 : ratio(gatePassed, gated);
        double defectPressure = defectPressure(tasks);
        double flowEfficiency = ratio(done, done + active + blocked + failed);
        double kpiScore = clamp01(
                completion * 0.42
                        + gatePassRate * 0.23
                        + flowEfficiency * 0.20
                        + (1.0 - defectPressure) * 0.15
        ) * 100.0;
        double target = kpiTarget(roleTag);

        return new EmsDashboardMetricsDto.RoleKpi(
                roleTag,
                total,
                queued,
                active,
                done,
                blocked,
                failed,
                defectWork,
                retryLoad,
                round(completion),
                round(gatePassRate),
                round(defectPressure),
                round(flowEfficiency),
                round(kpiScore),
                target,
                statusLabel(kpiScore, target, defectPressure, blocked + failed)
        );
    }

    private EmsDashboardMetricsDto.DefectWork defectWork(List<TaskEntity> tasks) {
        long totalDefectWork = tasks.stream().filter(this::isDefectWork).count();
        long openDefectWork = tasks.stream()
                .filter(this::isDefectWork)
                .filter(task -> !isDoneLike(task))
                .count();
        long blocked = tasks.stream().filter(task -> task.getStatus() == TaskStatus.blocked).count();
        long failed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.failed).count();
        long retryLoad = tasks.stream().mapToLong(task -> Math.max(0, task.getRetryCount())).sum();
        double pressure = defectPressure(tasks);
        double dpmo = tasks.isEmpty() ? 0.0 : ((double) (totalDefectWork + retryLoad) / tasks.size()) * 1_000_000.0;

        String interpretation = openDefectWork == 0
                ? "No open defect-work load is visible in the selected project."
                : "Open defect-work load exists; prioritize recovery tasks before expanding feature scope.";

        return new EmsDashboardMetricsDto.DefectWork(
                totalDefectWork,
                openDefectWork,
                blocked,
                failed,
                retryLoad,
                round(pressure),
                round(dpmo),
                interpretation
        );
    }

    private EmsDashboardMetricsDto.GraphHealth graphHealth(List<TaskEntity> tasks) {
        long graphTasks = tasks.stream().filter(task -> !payloadText(task, "ems_graph_key").isBlank()).count();
        long uniqueGraphs = tasks.stream()
                .map(task -> payloadText(task, "ems_graph_key"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet())
                .size();
        long linkedEdges = tasks.stream().filter(task -> task.getDependsOn() != null).count();
        long blockedByDependency = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.queued)
                .filter(task -> task.getDependsOn() != null && !isDoneLike(task.getDependsOn()))
                .count();
        long duplicateSemanticKeys = duplicateSemanticKeys(tasks);
        double graphCoverage = ratio(graphTasks, tasks.size());
        double dependencyCoverage = graphTasks <= uniqueGraphs ? 0.0 : ratio(linkedEdges, graphTasks - uniqueGraphs);
        int criticalPathLength = criticalPathLength(tasks);

        String interpretation;
        if (tasks.isEmpty()) {
            interpretation = "No tasks exist yet.";
        } else if (duplicateSemanticKeys > 0) {
            interpretation = "Duplicate semantic keys are present; orchestration should collapse or skip repeated work.";
        } else if (graphCoverage < 0.5) {
            interpretation = "Many tasks were created before EMS graph metadata; new orchestration will be more reliable.";
        } else {
            interpretation = "EMS graph metadata is present and can drive dependency-aware dispatch.";
        }

        return new EmsDashboardMetricsDto.GraphHealth(
                graphTasks,
                uniqueGraphs,
                linkedEdges,
                blockedByDependency,
                duplicateSemanticKeys,
                round(graphCoverage),
                round(dependencyCoverage),
                criticalPathLength,
                interpretation
        );
    }

    private long duplicateSemanticKeys(List<TaskEntity> tasks) {
        Map<String, Long> counts = tasks.stream()
                .map(task -> payloadText(task, "ems_semantic_key"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.values().stream().filter(count -> count > 1).mapToLong(count -> count - 1).sum();
    }

    private int criticalPathLength(List<TaskEntity> tasks) {
        Map<UUID, TaskEntity> byId = tasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity(), (a, b) -> a));
        Map<UUID, Integer> memo = new HashMap<>();
        int max = 0;
        for (TaskEntity task : tasks) {
            max = Math.max(max, depth(task, byId, memo, new HashSet<>()));
        }
        return max;
    }

    private int depth(TaskEntity task, Map<UUID, TaskEntity> byId, Map<UUID, Integer> memo, Set<UUID> visiting) {
        if (task == null || task.getId() == null) {
            return 0;
        }
        if (memo.containsKey(task.getId())) {
            return memo.get(task.getId());
        }
        if (!visiting.add(task.getId())) {
            return 1;
        }
        int depth = 1;
        TaskEntity dependency = task.getDependsOn();
        if (dependency != null && dependency.getId() != null) {
            TaskEntity known = byId.getOrDefault(dependency.getId(), dependency);
            depth = 1 + depth(known, byId, memo, visiting);
        }
        visiting.remove(task.getId());
        memo.put(task.getId(), depth);
        return depth;
    }

    private double weightedProgress(List<TaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return 0.0;
        }
        double totalWeight = 0.0;
        double earned = 0.0;
        for (TaskEntity task : tasks) {
            double weight = payloadNumber(task, "ems_kpi_weight", 1.0);
            totalWeight += weight;
            earned += weight * statusProgress(task);
        }
        return totalWeight == 0.0 ? 0.0 : earned / totalWeight;
    }

    private double statusProgress(TaskEntity task) {
        TaskStatus status = task.getStatus();
        if (status == TaskStatus.done || status == TaskStatus.spike_completed) {
            return 1.0;
        }
        if (status == TaskStatus.review) {
            return 0.75;
        }
        if (status == TaskStatus.in_progress) {
            return 0.55;
        }
        if (status == TaskStatus.claimed) {
            return 0.35;
        }
        if (status == TaskStatus.blocked) {
            return 0.10;
        }
        if (status == TaskStatus.failed) {
            return 0.0;
        }
        return 0.15;
    }

    private double defectPressure(List<TaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return 0.0;
        }
        double pressure = 0.0;
        for (TaskEntity task : tasks) {
            if (task.getStatus() == TaskStatus.failed) {
                pressure += 1.0;
            } else if (task.getStatus() == TaskStatus.blocked) {
                pressure += 0.75;
            }
            pressure += Math.min(2, Math.max(0, task.getRetryCount())) * 0.25;
            if (payloadBoolean(task, "ems_defect_work")) {
                pressure += 0.35;
            }
        }
        return clamp01(pressure / tasks.size());
    }

    private boolean isDefectWork(TaskEntity task) {
        if (payloadBoolean(task, "ems_defect_work")) {
            return true;
        }
        if (task.getRetryCount() > 0) {
            return true;
        }
        if (task.getStatus() == TaskStatus.failed || task.getStatus() == TaskStatus.blocked) {
            return true;
        }
        String text = ((task.getDescription() == null ? "" : task.getDescription()) + " "
                + payloadText(task, "toc_constraint_ref")).toLowerCase(Locale.ROOT);
        return text.contains("defect")
                || text.contains("bug")
                || text.contains("blocker")
                || text.contains("recovery")
                || text.contains("circuit breaker")
                || text.contains("generated artifact");
    }

    private boolean isDoneLike(TaskEntity task) {
        return task != null && (task.getStatus() == TaskStatus.done || task.getStatus() == TaskStatus.spike_completed);
    }

    private boolean isActiveLike(TaskEntity task) {
        return task.getStatus() == TaskStatus.claimed
                || task.getStatus() == TaskStatus.in_progress
                || task.getStatus() == TaskStatus.review;
    }

    private boolean isBlockedLike(TaskEntity task) {
        return task.getStatus() == TaskStatus.blocked || task.getStatus() == TaskStatus.failed;
    }

    private String roleTag(TaskEntity task) {
        return task.getRole() == null ? "unknown-role" : task.getRole().getTag();
    }

    private String flowStage(TaskEntity task) {
        String payloadStage = payloadText(task, "ems_flow_stage");
        if (!payloadStage.isBlank()) {
            return payloadStage;
        }
        return switch (roleTag(task)) {
            case "BARCAN-TAG-09" -> "decision";
            case "BARCAN-TAG-01" -> "architecture";
            case "BARCAN-TAG-02", "BARCAN-TAG-04", "BARCAN-TAG-07", "BARCAN-TAG-08" -> "implementation";
            case "BARCAN-TAG-03", "BARCAN-TAG-11" -> "experience";
            case "BARCAN-TAG-05" -> "operations";
            case "BARCAN-TAG-06" -> "verification";
            case "BARCAN-TAG-00" -> "integration";
            case "BARCAN-TAG-10" -> "compliance";
            default -> "implementation";
        };
    }

    private List<String> orderedStages() {
        return List.of("decision", "architecture", "implementation", "experience", "operations", "verification", "integration", "compliance");
    }

    private String stageLabel(String stage) {
        return switch (stage) {
            case "decision" -> "Decision";
            case "architecture" -> "Architecture";
            case "implementation" -> "Implementation";
            case "experience" -> "UX/UI";
            case "operations" -> "Build/Deploy";
            case "verification" -> "Verification";
            case "integration" -> "Integration";
            case "compliance" -> "Compliance";
            default -> stage;
        };
    }

    private double kpiTarget(String roleTag) {
        return switch (roleTag) {
            case "BARCAN-TAG-00", "BARCAN-TAG-06", "BARCAN-TAG-07" -> 92.0;
            case "BARCAN-TAG-01", "BARCAN-TAG-09", "BARCAN-TAG-10" -> 88.0;
            default -> 85.0;
        };
    }

    private String statusLabel(double score, double target, double defectPressure, long hardFailures) {
        if (hardFailures > 0 || defectPressure >= 0.45) {
            return "attention";
        }
        if (score >= target) {
            return "on_target";
        }
        if (score >= target - 12.0) {
            return "watch";
        }
        return "behind";
    }

    private String payloadText(TaskEntity task, String field) {
        JsonNode payload = task.getPayload();
        if (payload == null || payload.isNull() || !payload.has(field) || payload.get(field).isNull()) {
            return "";
        }
        return payload.get(field).asText("");
    }

    private boolean payloadBoolean(TaskEntity task, String field) {
        JsonNode payload = task.getPayload();
        return payload != null && payload.has(field) && payload.get(field).asBoolean(false);
    }

    private double payloadNumber(TaskEntity task, String field, double fallback) {
        JsonNode payload = task.getPayload();
        if (payload == null || !payload.has(field) || !payload.get(field).isNumber()) {
            return fallback;
        }
        return payload.get(field).asDouble(fallback);
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
