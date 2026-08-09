package com.eneik.production.services.quality;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.ProcessControlSnapshotEntity;
import com.eneik.production.models.persistence.ReviewConcernEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.ProcessControlSnapshotRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.ReviewConcernRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.audit.SixSigmaAuditService.DefectOpportunityCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Layer 1 (Six Sigma / Measure) of the unified Lean-TOC-Six-Sigma system - see
 * docs/ENGINEERING_INVARIANTS_CHARTER.md and the plan this implements. Computes classical u-charts:
 * subgroup = эпик (FeatureEntity), sequenced ONLY by completion order WITHIN one project - never by
 * calendar date, never across projects (operator's explicit "полная чистота эксперимента" constraint).
 *
 * Phase 1/Phase 2 discipline: the center line and control limits are computed ONCE, pooled from the
 * project's own first {@code baselineEpicCount} completed эпики (in completion order), then held FIXED
 * forever - every later эпик is checked against that fixed baseline, never against a recomputed running
 * average (a recomputed average would let drift silently renormalize itself, defeating the whole point).
 */
@Service
public class ProcessControlService {

    private static final Logger log = LoggerFactory.getLogger(ProcessControlService.class);

    public static final String STREAM_QUALITY_GATE = "qualityGate";
    public static final String STREAM_PR_CONFLICTS = "prConflicts";
    public static final String STREAM_TASK_REVIVAL = "taskRevival";
    public static final String STREAM_REVIEW_CONCERNS = "reviewConcerns";

    private static final String RESUME_COUNT_KEY = "ems_bounded_plan_resume_count";

    private static final Set<TaskStatus> TERMINAL_STATUSES =
            EnumSet.of(TaskStatus.done, TaskStatus.spike_completed, TaskStatus.failed);

    // Layer 0 taxonomy names (docs/ENGINEERING_INVARIANTS_CHARTER.md #1-12) - used to make a
    // KNOWN_PATTERN_VIOLATION Kaizen proposal cite the exact charter rule, not a bare number.
    private static final Map<Integer, String> CHARTER_PATTERN_NAMES = Map.ofEntries(
            Map.entry(1, "CAS vs read-then-write"),
            Map.entry(2, "Critical sections"),
            Map.entry(3, "Absorbing automaton states"),
            Map.entry(4, "Idempotency"),
            Map.entry(5, "Referential transparency"),
            Map.entry(6, "Category errors at serialization boundaries"),
            Map.entry(7, "Monotonic watermarks"),
            Map.entry(8, "Exact denominator scoping"),
            Map.entry(9, "Correlated entity consistency"),
            Map.entry(10, "Single choke point for a shared invariant"),
            Map.entry(11, "Gate granularity must match causal dependency"),
            Map.entry(12, "Independent verification, not self-attestation")
    );

    private final FeatureRepository featureRepository;
    private final TaskRepository taskRepository;
    private final ProcessControlSnapshotRepository snapshotRepository;
    private final SixSigmaAuditService sixSigmaAuditService;
    private final ReviewConcernRepository reviewConcernRepository;
    private final PrReviewRepository prReviewRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final DefectJournalRepository defectJournalRepository;
    private final KaizenService kaizenService;
    private final ProjectRepository projectRepository;

    // Self-injected proxy reference (2026-08-01, live incident: periodicRecompute -> recomputeForProject
    // was a plain self-invocation, which bypasses the Spring AOP proxy entirely - @Transactional on
    // recomputeForProject silently never activated when called this way. The first stream (qualityGate)
    // happened to survive anyway (plain @Column reads only); the second (prConflicts) hit a genuinely
    // lazy @ManyToOne with no Hibernate session open and threw LazyInitializationException. @Lazy breaks
    // the constructor circular-dependency this would otherwise create.
    private final ProcessControlService self;

    @Value("${process-control.baseline-epic-count:2}")
    private int baselineEpicCount;

    public ProcessControlService(FeatureRepository featureRepository,
                                  TaskRepository taskRepository,
                                  ProcessControlSnapshotRepository snapshotRepository,
                                  SixSigmaAuditService sixSigmaAuditService,
                                  ReviewConcernRepository reviewConcernRepository,
                                  PrReviewRepository prReviewRepository,
                                  com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                  DefectJournalRepository defectJournalRepository,
                                  KaizenService kaizenService,
                                  ProjectRepository projectRepository,
                                  @org.springframework.context.annotation.Lazy ProcessControlService self) {
        this.featureRepository = featureRepository;
        this.taskRepository = taskRepository;
        this.snapshotRepository = snapshotRepository;
        this.sixSigmaAuditService = sixSigmaAuditService;
        this.reviewConcernRepository = reviewConcernRepository;
        this.prReviewRepository = prReviewRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.defectJournalRepository = defectJournalRepository;
        this.kaizenService = kaizenService;
        this.projectRepository = projectRepository;
        this.self = self;
    }

    private record EpicPoint(UUID featureId, Instant completedAt, String sixSigmaMetricLabel) {}

    /**
     * Periodic entry point (2026-08-01) - without this, ProcessControlService's u-chart math has no live
     * call site at all (same class of gap as the ReviewConcernRepository field-declaration miss caught
     * earlier this session: code that compiles and even has tests, but nothing in the running system ever
     * calls it). "Завод за 1 раз делает 1 проект" (operator) - always the CURRENT active project, never a
     * cross-project batch.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 7200000, initialDelay = 120000)
    public void periodicRecompute() {
        try {
            UUID activeProjectId = sixSigmaAuditService.getActiveProjectId();
            if (activeProjectId != null) {
                self.recomputeForProject(activeProjectId);
            }
        } catch (Exception e) {
            log.error("[PROCESS-CONTROL] periodic recompute failed: ", e);
        }
    }

    /**
     * Recomputes and persists every u-chart snapshot for one project, one stream at a time. Idempotent:
     * wipes this project's existing snapshots for the stream and rewrites from scratch, since эпик
     * completion order within a project never changes retroactively (only grows).
     */
    @org.springframework.transaction.annotation.Transactional
    public List<ProcessControlSnapshotEntity> recomputeForProject(UUID projectId) {
        List<EpicPoint> completedEpics = completedEpicsInOrder(projectId);
        List<ProcessControlSnapshotEntity> all = new ArrayList<>();
        all.addAll(recomputeStream(projectId, completedEpics, STREAM_QUALITY_GATE,
                featureId -> sixSigmaAuditService.computeQualityGateCounts(null, featureId)));
        all.addAll(recomputeStream(projectId, completedEpics, STREAM_PR_CONFLICTS,
                featureId -> sixSigmaAuditService.computePrConflictCounts(null, featureId)));
        all.addAll(recomputeStream(projectId, completedEpics, STREAM_TASK_REVIVAL,
                this::taskRevivalCounts));
        all.addAll(recomputeStream(projectId, completedEpics, STREAM_REVIEW_CONCERNS,
                this::reviewConcernCounts));
        return all;
    }

    // Engineering invariant #13 (docs/ENGINEERING_INVARIANTS_CHARTER.md, Frege substitutivity salva
    // veritate): this used to query dismissedAt-filtered epics directly, with no knowledge at all of
    // ClientDeliverableReadinessService's duplicate-epic tie-break - a duplicate pair (one "winner", one
    // "loser", same real epic) read as TWO separate u-chart subgroups here, while /epics and productReadiness
    // saw only one. Reading FeatureEntity.canonicalFeatureId directly (set once, rigidly, by
    // ClientDeliverableReadinessService.unionDuplicateFeature) instead of calling into that service avoids
    // adding a new bean dependency here - the pointer itself, once persisted, is the single source of truth
    // every consumer can read independently and still agree (that agreement IS the invariant).
    private List<EpicPoint> completedEpicsInOrder(UUID projectId) {
        List<FeatureEntity> epics = featureRepository.findByProjectIdAndDismissedAtIsNull(projectId);
        Map<UUID, List<UUID>> memberIdsByCanonical = new java.util.HashMap<>();
        for (FeatureEntity epic : epics) {
            UUID canonical = epic.getCanonicalFeatureId() != null ? epic.getCanonicalFeatureId() : epic.getId();
            memberIdsByCanonical.computeIfAbsent(canonical, k -> new ArrayList<>()).add(epic.getId());
        }
        List<EpicPoint> points = new ArrayList<>();
        for (FeatureEntity epic : epics) {
            if (epic.getCanonicalFeatureId() != null) {
                continue; // a duplicate's own real work is rolled into its canonical epic's point below
            }
            List<UUID> memberIds = memberIdsByCanonical.getOrDefault(epic.getId(), List.of(epic.getId()));
            List<TaskEntity> tasks = memberIds.stream()
                    .flatMap(id -> taskRepository.findByFeatureId(id).stream())
                    .toList();
            if (tasks.isEmpty()) continue;
            boolean allTerminal = tasks.stream().allMatch(t -> TERMINAL_STATUSES.contains(t.getStatus()));
            if (!allTerminal) continue;
            Instant completedAt = tasks.stream()
                    .map(TaskEntity::getUpdatedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(epic.getCreatedAt());
            points.add(new EpicPoint(epic.getId(), completedAt, epic.getSixSigmaMetric()));
        }
        points.sort(Comparator.comparing(EpicPoint::completedAt));
        return points;
    }

    @FunctionalInterface
    private interface CountFn {
        DefectOpportunityCount apply(UUID featureId);
    }

    private List<ProcessControlSnapshotEntity> recomputeStream(UUID projectId, List<EpicPoint> completedEpics,
                                                                 String stream, CountFn countFn) {
        snapshotRepository.deleteAll(snapshotRepository.findByProjectIdAndStreamOrderBySequenceIndexAsc(projectId, stream));

        int total = completedEpics.size();
        int locked = Math.min(baselineEpicCount, total);

        List<DefectOpportunityCount> counts = new ArrayList<>();
        for (EpicPoint ep : completedEpics) {
            counts.add(countFn.apply(ep.featureId()));
        }

        // Pooled baseline centerline from exactly the first `locked` эпики, by completion order - a
        // stable set that never changes as later эпики complete (Phase 1/Phase 2 discipline).
        long baselineDefects = 0;
        long baselineOpportunities = 0;
        for (int i = 0; i < locked; i++) {
            baselineDefects += counts.get(i).defects();
            baselineOpportunities += counts.get(i).opportunities();
        }
        boolean baselineLocked = total >= baselineEpicCount && baselineOpportunities > 0;
        double centerLine = baselineOpportunities > 0 ? (double) baselineDefects / baselineOpportunities : 0.0;

        List<Double> monitoringUValues = new ArrayList<>();
        List<ProcessControlSnapshotEntity> snapshots = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            EpicPoint ep = completedEpics.get(i);
            DefectOpportunityCount c = counts.get(i);
            double u = c.opportunities() > 0 ? (double) c.defects() / c.opportunities() : 0.0;
            boolean isMonitoring = baselineLocked && i >= locked;

            double ucl = 0.0;
            double lcl = 0.0;
            boolean outOfControl = false;
            String weSignal = null;

            if (baselineLocked) {
                double spread = c.opportunities() > 0 ? 3.0 * Math.sqrt(centerLine / c.opportunities()) : 0.0;
                ucl = centerLine + spread;
                lcl = Math.max(0.0, centerLine - spread);
            }

            if (isMonitoring) {
                monitoringUValues.add(u);
                if (u > ucl || u < lcl) {
                    outOfControl = true;
                }
                weSignal = detectWesternElectricSignal(monitoringUValues, centerLine);
                if (weSignal != null) {
                    outOfControl = true;
                }
            }

            ProcessControlSnapshotEntity snap = new ProcessControlSnapshotEntity();
            snap.setProjectId(projectId);
            snap.setFeatureId(ep.featureId());
            snap.setStream(stream);
            snap.setSequenceIndex(i);
            snap.setU(u);
            snap.setN(c.opportunities());
            snap.setDefects(c.defects());
            snap.setCenterLine(centerLine);
            snap.setUpperControlLimit(ucl);
            snap.setLowerControlLimit(lcl);
            snap.setPhase(isMonitoring ? "MONITORING" : "BASELINE");
            snap.setOutOfControl(outOfControl);
            snap.setWesternElectricSignal(weSignal);
            snap.setSixSigmaMetricLabel(ep.sixSigmaMetricLabel());
            snap.setComputedAt(Instant.now());
            snapshots.add(snap);
        }

        List<ProcessControlSnapshotEntity> saved = snapshotRepository.saveAll(snapshots);
        long outOfControlCount = saved.stream().filter(ProcessControlSnapshotEntity::isOutOfControl).count();
        if (outOfControlCount > 0) {
            log.warn("[PROCESS-CONTROL] project={} stream={} {} out-of-control point(s) of {} monitoring point(s)",
                    projectId, stream, outOfControlCount, Math.max(0, total - locked));
        }

        // Layer 4 loop-closing: react only to the CURRENT state (latest эпик), not a replay of every
        // historical excursion on every recompute - a u-chart's job is "is the process out of control
        // right now", and each past excursion already had its moment when it was the latest point.
        if (!saved.isEmpty()) {
            ProcessControlSnapshotEntity latest = saved.get(saved.size() - 1);
            if (latest.isOutOfControl()) {
                closeLoop(projectId, stream, latest);
            }
        }
        return saved;
    }

    /**
     * Layer 4 (Analyze -> Improve): a u-chart out-of-control signal is diagnosed by looking at the
     * underlying defect events already recorded for this эпик. If they carry a consistent
     * rootCausePatternId (Layer 0), this is a KNOWN fix shape - raise a KNOWN_PATTERN_VIOLATION proposal
     * citing the exact charter rule. If uncategorized, raise a SYSTEMIC_DEFECT proposal flagged as a
     * candidate new invariant pattern - the taxonomy itself is meant to grow from real incidents.
     */
    private void closeLoop(UUID projectId, String stream, ProcessControlSnapshotEntity signal) {
        List<Integer> patternIds;
        if (STREAM_REVIEW_CONCERNS.equals(stream)) {
            patternIds = reviewConcernRepository.findByFeatureId(signal.getFeatureId()).stream()
                    .map(ReviewConcernEntity::getRootCausePatternId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } else {
            patternIds = defectJournalRepository.findByFeatureId(signal.getFeatureId()).stream()
                    .map(DefectJournalEntity::getRootCausePatternId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        String projectName = projectRepository.findById(projectId)
                .map(com.eneik.production.models.persistence.ProjectEntity::getName)
                .orElse(null);
        String title = String.format("u-chart out of control: %s (эпик %s)", stream, signal.getFeatureId());
        String description = String.format(
                "Stream '%s' u=%.4f outside [%.4f, %.4f] (centerline %.4f)%s at эпик sequence #%d.%s",
                stream, signal.getU(), signal.getLowerControlLimit(), signal.getUpperControlLimit(),
                signal.getCenterLine(),
                signal.getWesternElectricSignal() != null ? " [Western Electric: " + signal.getWesternElectricSignal() + "]" : "",
                signal.getSequenceIndex(),
                // 2026-08-07: the эпик owner's own operational definition of what quality means here
                // (FeatureEntity.sixSigmaMetric, carried onto the snapshot in recomputeStream) - gives a
                // human reviewer the actual business meaning of this excursion, not just a raw stream name.
                signal.getSixSigmaMetricLabel() != null && !signal.getSixSigmaMetricLabel().isBlank()
                        ? " Эпик's own quality target: \"" + signal.getSixSigmaMetricLabel() + "\"." : "");

        if (!patternIds.isEmpty()) {
            int dominant = patternIds.stream()
                    .collect(java.util.stream.Collectors.groupingBy(p -> p, java.util.stream.Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(patternIds.get(0));
            String patternName = CHARTER_PATTERN_NAMES.getOrDefault(dominant, "uncatalogued");
            kaizenService.recordKnownPatternViolationProposal(projectId, projectName, dominant, patternName, title, description);
        } else {
            kaizenService.recordSystemicDefectProposal(projectId, projectName, title,
                    description + " No underlying defect event carries a rootCausePatternId yet - candidate new invariant pattern, uncategorized.");
        }
    }

    /**
     * Western Electric rules beyond a simple 3σ excursion - catches slow drift, not just sudden spikes.
     * Evaluated only against the MONITORING-phase sequence so far (baseline points are the reference,
     * never flagged against themselves).
     */
    private String detectWesternElectricSignal(List<Double> monitoringUValues, double centerLine) {
        int n = monitoringUValues.size();
        if (n >= 8) {
            List<Double> last8 = monitoringUValues.subList(n - 8, n);
            boolean allAbove = last8.stream().allMatch(v -> v > centerLine);
            boolean allBelow = last8.stream().allMatch(v -> v < centerLine);
            if (allAbove || allBelow) {
                return "8_CONSECUTIVE_SAME_SIDE";
            }
        }
        if (n >= 6) {
            List<Double> last6 = monitoringUValues.subList(n - 6, n);
            boolean increasing = true;
            boolean decreasing = true;
            for (int i = 1; i < last6.size(); i++) {
                if (last6.get(i) <= last6.get(i - 1)) increasing = false;
                if (last6.get(i) >= last6.get(i - 1)) decreasing = false;
            }
            if (increasing || decreasing) {
                return "6_POINT_TREND";
            }
        }
        return null;
    }

    private DefectOpportunityCount taskRevivalCounts(UUID featureId) {
        List<TaskEntity> tasks = taskRepository.findByFeatureId(featureId);
        long defects = tasks.stream()
                .filter(t -> t.getPayload() != null && t.getPayload().path(RESUME_COUNT_KEY).asInt(0) > 0)
                .count();
        return new DefectOpportunityCount(defects, tasks.size());
    }

    private DefectOpportunityCount reviewConcernCounts(UUID featureId) {
        long defects = reviewConcernRepository.findByFeatureId(featureId).size();
        long reviewPasses = prReviewRepository.findAll().stream()
                .filter(r -> {
                    if (r.getJulesSessionId() == null) return false;
                    var sessionOpt = julesSessionRepository.findById(r.getJulesSessionId());
                    if (sessionOpt.isEmpty()) return false;
                    var taskOpt = taskRepository.findById(sessionOpt.get().getTaskId());
                    return taskOpt.isPresent() && featureId.equals(taskOpt.get().getFeatureId());
                })
                .count();
        return new DefectOpportunityCount(defects, reviewPasses);
    }
}
