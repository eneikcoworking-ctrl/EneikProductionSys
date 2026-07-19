package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.EmsDashboardMetricsDto;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
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
    private static final List<RoleDoctrineProfile> ROLE_DOCTRINES = List.of(
            new RoleDoctrineProfile("BARCAN-TAG-00", "CODE-GUARDIAN", "Code meaning, binary review, integration integrity", "must_be", "complicated"),
            new RoleDoctrineProfile("BARCAN-TAG-01", "ACTUALIST-OBJECT", "Real domain objects, identity, domain boundaries", "must_be", "complicated"),
            new RoleDoctrineProfile("BARCAN-TAG-02", "RIGID-DESIGNATOR", "Contracts, naming, API semantics, context safety", "must_be", "complicated"),
            new RoleDoctrineProfile("BARCAN-TAG-03", "BELIEF-INTENSION", "User intention, cognitive load, JTBD-backed interaction design", "performance", "complex"),
            new RoleDoctrineProfile("BARCAN-TAG-04", "MODAL-QUANTIFIER", "Prediction evidence, epistemic tagging, no hallucinated certainty", "performance", "complex"),
            new RoleDoctrineProfile("BARCAN-TAG-05", "NECESSARY-IDENTITY", "Environment causality, reproducible operations, incident closure", "must_be", "chaotic"),
            new RoleDoctrineProfile("BARCAN-TAG-06", "DEONTIC-CONSISTENCY", "Testability, bivalence, falsification, quality gates", "must_be", "complicated"),
            new RoleDoctrineProfile("BARCAN-TAG-07", "SECOND-ORDER-KNOWLEDGE", "Auth, validation, zero-trust proof, hostile inputs", "must_be", "complicated"),
            new RoleDoctrineProfile("BARCAN-TAG-08", "SUBSTITUTIVITY-SALVA-VERITATE", "Data type integrity, migrations, lineage, substitutability", "must_be", "complicated"),
            new RoleDoctrineProfile("BARCAN-TAG-09", "MORAL-DILEMMA", "Lean/TOC/JTBD value filter, waste prevention, decision quality", "performance", "complex"),
            new RoleDoctrineProfile("BARCAN-TAG-10", "DEONTIC-PROHIBITION", "Compliance prohibitions, retention, PIA and regulatory constraints", "must_be", "complicated"),
            new RoleDoctrineProfile("BARCAN-TAG-11", "CLIENT-PERCEPTION", "Perceptual UX, accessibility, visual evidence, Core Web Vitals", "performance", "complex")
    );

    public EmsDashboardMetricsDto build(List<TaskEntity> tasks) {
        return build(tasks, List.of());
    }

    // System/meta tasks (wishlist compiler, falsification audit, PR-review fallback, design review) are
    // all dispatched under the same orchestrator role (BARCAN-TAG-09) regardless of which role's doctrine
    // they actually touch - counting them as that role's owner-task evidence conflates infrastructure
    // plumbing with real product work satisfying the doctrine, inflating readiness for a role that may not
    // have shipped anything (confirmed live: operator flagged non-zero readiness on a fresh project with
    // zero real work, before any of these system tasks even existed - the conflation predates this fix and
    // was the root cause). Marked via the same "taskType" payload key JulesDispatchService already uses to
    // route these tasks around the normal review pipeline (isWishlistCompilerTask etc.) - reused here
    // rather than re-deriving task type from title/role heuristics.
    private static final String SYSTEM_TASK_TYPE_PAYLOAD_KEY = "taskType";

    private boolean isSystemMetaTask(TaskEntity task) {
        return task.getPayload() != null && task.getPayload().has(SYSTEM_TASK_TYPE_PAYLOAD_KEY);
    }

    public EmsDashboardMetricsDto build(List<TaskEntity> tasks, List<WishlistEntity> wishlist) {
        List<TaskEntity> safeTasks = tasks == null ? List.of() : tasks;
        List<WishlistEntity> safeWishlist = wishlist == null ? List.of() : wishlist;
        List<TaskEntity> realWorkTasks = safeTasks.stream().filter(task -> !isSystemMetaTask(task)).toList();
        return new EmsDashboardMetricsDto(
                Instant.now().toString(),
                flowChart(safeTasks),
                roleDoctrineReadiness(realWorkTasks, safeWishlist),
                roleKpis(safeTasks),
                defectWork(safeTasks),
                graphHealth(safeTasks),
                List.of(
                        "Role doctrine readiness is calculated for all 12 BARCAN tags from source-role wishlist evidence, owner-role task evidence, defects, and missing evidence.",
                        "Role doctrine readiness excludes system/meta tasks (wishlist compiler, falsification audit, PR-review fallback, design review) - they are all dispatched under the orchestrator role regardless of which doctrine they touch, so counting them would inflate readiness with infrastructure plumbing instead of real product work. Role KPIs below are not filtered this way and include them.",
                        "Role execution telemetry is separate from doctrine readiness; zero owner tasks means no execution load, not role approval.",
                        "Defect work includes failed, blocked, retried, and circuit-breaker recovery tasks.",
                        "Graph health uses EMS payload keys plus dependsOn edges; lower duplicate semantic keys means cleaner orchestration.",
                        "A queued task with an unfinished dependsOn task is intentionally blocked by the dependency graph, not by missing Jules capability.",
                        "Use sourceRoleTag as the role that raised an objection; use task.roleTag as the owner role responsible for execution."
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

    private EmsDashboardMetricsDto.RoleDoctrineReadiness roleDoctrineReadiness(List<TaskEntity> tasks, List<WishlistEntity> wishlist) {
        Map<String, List<TaskEntity>> tasksByOwnerRole = tasks.stream()
                .collect(Collectors.groupingBy(this::roleTag, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<WishlistEntity>> wishlistBySourceRole = wishlist.stream()
                .filter(item -> item.getSourceRoleTag() != null && !item.getSourceRoleTag().isBlank())
                .collect(Collectors.groupingBy(WishlistEntity::getSourceRoleTag, LinkedHashMap::new, Collectors.toList()));

        List<EmsDashboardMetricsDto.RoleDoctrineVerdict> verdicts = ROLE_DOCTRINES.stream()
                .map(profile -> roleDoctrineVerdict(
                        profile,
                        tasksByOwnerRole.getOrDefault(profile.roleTag(), List.of()),
                        wishlistBySourceRole.getOrDefault(profile.roleTag(), List.of()),
                        tasks
                ))
                .toList();

        long satisfied = countStance(verdicts, "satisfied");
        long almostSatisfied = countStance(verdicts, "almost_satisfied");
        long objects = countStance(verdicts, "objects");
        long refuses = countStance(verdicts, "refuses");
        long unknown = countStance(verdicts, "unknown");
        double readinessScore = verdicts.stream()
                .mapToDouble(EmsDashboardMetricsDto.RoleDoctrineVerdict::satisfactionScore)
                .average()
                .orElse(0.0);

        String statusLabel;
        String interpretation;
        if (refuses > 0) {
            statusLabel = "blocked";
            interpretation = "One or more BARCAN doctrines refuse the current project state; resolve Must-Be objections before acceptance.";
        } else if (objects > 0) {
            statusLabel = "contested";
            interpretation = "BARCAN doctrine objections exist; compile source-role wishlist into owner-role atomic work before expanding scope.";
        } else if (unknown > 0) {
            statusLabel = "incomplete";
            interpretation = "Some BARCAN roles have no attack or execution evidence yet; run the role council attack before claiming readiness.";
        } else {
            statusLabel = "ready";
            interpretation = "All BARCAN doctrines are satisfied or almost satisfied from available project evidence.";
        }

        return new EmsDashboardMetricsDto.RoleDoctrineReadiness(
                verdicts,
                verdicts.size(),
                satisfied,
                almostSatisfied,
                objects,
                refuses,
                unknown,
                round(readinessScore),
                statusLabel,
                interpretation
        );
    }

    private EmsDashboardMetricsDto.RoleDoctrineVerdict roleDoctrineVerdict(RoleDoctrineProfile profile,
                                                                           List<TaskEntity> ownerTasks,
                                                                           List<WishlistEntity> sourceWishlist,
                                                                           List<TaskEntity> allTasks) {
        long ownerTotal = ownerTasks.size();
        long ownerDone = ownerTasks.stream().filter(this::isDoneLike).count();
        long ownerOpen = ownerTasks.stream().filter(task -> !isDoneLike(task)).count();
        long ownerBlocked = ownerTasks.stream().filter(this::isBlockedLike).count();
        long ownerFailed = ownerTasks.stream().filter(task -> task.getStatus() == TaskStatus.failed).count();
        long defectWork = ownerTasks.stream().filter(this::isDefectWork).count();
        long gatePassed = ownerTasks.stream().filter(TaskEntity::isQualityGatePassed).count();
        long sourceTotal = sourceWishlist.size();
        long sourcePending = sourceWishlist.stream()
                .filter(item -> item.getStatus() == WishlistStatus.pending)
                .count();

        boolean hasEvidence = ownerTotal > 0 || sourceTotal > 0;
        boolean severeSourceObjection = sourceWishlist.stream()
                .filter(item -> item.getStatus() == WishlistStatus.pending)
                .anyMatch(item -> containsRefusalSignal(item.getContent())
                        || containsRefusalSignal(item.getAcceptanceCriteria())
                        || containsRefusalSignal(item.getDod()));
        boolean hardFailure = ownerBlocked > 0 || ownerFailed > 0;

        double totalPotentialImpact = 0.0;
        double actualRealizedImpact = 0.0;
        for (TaskEntity task : allTasks) {
            double impact = getTaskImpact(task, profile.roleTag());
            totalPotentialImpact += impact;
            if (isDoneLike(task)) {
                double qualityMultiplier = task.isQualityGatePassed() ? 1.0 : 0.7;
                actualRealizedImpact += impact * qualityMultiplier;
            }
        }
        double impactRatio = totalPotentialImpact == 0.0 ? 0.0 : (actualRealizedImpact / totalPotentialImpact);

        String stance;
        double satisfactionScore;
        if (!hasEvidence) {
            stance = "unknown";
            satisfactionScore = 25.0;
        } else if ((severeSourceObjection && "must_be".equals(profile.kanoBias())) || ownerFailed > 0) {
            stance = "refuses";
            double maxPotential = Math.max(0.0, 34.0 - (ownerFailed * 6.0) - Math.min(20.0, sourcePending * 4.0));
            satisfactionScore = impactRatio * maxPotential;
        } else if (sourcePending > 0 || hardFailure || defectWork > 0) {
            stance = "objects";
            double maxPotential = Math.max(35.0, 62.0 - (ownerBlocked * 5.0) - Math.min(18.0, sourcePending * 3.0) - Math.min(12.0, defectWork * 2.0));
            satisfactionScore = 35.0 + impactRatio * (maxPotential - 35.0);
        } else if (ownerOpen > 0) {
            stance = "almost_satisfied";
            satisfactionScore = 72.0 + impactRatio * 18.0;
        } else {
            stance = "satisfied";
            double baseScore = ownerTotal == 0 ? 78.0 : 92.0 + Math.min(8.0, gatePassed * 1.5);
            satisfactionScore = baseScore;
        }

        double confidence = confidence(ownerTotal, ownerDone, gatePassed, sourceTotal, hasEvidence);
        String kanoPressure = kanoPressure(profile, stance);
        String topObjection = topObjection(stance, sourcePending, ownerOpen, ownerBlocked, ownerFailed, defectWork);

        return new EmsDashboardMetricsDto.RoleDoctrineVerdict(
                profile.roleTag(),
                profile.doctrineName(),
                profile.doctrineFocus(),
                stance,
                round(satisfactionScore),
                round(confidence),
                kanoPressure,
                profile.cynefinBias(),
                topObjection,
                sourcePending,
                sourceTotal,
                ownerTotal,
                ownerOpen,
                ownerBlocked,
                ownerDone,
                defectWork,
                roleEvidence(ownerTotal, ownerDone, gatePassed, sourcePending, sourceTotal, ownerBlocked, defectWork)
        );
    }

    private List<EmsDashboardMetricsDto.RoleKpi> roleKpis(List<TaskEntity> tasks) {
        Map<String, List<TaskEntity>> byRole = tasks.stream()
                .collect(Collectors.groupingBy(this::roleTag, LinkedHashMap::new, Collectors.toList()));
        List<String> orderedTags = new ArrayList<>(roleDoctrineTags());
        byRole.keySet().stream()
                .filter(tag -> !orderedTags.contains(tag))
                .sorted()
                .forEach(orderedTags::add);
        return orderedTags.stream()
                .map(roleTag -> roleKpi(roleTag, byRole.getOrDefault(roleTag, List.of())))
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
        if (total == 0) {
            double target = kpiTarget(roleTag);
            return new EmsDashboardMetricsDto.RoleKpi(
                    roleTag,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0.0,
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    target,
                    "idle"
            );
        }
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
                .filter(task -> task.getDependsOn() != null && !isTerminalForDependency(task.getDependsOn()))
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

    private boolean isTerminalForDependency(TaskEntity task) {
        return isDoneLike(task) || isBlockedLike(task);
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

    private long countStance(List<EmsDashboardMetricsDto.RoleDoctrineVerdict> verdicts, String stance) {
        return verdicts.stream().filter(verdict -> stance.equals(verdict.stance())).count();
    }

    private Set<String> roleDoctrineTags() {
        return ROLE_DOCTRINES.stream()
                .map(RoleDoctrineProfile::roleTag)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean containsRefusalSignal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("refusal")
                || lower.contains("reject")
                || lower.contains("forbidden")
                || lower.contains("violation")
                || lower.contains("critical")
                || lower.contains("p0")
                || lower.contains("security")
                || lower.contains("auth")
                || lower.contains("privacy")
                || lower.contains("compliance")
                || lower.contains("leak")
                || lower.contains("failed")
                || lower.contains("blocked");
    }

    private double confidence(long ownerTotal, long ownerDone, long gatePassed, long sourceTotal, boolean hasEvidence) {
        if (!hasEvidence) {
            return 0.18;
        }
        double value = 0.24;
        if (sourceTotal > 0) {
            value += 0.26;
        }
        if (ownerTotal > 0) {
            value += 0.24;
        }
        if (ownerDone > 0) {
            value += 0.14;
        }
        if (gatePassed > 0) {
            value += 0.12;
        }
        return clamp01(value);
    }

    private String kanoPressure(RoleDoctrineProfile profile, String stance) {
        return switch (stance) {
            case "refuses" -> "must_be";
            case "objects" -> profile.kanoBias();
            case "unknown" -> "discovery";
            case "almost_satisfied" -> "performance";
            default -> "none";
        };
    }

    private String topObjection(String stance, long sourcePending, long ownerOpen, long ownerBlocked, long ownerFailed, long defectWork) {
        if ("unknown".equals(stance)) {
            return "No role-attack or execution evidence exists yet; run the BARCAN council attack before project acceptance.";
        }
        if (ownerFailed > 0) {
            return "Owner-role execution has failed work; recover through a fresh atomic wishlist item before claiming doctrine satisfaction.";
        }
        if (sourcePending > 0) {
            return "Source-role objections are still pending; compile, deduplicate, or explicitly dismiss them.";
        }
        if (ownerBlocked > 0) {
            return "Owner-role work is blocked; analyze the failed attempt and create smaller recovery work.";
        }
        if (defectWork > 0) {
            return "Defect-work evidence remains attached to this role; close recovery before increasing feature scope.";
        }
        if (ownerOpen > 0) {
            return "Execution work is still open; role is close but not fully satisfied.";
        }
        return "No open doctrine objection is visible in the current project evidence.";
    }

    private List<String> roleEvidence(long ownerTotal, long ownerDone, long gatePassed, long sourcePending,
                                      long sourceTotal, long ownerBlocked, long defectWork) {
        List<String> evidence = new ArrayList<>();
        if (sourceTotal > 0) {
            evidence.add("source_wishlist_total=" + sourceTotal);
        }
        if (sourcePending > 0) {
            evidence.add("source_wishlist_pending=" + sourcePending);
        }
        if (ownerTotal > 0) {
            evidence.add("owner_tasks=" + ownerDone + "/" + ownerTotal + " done");
        }
        if (gatePassed > 0) {
            evidence.add("quality_gate_passed=" + gatePassed);
        }
        if (ownerBlocked > 0) {
            evidence.add("blocked_or_failed_owner_tasks=" + ownerBlocked);
        }
        if (defectWork > 0) {
            evidence.add("defect_work=" + defectWork);
        }
        if (evidence.isEmpty()) {
            evidence.add("no_evidence_yet");
        }
        return evidence;
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

    private double getTaskImpact(TaskEntity task, String roleTag) {
        JsonNode payload = task.getPayload();
        if (payload != null && payload.has("impact_coefficients")) {
            JsonNode matrix = payload.get("impact_coefficients");
            if (matrix.has(roleTag)) {
                return matrix.get(roleTag).asDouble(0.1);
            }
        }
        if (task.getRole() != null && roleTag.equals(task.getRole().getTag())) {
            return 0.9;
        }
        // A task owned by another role and not explicitly cross-referenced via impact_coefficients
        // carries no weight for this role — otherwise every unrelated task in the project nudges every
        // other role's satisfaction ratio, diluting it toward the project-wide average as task count grows.
        return 0.0;
    }

    private record RoleDoctrineProfile(
            String roleTag,
            String doctrineName,
            String doctrineFocus,
            String kanoBias,
            String cynefinBias
    ) {}
}
