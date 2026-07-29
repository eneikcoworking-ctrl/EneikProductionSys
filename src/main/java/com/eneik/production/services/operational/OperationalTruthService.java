package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.OperationalTruthDto;
import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.dashboard.SystemStatusService;
import com.eneik.production.services.task.TaskTitleBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OperationalTruthService {
    private static final Set<String> REVIEW_FAILING_STATUSES = Set.of(
            "failure", "failing", "conflict", "escalated", "closed_unmerged", "invalid_pr", "unowned"
    );
    private static final Set<String> REVIEW_PENDING_STATUSES = Set.of("pending", "unavailable", "success", "conflict");
    private static final Set<String> OPEN_SESSION_STATUSES = Set.of("queued", "running", "pr_opened");

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PrReviewRepository prReviewRepository;
    private final DefectJournalRepository defectJournalRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final SystemStatusService systemStatusService;

    public OperationalTruthService(ProjectRepository projectRepository,
                                   TaskRepository taskRepository,
                                   WishlistRepository wishlistRepository,
                                   JulesSessionRepository julesSessionRepository,
                                   PrReviewRepository prReviewRepository,
                                   DefectJournalRepository defectJournalRepository,
                                   ClientDeliverableReadinessService readinessService,
                                   SystemStatusService systemStatusService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.wishlistRepository = wishlistRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
        this.defectJournalRepository = defectJournalRepository;
        this.readinessService = readinessService;
        this.systemStatusService = systemStatusService;
    }

    @Transactional(readOnly = true)
    public OperationalTruthDto build(UUID projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<WishlistEntity> wishlist = wishlistRepository.findByProjectId(projectId);
        List<JulesSessionEntity> sessions = sessionsForTasks(tasks);
        List<PrReviewEntity> reviews = reviewsForSessions(sessions);
        List<DefectJournalEntity> recentDefects = defectJournalRepository.findByProjectIdAndCreatedAtAfter(
                projectId, Instant.now().minus(24, ChronoUnit.HOURS));

        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(projectId);
        String systemStatus = systemStallStatus(systemStatusService.getStatus(projectId));
        DuplicateContent duplicateContent = duplicateContent(tasks);
        Map<UUID, List<JulesSessionEntity>> sessionsByTask = sessions.stream()
                .collect(Collectors.groupingBy(JulesSessionEntity::getTaskId));
        Map<UUID, List<PrReviewEntity>> reviewsBySession = reviews.stream()
                .collect(Collectors.groupingBy(PrReviewEntity::getJulesSessionId));

        OperationalTruthDto.Delivery delivery = delivery(readiness);
        OperationalTruthDto.ActiveFlow activeFlow = activeFlow(tasks, wishlist, sessions);
        OperationalTruthDto.EvidenceSummary evidence = evidence(tasks, reviews);
        OperationalTruthDto.DefectSummary defects = defects(recentDefects);
        List<OperationalTruthDto.Blocker> blockers = blockers(
                tasks, wishlist, reviews, systemStatus, duplicateContent, sessionsByTask, reviewsBySession);
        List<OperationalTruthDto.InvariantStatus> invariants = invariants(
                readiness, tasks, reviews, systemStatus, duplicateContent, sessionsByTask, reviewsBySession, recentDefects);
        OperationalTruthDto.Trust trust = trust(evidence, blockers, systemStatus, duplicateContent, recentDefects);
        OperationalTruthDto.BlockedValue blockedValue = blockedValue(blockers);
        OperationalTruthDto.LearningSummary learning = learning(recentDefects, invariants);

        return new OperationalTruthDto(
                Instant.now(),
                "observe_only",
                new OperationalTruthDto.ProjectRef(
                        project.getId(),
                        project.getName(),
                        project.getStatus() == null ? "unknown" : project.getStatus().name(),
                        project.getRepositoryName()),
                delivery,
                trust,
                activeFlow,
                blockedValue,
                evidence,
                defects,
                learning,
                sourceOfTruth(),
                invariants,
                promotionPolicy(),
                frontendTranslations(),
                recommendedNextAction(delivery, blockedValue, learning)
        );
    }

    static String deliveryStatus(ClientDeliverableReadinessService.Readiness readiness) {
        if (readiness.totalFeatures() == 0) {
            return "no_scope";
        }
        if (!readiness.decompositionComplete()) {
            return "decomposing";
        }
        if (readiness.completeFeatures() >= readiness.totalFeatures()) {
            return "delivered";
        }
        return "building";
    }

    static String trustLevel(double score) {
        if (score >= 0.85) {
            return "trusted";
        }
        if (score >= 0.65) {
            return "watch";
        }
        if (score >= 0.40) {
            return "degraded";
        }
        return "blocked";
    }

    static boolean isTrustBlockingSystemStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.toLowerCase(Locale.ROOT);
        return !Set.of("ok", "idle_no_actionable_work", "busy_with_actionable_work").contains(normalized);
    }

    static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, Math.round(value * 100.0) / 100.0));
    }

    private List<JulesSessionEntity> sessionsForTasks(List<TaskEntity> tasks) {
        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        return taskIds.isEmpty() ? List.of() : julesSessionRepository.findByTaskIdIn(taskIds);
    }

    private List<PrReviewEntity> reviewsForSessions(List<JulesSessionEntity> sessions) {
        List<UUID> sessionIds = sessions.stream().map(JulesSessionEntity::getId).toList();
        return sessionIds.isEmpty() ? List.of() : prReviewRepository.findAll().stream()
                .filter(review -> sessionIds.contains(review.getJulesSessionId()))
                .toList();
    }

    private OperationalTruthDto.Delivery delivery(ClientDeliverableReadinessService.Readiness readiness) {
        String status = deliveryStatus(readiness);
        double featureRatio = readiness.totalFeatures() == 0
                ? 0.0
                : (double) readiness.completeFeatures() / readiness.totalFeatures();
        double mergedRatio = readiness.totalDeliverables() == 0
                ? 0.0
                : (double) readiness.mergedDeliverables() / readiness.totalDeliverables();
        String headline = switch (status) {
            case "delivered" -> "All planned features have delivery evidence.";
            case "building" -> readiness.completeFeatures() + " of " + readiness.totalFeatures()
                    + " features have delivery evidence.";
            case "decomposing" -> "The client brief is still being decomposed into verifiable work.";
            default -> "No measurable delivery scope is available yet.";
        };
        return new OperationalTruthDto.Delivery(
                readiness.totalFeatures(),
                readiness.completeFeatures(),
                readiness.totalDeliverables(),
                readiness.mergedDeliverables(),
                clamp(featureRatio),
                clamp(mergedRatio),
                readiness.decompositionComplete(),
                status,
                headline
        );
    }

    private OperationalTruthDto.ActiveFlow activeFlow(List<TaskEntity> tasks,
                                                      List<WishlistEntity> wishlist,
                                                      List<JulesSessionEntity> sessions) {
        long queued = countStatus(tasks, TaskStatus.queued);
        long active = tasks.stream().filter(task -> Set.of(TaskStatus.claimed, TaskStatus.in_progress).contains(task.getStatus())).count();
        long review = tasks.stream().filter(task -> Set.of(TaskStatus.pending_review, TaskStatus.review).contains(task.getStatus())).count();
        long done = countStatus(tasks, TaskStatus.done) + countStatus(tasks, TaskStatus.spike_completed);
        long failed = countStatus(tasks, TaskStatus.failed);
        long pendingWishlist = wishlist.stream().filter(item -> item.getStatus() == WishlistStatus.pending).count();
        long compilingWishlist = wishlist.stream().filter(item -> item.getStatus() == WishlistStatus.compiling).count();
        long openSessions = sessions.stream().filter(session -> OPEN_SESSION_STATUSES.contains(normalize(session.getStatus()))).count();
        List<String> narrative = new ArrayList<>();
        if (queued > 0) {
            narrative.add(queued + " task(s) are waiting for agent capacity.");
        }
        if (active > 0 || review > 0) {
            narrative.add((active + review) + " task(s) are actively moving through implementation/review.");
        }
        if (pendingWishlist > 0 || compilingWishlist > 0) {
            narrative.add((pendingWishlist + compilingWishlist) + " wishlist item(s) still need decomposition.");
        }
        if (narrative.isEmpty()) {
            narrative.add("No active flow is visible for this project.");
        }
        return new OperationalTruthDto.ActiveFlow(
                queued, active, review, done, failed, pendingWishlist, compilingWishlist, openSessions, narrative);
    }

    private OperationalTruthDto.EvidenceSummary evidence(List<TaskEntity> tasks, List<PrReviewEntity> reviews) {
        int mergedReviews = (int) reviews.stream().filter(review -> Boolean.TRUE.equals(review.getMerged())).count();
        int openReviews = (int) reviews.stream().filter(review -> !Boolean.TRUE.equals(review.getMerged())).count();
        int pendingReviews = (int) reviews.stream()
                .filter(review -> REVIEW_PENDING_STATUSES.contains(normalize(review.getCiStatus())))
                .count();
        int failingReviews = (int) reviews.stream()
                .filter(review -> REVIEW_FAILING_STATUSES.contains(normalize(review.getCiStatus())))
                .count();
        int qualityGatePassed = (int) tasks.stream().filter(TaskEntity::isQualityGatePassed).count();
        int qualityGateFailed = (int) tasks.stream()
                .filter(task -> task.getQualityGateReport() != null)
                .filter(task -> !task.isQualityGatePassed())
                .count();
        int screenshots = (int) reviews.stream()
                .filter(review -> review.getScreenshotUrls() != null && !review.getScreenshotUrls().isBlank())
                .count();
        List<OperationalTruthDto.EvidenceSignal> strongest = new ArrayList<>();
        if (mergedReviews > 0) {
            strongest.add(new OperationalTruthDto.EvidenceSignal(
                    "merged_pr", 5, mergedReviews + " review(s)", "Strong delivery evidence."));
        }
        if (qualityGatePassed > 0) {
            strongest.add(new OperationalTruthDto.EvidenceSignal(
                    "quality_gate", 4, qualityGatePassed + " task(s)", "Implementation confidence evidence."));
        }
        if (screenshots > 0) {
            strongest.add(new OperationalTruthDto.EvidenceSignal(
                    "screenshot", 3, screenshots + " review(s)", "User-visible verification evidence."));
        }
        if (openReviews > 0) {
            strongest.add(new OperationalTruthDto.EvidenceSignal(
                    "open_review", 2, openReviews + " review(s)", "Activity evidence, not delivery by itself."));
        }
        return new OperationalTruthDto.EvidenceSummary(
                mergedReviews, openReviews, pendingReviews, failingReviews, qualityGatePassed, qualityGateFailed,
                screenshots, strongest);
    }

    private OperationalTruthDto.DefectSummary defects(List<DefectJournalEntity> recentDefects) {
        List<OperationalTruthDto.DefectItem> items = recentDefects.stream()
                .sorted(Comparator.comparing(DefectJournalEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(defect -> new OperationalTruthDto.DefectItem(
                        text(defect.getSeverity()),
                        text(defect.getCategory()),
                        text(defect.getSourceComponent()),
                        text(defect.getDefectType()),
                        truncate(defect.getDescription(), 180)))
                .toList();
        return new OperationalTruthDto.DefectSummary(recentDefects.size(), items);
    }

    private List<OperationalTruthDto.Blocker> blockers(List<TaskEntity> tasks,
                                                       List<WishlistEntity> wishlist,
                                                       List<PrReviewEntity> reviews,
                                                       String systemStatus,
                                                       DuplicateContent duplicateContent,
                                                       Map<UUID, List<JulesSessionEntity>> sessionsByTask,
                                                       Map<UUID, List<PrReviewEntity>> reviewsBySession) {
        List<OperationalTruthDto.Blocker> blockers = new ArrayList<>();
        if (isTrustBlockingSystemStatus(systemStatus)) {
            blockers.add(new OperationalTruthDto.Blocker(
                    "system_status", "high", "system", "System stop condition",
                    "System status is `" + systemStatus + "`, so throughput claims need investigation."));
        }
        if (duplicateContent.duplicated()) {
            blockers.add(new OperationalTruthDto.Blocker(
                    "duplicate_content", "high", "tasks", "Generated task duplication",
                    "The same task content appears " + duplicateContent.maxCount()
                            + " times in recent work; this is negative evidence for value flow."));
        }
        List<TaskEntity> blockedTasks = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.blocked)
                .limit(5)
                .toList();
        for (TaskEntity task : blockedTasks) {
            blockers.add(new OperationalTruthDto.Blocker(
                    "blocked_task", "medium", task.getId().toString(), TaskTitleBuilder.displayTitle(task),
                    truncate(task.getJulesDispatchStatus() == null ? task.getDescription() : task.getJulesDispatchStatus(), 180)));
        }
        long doneWithoutMergeEvidence = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.done)
                .filter(task -> !taskHasMergedReview(task, sessionsByTask, reviewsBySession))
                .count();
        if (doneWithoutMergeEvidence > 0) {
            blockers.add(new OperationalTruthDto.Blocker(
                    "done_without_delivery_evidence", "medium", "tasks", "Done is not delivery",
                    doneWithoutMergeEvidence + " done task(s) have no merged PR evidence in the local review graph."));
        }
        long failingReviews = reviews.stream()
                .filter(review -> REVIEW_FAILING_STATUSES.contains(normalize(review.getCiStatus())))
                .count();
        if (failingReviews > 0) {
            blockers.add(new OperationalTruthDto.Blocker(
                    "review_not_mergeable", "high", "pr_reviews", "PR evidence blocked",
                    failingReviews + " review(s) have failing/conflict/terminal non-delivery status."));
        }
        long pendingWishlist = wishlist.stream().filter(item -> item.getStatus() == WishlistStatus.pending).count();
        long movingTasks = tasks.stream()
                .filter(task -> Set.of(TaskStatus.queued, TaskStatus.claimed, TaskStatus.in_progress, TaskStatus.review, TaskStatus.pending_review)
                        .contains(task.getStatus()))
                .count();
        if (pendingWishlist > 0 && movingTasks == 0) {
            blockers.add(new OperationalTruthDto.Blocker(
                    "wishlist_waiting", "medium", "wishlist", "Wishlist not flowing",
                    pendingWishlist + " pending wishlist item(s) exist but no visible task flow is active."));
        }
        return blockers;
    }

    private OperationalTruthDto.BlockedValue blockedValue(List<OperationalTruthDto.Blocker> blockers) {
        String headline = blockers.isEmpty()
                ? "No value blocker is visible in the read-only operational model."
                : blockers.size() + " blocker(s) currently degrade value delivery or trust.";
        return new OperationalTruthDto.BlockedValue(blockers.size(), headline, blockers);
    }

    private OperationalTruthDto.Trust trust(OperationalTruthDto.EvidenceSummary evidence,
                                            List<OperationalTruthDto.Blocker> blockers,
                                            String systemStatus,
                                            DuplicateContent duplicateContent,
                                            List<DefectJournalEntity> recentDefects) {
        double score = 1.0;
        List<String> warnings = new ArrayList<>();
        List<String> positives = new ArrayList<>();
        if (evidence.mergedReviews() > 0) {
            positives.add(evidence.mergedReviews() + " merged PR review(s) provide strong delivery evidence.");
        }
        if (evidence.qualityGatePassed() > 0) {
            positives.add(evidence.qualityGatePassed() + " task(s) passed quality gates.");
        }
        if (!isTrustBlockingSystemStatus(systemStatus)) {
            positives.add("System status is " + (systemStatus == null || systemStatus.isBlank() ? "not set" : systemStatus) + ".");
        } else {
            score -= "content_defect".equals(normalize(systemStatus)) ? 0.35 : 0.25;
            warnings.add("System status is " + systemStatus + ".");
        }
        if (duplicateContent.duplicated()) {
            score -= 0.30;
            warnings.add("Recent task content duplication is active.");
        }
        if (evidence.failingReviews() > 0) {
            score -= 0.20;
            warnings.add(evidence.failingReviews() + " PR review(s) are failing, conflicted, or terminal non-delivery.");
        }
        if (evidence.qualityGateFailed() > 0) {
            score -= 0.15;
            warnings.add(evidence.qualityGateFailed() + " task(s) have failed quality-gate evidence.");
        }
        long highBlockers = blockers.stream().filter(blocker -> "high".equals(blocker.severity())).count();
        if (highBlockers > 0) {
            score -= Math.min(0.25, highBlockers * 0.10);
        }
        if (!recentDefects.isEmpty()) {
            score -= Math.min(0.15, recentDefects.size() * 0.02);
            warnings.add(recentDefects.size() + " defect-journal item(s) were recorded in the last 24h.");
        }
        double clamped = clamp(score);
        return new OperationalTruthDto.Trust(clamped, trustLevel(clamped), positives, warnings);
    }

    private List<OperationalTruthDto.InvariantStatus> invariants(
            ClientDeliverableReadinessService.Readiness readiness,
            List<TaskEntity> tasks,
            List<PrReviewEntity> reviews,
            String systemStatus,
            DuplicateContent duplicateContent,
            Map<UUID, List<JulesSessionEntity>> sessionsByTask,
            Map<UUID, List<PrReviewEntity>> reviewsBySession,
            List<DefectJournalEntity> recentDefects) {
        List<OperationalTruthDto.InvariantStatus> result = new ArrayList<>();
        boolean deliveryHasEvidence = readiness.completeFeatures() == 0 || readiness.mergedDeliverables() > 0;
        result.add(invariant("delivered_requires_evidence", deliveryHasEvidence ? "pass" : "warn",
                "delivered(x) requires checkable evidence(x)",
                deliveryHasEvidence ? "Readiness evidence is present or no feature is complete yet."
                        : "A complete feature is reported without merged deliverable evidence."));

        long doneWithoutMerge = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.done)
                .filter(task -> !taskHasMergedReview(task, sessionsByTask, reviewsBySession))
                .count();
        result.add(invariant("done_is_not_delivery", doneWithoutMerge == 0 ? "pass" : "warn",
                "task_done(x) is not substitutable for delivered_value(x)",
                doneWithoutMerge + " done task(s) lack local merged PR evidence."));

        long closedUnmerged = reviews.stream().filter(review -> "closed_unmerged".equals(normalize(review.getCiStatus()))).count();
        result.add(invariant("closed_unmerged_is_not_delivery", closedUnmerged == 0 ? "pass" : "observed",
                "closed_unmerged(pr) is terminal non-delivery evidence",
                closedUnmerged + " closed-unmerged review(s) are visible."));

        result.add(invariant("runtime_status_affects_trust", isTrustBlockingSystemStatus(systemStatus) ? "warn" : "pass",
                "bad_runtime_status -> degraded_trust",
                "system_stall_status=" + (systemStatus == null || systemStatus.isBlank() ? "unset" : systemStatus)));

        result.add(invariant("duplicate_content_blocks_throughput_trust", duplicateContent.duplicated() ? "warn" : "pass",
                "duplicate_content >= threshold -> stop_the_line_quality_defect",
                duplicateContent.duplicated()
                        ? "Max duplicate count in recent task content is " + duplicateContent.maxCount() + "."
                        : "No duplicate-content threshold breach found in recent tasks."));

        result.add(invariant("agent_claims_are_weak_evidence", "pass",
                "agent_claim(x) and no_artifact(x) -> unverified(x)",
                "Evidence algebra ranks Jules/session activity below merged PR and gate evidence."));

        result.add(invariant("defect_requires_invariant_capture", recentDefects.isEmpty() ? "pass" : "observed",
                "defect_fixed(x) and no_invariant(x) -> not_learned(x)",
                recentDefects.size() + " recent defect(s) should be checked for invariant/test/RAG capture."));
        return result;
    }

    private OperationalTruthDto.InvariantStatus invariant(String key, String status, String statement, String evidence) {
        return new OperationalTruthDto.InvariantStatus(key, status, statement, evidence);
    }

    private OperationalTruthDto.LearningSummary learning(List<DefectJournalEntity> recentDefects,
                                                         List<OperationalTruthDto.InvariantStatus> invariants) {
        List<String> unresolved = recentDefects.stream()
                .collect(Collectors.groupingBy(DefectJournalEntity::getDefectType, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> entry.getValue() + "x " + entry.getKey() + " should be reviewed for invariant/test/RAG capture.")
                .toList();
        int observed = (int) invariants.stream().filter(item -> !"not_evaluated".equals(item.status())).count();
        return new OperationalTruthDto.LearningSummary(recentDefects.size(), observed, unresolved);
    }

    private String recommendedNextAction(OperationalTruthDto.Delivery delivery,
                                         OperationalTruthDto.BlockedValue blockedValue,
                                         OperationalTruthDto.LearningSummary learning) {
        if (!blockedValue.blockers().isEmpty()) {
            OperationalTruthDto.Blocker blocker = blockedValue.blockers().stream()
                    .filter(item -> "high".equals(item.severity()))
                    .findFirst()
                    .orElse(blockedValue.blockers().get(0));
            return "Investigate " + blocker.title() + ": " + blocker.reason();
        }
        if ("decomposing".equals(delivery.status())) {
            return "Let the compiler/orchestration path finish turning wishlist scope into verifiable work.";
        }
        if ("building".equals(delivery.status())) {
            return "Continue delivery on unfinished features; count progress only when evidence reaches merge/readiness.";
        }
        if (learning.candidateDefects() > 0) {
            return "Convert recent defect observations into invariants, regression tests, or RAG entries.";
        }
        return "Keep the operational math layer in observe-only mode and monitor value evidence.";
    }

    private List<OperationalTruthDto.SourceOfTruthEntry> sourceOfTruth() {
        return List.of(
                sot("Task lifecycle", "ProjectFlowService / ClaimService / JulesDispatchService",
                        "Normalize activity into active flow and blockers."),
                sot("Wishlist lifecycle", "ProjectFlowService / TechnicalLeadCompiler / OpsAuditorService",
                        "Explain decomposition state without changing wishlist status."),
                sot("PR and merge truth", "AutoMergeService / GitHubPullRequestService",
                        "Rank merged PRs as strong delivery evidence."),
                sot("Delivery readiness", "ClientDeliverableReadinessService",
                        "Use canonical feature and planned-work readiness."),
                sot("Quality evidence", "GateOrchestrator",
                        "Aggregate gate pass/fail evidence."),
                sot("Defect memory", "DefectJournalService / KaizenService",
                        "Expose defects that still need invariant capture.")
        );
    }

    private OperationalTruthDto.SourceOfTruthEntry sot(String fact, String owner, String use) {
        return new OperationalTruthDto.SourceOfTruthEntry(fact, owner, use);
    }

    private List<OperationalTruthDto.PromotionRule> promotionPolicy() {
        return List.of(
                new OperationalTruthDto.PromotionRule("observe_only", "Compute and display only; no side effects."),
                new OperationalTruthDto.PromotionRule("warn_only", "Warn the operator but do not block work."),
                new OperationalTruthDto.PromotionRule("soft_gate", "Block optional/new work with explicit bypass."),
                new OperationalTruthDto.PromotionRule("hard_gate", "Block unsafe flow automatically after tests and false-positive review."),
                new OperationalTruthDto.PromotionRule("auto_remediate", "Execute narrow precondition-checked repair only after promotion.")
        );
    }

    private List<OperationalTruthDto.FrontendTranslation> frontendTranslations() {
        return List.of(
                new OperationalTruthDto.FrontendTranslation("completeFeatures / totalFeatures",
                        "What product capability has been delivered."),
                new OperationalTruthDto.FrontendTranslation("mergedPlannedTasks / totalPlannedTasks",
                        "How much planned implementation has reached merge evidence."),
                new OperationalTruthDto.FrontendTranslation("queue / active / review",
                        "What is currently moving."),
                new OperationalTruthDto.FrontendTranslation("blockers",
                        "What prevents value delivery or lowers trust."),
                new OperationalTruthDto.FrontendTranslation("defect journal",
                        "What the system still needs to convert into learning.")
        );
    }

    private DuplicateContent duplicateContent(List<TaskEntity> tasks) {
        Map<String, Long> counts = tasks.stream()
                .limit(30)
                .map(this::duplicateKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), HashMap::new, Collectors.counting()));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        return new DuplicateContent(max >= 3, max);
    }

    private String duplicateKey(TaskEntity task) {
        if (task.getPayload() != null) {
            String sliceTitle = task.getPayload().path("slice_title").asText("");
            if (!sliceTitle.isBlank()) {
                return sliceTitle;
            }
        }
        return task.getDescription();
    }

    private boolean taskHasMergedReview(TaskEntity task,
                                        Map<UUID, List<JulesSessionEntity>> sessionsByTask,
                                        Map<UUID, List<PrReviewEntity>> reviewsBySession) {
        return sessionsByTask.getOrDefault(task.getId(), List.of()).stream()
                .flatMap(session -> reviewsBySession.getOrDefault(session.getId(), List.of()).stream())
                .anyMatch(review -> Boolean.TRUE.equals(review.getMerged()));
    }

    private long countStatus(List<TaskEntity> tasks, TaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }

    private String systemStallStatus(Map<String, Object> systemStatus) {
        Object systemHealth = systemStatus.get("systemHealth");
        Object data = dataSection(systemHealth);
        if (data instanceof Map<?, ?> map) {
            Object status = map.get("status");
            return status == null ? "" : status.toString();
        }
        return "";
    }

    private Object dataSection(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("data")) {
            return map.get("data");
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private record DuplicateContent(boolean duplicated, long maxCount) {
    }
}
