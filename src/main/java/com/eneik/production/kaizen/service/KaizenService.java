package com.eneik.production.kaizen.service;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.model.KaizenProposal;
import com.eneik.production.kaizen.model.KaizenProposalEntity;
import com.eneik.production.kaizen.repository.KaizenProposalRepository;
import com.eneik.production.models.persistence.EvidenceNodeEntity;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
import com.eneik.production.services.lever.LeverStage;
import com.eneik.production.services.toc.ConstraintIdentificationService;
import com.eneik.production.toc.service.TocSentinelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Kaizen Service - 2-Hour Window Aggregated Micro-Improvement Engine.
 * High-frequency telemetry (anomalies, stalls, DPMO spikes) is recorded silently into the under-the-hood
 * Defect Journal database table. Once every 2 hours, defects are mathematically aggregated, de-duplicated,
 * and presented as human-understandable, action-oriented micro-improvements on the user interface without spam.
 *
 * 2026-08-05: proposals moved off an in-memory ConcurrentHashMap onto {@link KaizenProposalRepository} - the
 * map was wiped on every backend restart (confirmed live: ~10 restarts in one night erased every
 * accumulated proposal with no trace beyond a log line). The public API (KaizenController,
 * ProjectTreeService) still works with the plain {@link KaizenProposal} domain object; conversion to/from
 * {@link KaizenProposalEntity} happens only inside this class.
 */
@Service
public class KaizenService {

    private static final Logger log = LoggerFactory.getLogger(KaizenService.class);

    private final TocSentinelService tocSentinelService;
    private final SixSigmaAuditService sixSigmaAuditService;
    private final TaskRepository taskRepository;
    private final DefectJournalService defectJournalService;
    private final ConstraintIdentificationService constraintIdentificationService;
    private final KaizenProposalRepository kaizenProposalRepository;
    private final EvidenceNodeRepository evidenceNodeRepository;
    private final LeverPromotionService leverPromotionService;
    private final com.eneik.production.repositories.WishlistRepository wishlistRepository;

    public static final String F1_KAIZEN_CTQ_TARGETING = "F1_KAIZEN_CTQ_TARGETING";

    public KaizenService(TocSentinelService tocSentinelService,
                          SixSigmaAuditService sixSigmaAuditService,
                          TaskRepository taskRepository,
                          DefectJournalService defectJournalService,
                          ConstraintIdentificationService constraintIdentificationService,
                          KaizenProposalRepository kaizenProposalRepository,
                          EvidenceNodeRepository evidenceNodeRepository,
                          LeverPromotionService leverPromotionService,
                          com.eneik.production.repositories.WishlistRepository wishlistRepository) {
        this.tocSentinelService = tocSentinelService;
        this.sixSigmaAuditService = sixSigmaAuditService;
        this.taskRepository = taskRepository;
        this.defectJournalService = defectJournalService;
        this.constraintIdentificationService = constraintIdentificationService;
        this.kaizenProposalRepository = kaizenProposalRepository;
        this.evidenceNodeRepository = evidenceNodeRepository;
        this.leverPromotionService = leverPromotionService;
        this.wishlistRepository = wishlistRepository;
        log.info("[KAIZEN-INIT] 2-Hour Aggregated Kaizen Micro-Improvement Service initialized.");
    }

    // ---- Persistence helpers (replace the old ConcurrentHashMap's role) ----

    private List<KaizenProposal> allProposals() {
        return kaizenProposalRepository.findAll().stream().map(KaizenProposalEntity::toDomain).toList();
    }

    private void saveProposal(KaizenProposal p) {
        kaizenProposalRepository.save(KaizenProposalEntity.fromDomain(p));
    }

    private Optional<KaizenProposal> findProposal(String id) {
        return kaizenProposalRepository.findById(id).map(KaizenProposalEntity::toDomain);
    }

    private void deleteMatching(KaizenProposal.KaizenCategory category, String targetComponent, String excludeId) {
        kaizenProposalRepository.findAll().stream()
                .filter(e -> category.name().equals(e.getCategory())
                        && Objects.equals(e.getTargetComponent(), targetComponent)
                        && !e.getId().equals(excludeId))
                .forEach(e -> kaizenProposalRepository.deleteById(e.getId()));
    }

    /**
     * Additive write to the shared evidence graph feeding EvidenceCoherenceService (Thagard/ECHO) - never
     * replaces the KaizenProposal write it accompanies. Polarity for SYSTEMIC_DEFECT/KNOWN_PATTERN_VIOLATION
     * is always NEGATIVE_FINDING regardless of status - these categories never go through automatic
     * applyMicroStep/evaluateAndStandardize (review-only, expectedGainPercent=0), so a status-based rule
     * would leave them permanently NEUTRAL even though they represent a confirmed problem by definition.
     * Other categories are asserted as evidence only once evaluateAndStandardize resolves them
     * (STANDARDIZED -> POSITIVE_CONFIRMATION, REVERTED -> NEGATIVE_FINDING) - a still-PROPOSED/APPLIED
     * micro-tuning proposal is speculative, not yet real evidence.
     */
    private void writeEvidenceNode(KaizenProposal p, EvidenceNodeEntity.Polarity polarity) {
        EvidenceNodeEntity node = new EvidenceNodeEntity();
        node.setProjectId(p.getProjectId());
        node.setPolarity(polarity);
        node.setSummaryText(p.getTitle() + ": " + p.getActionDescription());
        node.setKaizenProposalId(p.getId());
        evidenceNodeRepository.save(node);
    }

    /**
     * Silent Telemetry Ingestion: Records under-the-hood defect observations without spamming UI.
     */
    public void recordUnderTheHoodDefects(UUID projectId) {
        final UUID targetProjectId = (projectId != null) ? projectId : sixSigmaAuditService.getActiveProjectId();

        // 1. Check for stale tasks waiting > 1 hour (Muda waste)
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long staleQueuedCount = taskRepository.findAll().stream()
                .filter(t -> (targetProjectId == null || (t.getProject() != null && targetProjectId.equals(t.getProject().getId()))))
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isBefore(oneHourAgo))
                .count();

        if (staleQueuedCount > 0) {
            defectJournalService.recordDefect(
                    targetProjectId,
                    "MEDIUM",
                    "WASTE_REDUCTION",
                    "TaskQueue",
                    "STALE_QUEUE_WAITING",
                    String.format("Found %d tasks queued > 1h", staleQueuedCount),
                    (double) staleQueuedCount
            );
        }

        // 2. Check DBR buffer status
        var dbrStatus = tocSentinelService.getDbrStatus();
        if (dbrStatus.ropeThrottlingActive()) {
            defectJournalService.recordDefect(
                    targetProjectId,
                    "HIGH",
                    "BUFFER_TUNING",
                    dbrStatus.primaryConstraintNode(),
                    "DBR_BUFFER_FULL",
                    String.format("Constraint '%s' buffer full (%d/%d)", dbrStatus.primaryConstraintNode(), dbrStatus.bufferSize(), dbrStatus.maxBufferCapacity()),
                    (double) dbrStatus.bufferSize()
            );
        }

        // 3. Check Six Sigma DPMO
        var sixSigma = (targetProjectId != null)
                ? sixSigmaAuditService.calculateProjectSixSigmaAudit(targetProjectId)
                : sixSigmaAuditService.calculateFullSixSigmaAudit();

        if (sixSigma.dpmo() > 1000.0) {
            defectJournalService.recordDefect(
                    targetProjectId,
                    "HIGH",
                    "DEFECT_ELIMINATION",
                    resolveQualityGateComponent(targetProjectId),
                    "HIGH_DPMO_DEFECT",
                    String.format("DPMO spike detected: %.2f", sixSigma.dpmo()),
                    sixSigma.dpmo()
            );
        }
    }

    /**
     * 2026-08-08 (ML-update patch, Phase 1): incumbent is the old hardcoded "QualityGate" bucket - real
     * defects concentrate overwhelmingly in a small number of specific checks (Sober's parsimony/AIC-BIC,
     * BARCAN-TAG-04 philosopher 6), so the candidate targets the single dominant checkName instead. Records
     * one lever observation per cycle: ground truth here is self-contained (does the top check actually
     * account for a dominant share of this cycle's real defects, Mackie's INUS conditions, BARCAN-TAG-05
     * philosopher 2) rather than waiting on a full proposal-resolution round-trip, since DEFECT_ELIMINATION
     * proposals share one stable id per project and can't otherwise supply a fresh ground-truth sample every
     * cycle. Below soft_gate, returns the unchanged incumbent value - zero live behavior change.
     */
    private String resolveQualityGateComponent(UUID targetProjectId) {
        List<SixSigmaAuditService.CtqEntry> breakdown = sixSigmaAuditService.computeCtqBreakdown(targetProjectId);
        String incumbent = "QualityGate";
        if (breakdown.isEmpty()) {
            return incumbent;
        }
        SixSigmaAuditService.CtqEntry top = breakdown.get(0);
        long totalDefects = breakdown.stream().mapToLong(SixSigmaAuditService.CtqEntry::defects).sum();
        String candidate = top.checkName();

        LeverAgreement agreement = totalDefects > 0 && top.defects() / (double) totalDefects >= 0.5
                ? LeverAgreement.TRUE : LeverAgreement.FALSE;
        String subjectId = targetProjectId != null ? targetProjectId.toString() : "GLOBAL";
        leverPromotionService.recordObservation(F1_KAIZEN_CTQ_TARGETING, subjectId, incumbent, candidate,
                agreement, agreement == LeverAgreement.TRUE ? "dominant_ctq" : "no_dominant_ctq");

        boolean promoted = leverPromotionService.currentStage(F1_KAIZEN_CTQ_TARGETING).atLeast(LeverStage.SOFT_GATE);
        return promoted ? candidate : incumbent;
    }

    /**
     * Mathematical Aggregation & De-duplication: Generates human-understandable 2-hour Kaizen proposals.
     */
    public List<KaizenProposal> scanForOpportunities() {
        return scanForOpportunities(null);
    }

    public List<KaizenProposal> scanForOpportunities(UUID projectId) {
        final UUID targetProjectId = (projectId != null) ? projectId : sixSigmaAuditService.getActiveProjectId();

        // 1. Record latest telemetry silently under the hood
        recordUnderTheHoodDefects(targetProjectId);

        // 2. Retrieve defects from the last 2 hours (2-hour window)
        List<DefectJournalEntity> recentDefects = defectJournalService.getDefectsInWindow(targetProjectId, 2);

        String projectName = "Global";
        if (targetProjectId != null) {
            var projectOpt = taskRepository.findAll().stream()
                    .filter(t -> t.getProject() != null && targetProjectId.equals(t.getProject().getId()))
                    .map(t -> t.getProject().getName())
                    .findFirst();
            if (projectOpt.isPresent()) projectName = projectOpt.get();
        }

        final String finalProjectName = projectName;
        List<KaizenProposal> newProposals = new ArrayList<>();
        List<KaizenProposal> current = allProposals();

        // Group recent defects by category and component
        Map<String, List<DefectJournalEntity>> groupedDefects = new HashMap<>();
        for (DefectJournalEntity d : recentDefects) {
            String key = d.getCategory() + ":" + d.getSourceComponent();
            groupedDefects.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        // Deduplication helper check for active proposal
        java.util.function.BiFunction<KaizenProposal.KaizenCategory, String, Boolean> hasActiveProposal = (cat, comp) ->
                current.stream().anyMatch(p ->
                        Objects.equals(p.getProjectId(), targetProjectId) &&
                        p.getCategory() == cat &&
                        Objects.equals(p.getTargetComponent(), comp) &&
                        (p.getStatus() == KaizenProposal.ProposalStatus.PROPOSED || p.getStatus() == KaizenProposal.ProposalStatus.APPLIED)
                );

        // Process Waste Reduction Group (TaskQueue)
        List<DefectJournalEntity> wasteGroup = groupedDefects.get("WASTE_REDUCTION:TaskQueue");
        if (wasteGroup != null && !wasteGroup.isEmpty() && !hasActiveProposal.apply(KaizenProposal.KaizenCategory.WASTE_REDUCTION, "TaskQueue")) {
            double avgStale = wasteGroup.stream().mapToDouble(d -> d.getMetricValue() != null ? d.getMetricValue() : 1.0).average().orElse(1.0);
            String propId = "kz-2h-muda-" + targetProjectId;

            KaizenProposal p = new KaizenProposal(
                    propId,
                    "Устранение потерь очереди (Muda)",
                    KaizenProposal.KaizenCategory.WASTE_REDUCTION,
                    "TaskQueue",
                    String.format("За последние 2 часа зафиксировано %d сигналов простоя очереди (в среднем %.1f задач в ожидании > 1ч). Предложение: автоматический пересчет и оптимизация приоритетов задач.",
                            wasteGroup.size(), avgStale),
                    6.5,
                    targetProjectId,
                    finalProjectName
            );
            p.setBaselineMetric(avgStale);
            deleteMatching(p.getCategory(), p.getTargetComponent(), propId);
            saveProposal(p);
            newProposals.add(p);
        }

        // Process Buffer Tuning Group (DBR Bottleneck)
        for (Map.Entry<String, List<DefectJournalEntity>> entry : groupedDefects.entrySet()) {
            if (entry.getKey().startsWith("BUFFER_TUNING:")) {
                String component = entry.getKey().substring("BUFFER_TUNING:".length());
                if (!hasActiveProposal.apply(KaizenProposal.KaizenCategory.BUFFER_TUNING, component)) {
                    List<DefectJournalEntity> bufList = entry.getValue();
                    double avgBuf = bufList.stream().mapToDouble(d -> d.getMetricValue() != null ? d.getMetricValue() : 0.0).average().orElse(0.0);
                    String propId = "kz-2h-dbr-" + component.replaceAll("[^a-zA-Z0-9-]", "");

                    KaizenProposal p = new KaizenProposal(
                            propId,
                            String.format("Точечная настройка буфера DBR: %s", component),
                            KaizenProposal.KaizenCategory.BUFFER_TUNING,
                            component,
                            String.format("На узле-ограничении '%s' за 2 часа зафиксировано %d перегрузок буфера. Предложение: аккуратное точечное расширение емкости буфера на +2 единицы для выравнивания потока.",
                                    component, bufList.size()),
                            8.5,
                            targetProjectId,
                            finalProjectName
                    );
                    p.setBaselineMetric(avgBuf);
                    deleteMatching(p.getCategory(), p.getTargetComponent(), propId);
                    saveProposal(p);
                    newProposals.add(p);
                }
            }
        }

        // Process Defect Elimination Group (QualityGate). 2026-08-08 (ML-update patch, Phase 1): grouped by
        // CATEGORY alone, not "DEFECT_ELIMINATION:QualityGate" - resolveQualityGateComponent's candidate
        // path writes a specific checkName as sourceComponent once F1_KAIZEN_CTQ_TARGETING is promoted past
        // soft_gate, so an exact "QualityGate" key match would silently stop finding these entries the
        // moment that happens.
        List<DefectJournalEntity> defectGroup = recentDefects.stream()
                .filter(d -> "DEFECT_ELIMINATION".equals(d.getCategory()))
                .toList();
        if (!defectGroup.isEmpty()) {
            String resolvedComponent = defectGroup.get(defectGroup.size() - 1).getSourceComponent();
            if (!hasActiveProposal.apply(KaizenProposal.KaizenCategory.DEFECT_ELIMINATION, resolvedComponent)) {
                double avgDpmo = defectGroup.stream().mapToDouble(d -> d.getMetricValue() != null ? d.getMetricValue() : 1000.0).average().orElse(1000.0);
                String propId = "kz-2h-sixsigma-" + targetProjectId;
                boolean targeted = !"QualityGate".equals(resolvedComponent);

                KaizenProposal p = new KaizenProposal(
                        propId,
                        targeted ? "Снижение уровня дефектов проверки '" + resolvedComponent + "' (Six Sigma)"
                                : "Снижение уровня дефектов Quality Gate (Six Sigma)",
                        KaizenProposal.KaizenCategory.DEFECT_ELIMINATION,
                        resolvedComponent,
                        targeted
                                ? String.format("За 2-часовой окно средний DPMO составил %.2f (всего %d всплесков). Доминирующая проверка: '%s'. Предложение: точечное устранение причины именно этой проверки.",
                                        avgDpmo, defectGroup.size(), resolvedComponent)
                                : String.format("За 2-часовой окно средний DPMO составил %.2f (всего %d всплесков). Предложение: усиление автоматической зачистки транзиторных ошибок проверки качества.",
                                        avgDpmo, defectGroup.size()),
                        12.0,
                        targetProjectId,
                        finalProjectName
                );
                p.setBaselineMetric(avgDpmo);
                deleteMatching(p.getCategory(), p.getTargetComponent(), propId);
                saveProposal(p);
                newProposals.add(p);
            }
        }

        // Process Role Quality Drift Group (2026-08-07, DMAIC Control-phase wiring) - scoped to one real
        // project's own history (targetProjectId != null), unlike the 3 groups above which can aggregate
        // globally from DefectJournalEntity: a role's inherent difficulty varies by domain, so drift is
        // only meaningful against that SAME project's own past, not a cross-project average.
        if (targetProjectId != null) {
            for (var drift : sixSigmaAuditService.detectRoleDefectWeightDrift(targetProjectId)) {
                String component = drift.roleTag();
                if (hasActiveProposal.apply(KaizenProposal.KaizenCategory.ROLE_QUALITY_DRIFT, component)) {
                    continue;
                }
                newProposals.add(recordRoleQualityDriftProposal(targetProjectId, finalProjectName, drift));
            }
        }

        log.info("[KAIZEN-PDCA][PLAN-2H] 2-Hour window analysis completed for {}. Generated {} clean deduplicated proposal(s).", finalProjectName, newProposals.size());
        return newProposals;
    }

    /**
     * External entry point (2026-08-01) for a finding about the orchestrator/factory's OWN code, not the
     * client project - GeminiProjectObserverService routes any self-referential finding here instead of
     * the normal wishlist pipeline (which would otherwise dispatch Jules against the CLIENT project's repo
     * to "fix" something that only exists in EneikProductionSys' own source - confirmed live, several
     * gemini_observer wishlists like "Fix pending_review state transition logic" were unfixable no-ops for
     * exactly this reason). expectedGainPercent is fixed at 0 so periodicKaizenCycle's auto-apply loop
     * (">= 5.0" threshold) never fires for this category - review and any resulting code change stay
     * human/Claude-only, matching operator directive ("им занимаемся только мы с тобой").
     */
    public KaizenProposal recordSystemicDefectProposal(java.util.UUID projectId, String projectName,
                                                         String title, String actionDescription) {
        String propId = "kz-systemic-" + java.util.UUID.randomUUID();
        KaizenProposal proposal = new KaizenProposal(
                propId,
                title,
                KaizenProposal.KaizenCategory.SYSTEMIC_DEFECT,
                "EneikProductionSys",
                actionDescription,
                0.0,
                projectId,
                projectName == null ? "Global" : projectName
        );
        saveProposal(proposal);
        writeEvidenceNode(proposal, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        log.info("[KAIZEN-SYSTEMIC] Recorded review-only systemic defect proposal '{}' from project {}: {}",
                propId, projectId, title);
        return proposal;
    }

    /**
     * Phase 3 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md - the direct entry point
     * for a RuntimeHealthShiftDetector-confirmed real shift in the active product's own runtime
     * behavior. Deliberately parallel to, not merged with, recordSystemicDefectProposal above: that one
     * is about THIS codebase's own defects, this one is about the delivered PRODUCT's - see
     * KaizenProposal.KaizenCategory.PRODUCT_RUNTIME_DEFECT's own javadoc for why they must stay visibly
     * separate. Same safety shape: expectedGainPercent fixed at 0.0, review-only, never auto-applied.
     */
    public KaizenProposal recordProductRuntimeDefectProposal(java.util.UUID projectId, String projectName,
                                                               String title, String actionDescription) {
        // Idempotency guard: an ongoing anomaly (still PROPOSED, not yet reviewed) must not spawn a fresh
        // duplicate proposal every time a later observation confirms it's still shifted - same class of
        // fix as the philosophical-audit/formal-audit dedup guards elsewhere in this codebase tonight.
        boolean alreadyOpen = kaizenProposalRepository.findByProjectId(projectId).stream()
                .anyMatch(p -> "PRODUCT_RUNTIME_DEFECT".equals(p.getCategory()) && "PROPOSED".equals(p.getStatus()));
        if (alreadyOpen) {
            log.info("[KAIZEN-PRODUCT-RUNTIME] Project {} already has an open product runtime defect proposal; not duplicating", projectId);
            return null;
        }
        String propId = "kz-product-runtime-" + java.util.UUID.randomUUID();
        KaizenProposal proposal = new KaizenProposal(
                propId,
                title,
                KaizenProposal.KaizenCategory.PRODUCT_RUNTIME_DEFECT,
                projectName == null ? "active product" : projectName,
                actionDescription,
                0.0,
                projectId,
                projectName == null ? "Global" : projectName
        );
        saveProposal(proposal);
        writeEvidenceNode(proposal, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        log.info("[KAIZEN-PRODUCT-RUNTIME] Recorded review-only product runtime defect proposal '{}' from project {}: {}",
                propId, projectId, title);
        return proposal;
    }

    /**
     * External entry point (2026-08-01, Layer 4 loop-closing) for a u-chart out-of-control signal
     * (ProcessControlService) whose underlying defect events already carry a rootCausePatternId matching
     * one of the 12 documented patterns in docs/ENGINEERING_INVARIANTS_CHARTER.md - review-only, same
     * boundary as recordSystemicDefectProposal (expectedGainPercent fixed at 0, never auto-applied).
     */
    public KaizenProposal recordKnownPatternViolationProposal(java.util.UUID projectId, String projectName,
                                                                int rootCausePatternId, String patternName,
                                                                String title, String actionDescription) {
        String propId = "kz-pattern-" + java.util.UUID.randomUUID();
        String fullDescription = String.format(
                "Charter pattern #%d (%s, docs/ENGINEERING_INVARIANTS_CHARTER.md): %s",
                rootCausePatternId, patternName, actionDescription);
        KaizenProposal proposal = new KaizenProposal(
                propId,
                title,
                KaizenProposal.KaizenCategory.KNOWN_PATTERN_VIOLATION,
                "EneikProductionSys",
                fullDescription,
                0.0,
                projectId,
                projectName == null ? "Global" : projectName
        );
        saveProposal(proposal);
        writeEvidenceNode(proposal, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        log.info("[KAIZEN-KNOWN-PATTERN] Recorded review-only known-pattern-violation proposal '{}' from project {}: charter #{} ({})",
                propId, projectId, rootCausePatternId, patternName);
        return proposal;
    }

    /**
     * External entry point (2026-08-07, DMAIC Control-phase wiring) for a role's own ems_defect_weight
     * trending upward within one project's history (SixSigmaAuditService.detectRoleDefectWeightDrift) -
     * review-only, same boundary as recordSystemicDefectProposal/recordKnownPatternViolationProposal
     * (expectedGainPercent fixed at 0, never auto-applied): a real quality trend needs human/Claude
     * judgment on cause, not an automatic runtime-parameter tweak.
     */
    public KaizenProposal recordRoleQualityDriftProposal(UUID projectId, String projectName,
                                                            com.eneik.production.services.audit.SixSigmaAuditService.RoleQualityDrift drift) {
        String propId = "kz-drift-" + projectId + "-" + drift.roleTag();
        String description = String.format(
                "Role %s's own defect-weight average rose from %.2f (%d earlier task%s) to %.2f (%d recent task%s) "
                        + "within this project's own history - a %.0f%% increase, not a one-off failure. "
                        + "Worth checking whether something about how this role is being briefed/executed has degraded.",
                drift.roleTag(), drift.historicalAverage(), drift.historicalSampleSize(),
                drift.historicalSampleSize() == 1 ? "" : "s", drift.recentAverage(), drift.recentSampleSize(),
                drift.recentSampleSize() == 1 ? "" : "s",
                (drift.recentAverage() / drift.historicalAverage() - 1.0) * 100.0);
        KaizenProposal proposal = new KaizenProposal(
                propId,
                "Role quality drift: " + drift.roleTag(),
                KaizenProposal.KaizenCategory.ROLE_QUALITY_DRIFT,
                drift.roleTag(),
                description,
                0.0,
                projectId,
                projectName == null ? "Global" : projectName
        );
        saveProposal(proposal);
        writeEvidenceNode(proposal, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        log.info("[KAIZEN-ROLE-DRIFT] Recorded review-only role-quality-drift proposal '{}' for project {}: role {} {} -> {}",
                propId, projectId, drift.roleTag(), drift.historicalAverage(), drift.recentAverage());
        return proposal;
    }

    /**
     * Do Phase: Executes a single, safe micro-improvement step.
     */
    public boolean applyMicroStep(String proposalId) {
        KaizenProposal proposal = findProposal(proposalId).orElse(null);
        if (proposal == null || proposal.getStatus() != KaizenProposal.ProposalStatus.PROPOSED) {
            return false;
        }

        log.info("[KAIZEN-PDCA][DO] Executing 2-hour micro-step for proposal '{}': {}", proposal.getId(), proposal.getTitle());

        switch (proposal.getCategory()) {
            case BUFFER_TUNING -> {
                long currentCap = tocSentinelService.getOptimizer().getMaxBufferCapacity();
                UUID targetProjectId = proposal.getProjectId() != null
                        ? proposal.getProjectId() : sixSigmaAuditService.getActiveProjectId();
                long newCap = currentCap + 2; // safe floor if this project has no measured task-cycle variance yet
                if (targetProjectId != null) {
                    var recommendation = constraintIdentificationService.recommendedBufferCapacity(targetProjectId, 3.0);
                    // Never shrink capacity while responding to a buffer-full alert - the variance-based
                    // number replaces the ARBITRARINESS of "+2", not the guarantee that this step makes
                    // forward progress.
                    newCap = Math.max(currentCap + 1, recommendation.bufferCapacity());
                    log.info("[KAIZEN-ACTION] project={} measured task-cycle-time stdDev={}s over {} samples, throughput={}/s -> recommended buffer={}",
                            targetProjectId, String.format("%.1f", recommendation.stdDevCycleTimeSeconds()),
                            recommendation.sampleSize(), String.format("%.4f", recommendation.throughputPerSecond()),
                            recommendation.bufferCapacity());
                }
                tocSentinelService.getOptimizer().setMaxBufferCapacity(newCap);
                log.info("[KAIZEN-ACTION] Micro-tuned DBR Max Buffer Capacity from {} to {} (variance-based, not a hardcoded increment).", currentCap, newCap);
            }
            case WASTE_REDUCTION -> {
                log.info("[KAIZEN-ACTION] Refreshing task queue priorities to eliminate waiting waste.");
            }
            case DEFECT_ELIMINATION -> {
                log.info("[KAIZEN-ACTION] Applied transient defect cleanup policy.");
            }
            case SPEED_OPTIMIZATION -> {
                log.info("[KAIZEN-ACTION] Micro-tuned dynamic timeout sensitivity.");
            }
            // 2026-08-11 (design shop Stage 2.5): the reviewConcerns u-chart stream is the ONE
            // SYSTEMIC_DEFECT source with a real, bounded, autonomous next step already built - see
            // ProjectFlowService.dispatchDesignConcernTriage. A statistically confirmed pattern of design
            // review concerns becomes real backlog work, same as any other already-verified fact in this
            // codebase (runtime_observability_gap's own precedent). Every OTHER SYSTEMIC_DEFECT source
            // still has no autonomous action - deliberately not widening this beyond the one gate that
            // actually has a real, tested consumer for its output.
            case SYSTEMIC_DEFECT -> {
                if (proposal.getTitle() != null
                        && proposal.getTitle().contains(com.eneik.production.services.quality.ProcessControlService.STREAM_REVIEW_CONCERNS)
                        && proposal.getProjectId() != null) {
                    com.eneik.production.models.persistence.WishlistEntity wishlist =
                            new com.eneik.production.models.persistence.WishlistEntity();
                    wishlist.setProjectId(proposal.getProjectId());
                    wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.design_review_concern_pattern);
                    wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
                    wishlist.setLeanValue(com.eneik.production.models.persistence.LeanValue.valuable);
                    wishlist.setContent("Statistically confirmed pattern of design review concerns (Six Sigma "
                            + "u-chart out of control): " + proposal.getActionDescription());
                    wishlist.setJtbd("When design review concerns keep recurring beyond normal variance for "
                            + proposal.getProjectName() + ", I want the underlying pattern addressed, so that "
                            + "future reviews stop flagging the same class of issue");
                    wishlist.setSixSigmaMetric(com.eneik.production.services.quality.ProcessControlService.STREAM_REVIEW_CONCERNS);
                    wishlistRepository.save(wishlist);
                    log.info("[KAIZEN-ACTION] Systemic defect proposal '{}' (reviewConcerns) escalated to a real "
                            + "wishlist item for project {} - no human action required.", proposal.getId(), proposal.getProjectId());
                } else {
                    log.info("[KAIZEN-ACTION] Systemic defect proposal '{}' marked applied by "
                            + "explicit human/operator action - this category has no automatic action of its own.", proposal.getId());
                }
            }
            case KNOWN_PATTERN_VIOLATION -> log.info("[KAIZEN-ACTION] Known-pattern-violation proposal '{}' marked "
                    + "applied by explicit human/operator action - this category has no automatic action of its own.", proposal.getId());
        }

        proposal.setStatus(KaizenProposal.ProposalStatus.APPLIED);
        proposal.setAppliedAt(Instant.now());
        saveProposal(proposal);
        return true;
    }

    /**
     * Check & Act Phase: Evaluates metric impact after execution and standardizes or reverts.
     */
    public KaizenProposal evaluateAndStandardize(String proposalId) {
        KaizenProposal proposal = findProposal(proposalId).orElse(null);
        if (proposal == null || proposal.getStatus() != KaizenProposal.ProposalStatus.APPLIED) {
            return proposal;
        }

        double postMetric = 0.0;
        boolean improved = false;

        switch (proposal.getCategory()) {
            case BUFFER_TUNING -> {
                postMetric = (double) tocSentinelService.getDbrStatus().bufferSize();
                improved = postMetric <= (proposal.getBaselineMetric() != null ? proposal.getBaselineMetric() : 100.0);
            }
            case DEFECT_ELIMINATION -> {
                postMetric = sixSigmaAuditService.calculateFullSixSigmaAudit().dpmo();
                improved = postMetric <= (proposal.getBaselineMetric() != null ? proposal.getBaselineMetric() : 1000.0);
            }
            default -> improved = true;
        }

        proposal.setPostMetric(postMetric);

        if (improved) {
            proposal.setStatus(KaizenProposal.ProposalStatus.STANDARDIZED);
            saveProposal(proposal);
            deleteMatching(proposal.getCategory(), proposal.getTargetComponent(), proposal.getId());
            writeEvidenceNode(proposal, EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION);
            log.info("[KAIZEN-PDCA][ACT] Standardized micro-improvement '{}'! Post-metric: {} (Baseline: {}).",
                    proposal.getTitle(), postMetric, proposal.getBaselineMetric() != null ? proposal.getBaselineMetric() : 0.0);
        } else {
            proposal.setStatus(KaizenProposal.ProposalStatus.REVERTED);
            saveProposal(proposal);
            writeEvidenceNode(proposal, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
            log.warn("[KAIZEN-PDCA][ACT] Reverted micro-improvement '{}' due to insufficient gain.", proposal.getTitle());
        }

        return proposal;
    }

    /**
     * Periodic Kaizen background cycle running ONCE EVERY 2 HOURS (7,200,000 ms).
     */
    @Scheduled(fixedRate = 7200000, initialDelay = 60000) // Every 2 hours
    public void periodicKaizenCycle() {
        try {
            List<KaizenProposal> scanned = scanForOpportunities();
            for (KaizenProposal p : scanned) {
                if (p.getExpectedGainPercent() >= 5.0) {
                    applyMicroStep(p.getId());
                    evaluateAndStandardize(p.getId());
                }
            }
            applyAutonomouslyActionableSystemicDefects();
        } catch (Exception e) {
            log.error("[KAIZEN-ERROR] Kaizen 2-hour periodic cycle encountered error: ", e);
        }
    }

    // 2026-08-11 (design shop Stage 2.5): SYSTEMIC_DEFECT proposals are created with
    // expectedGainPercent=0.0 (review-only by default, see recordSystemicDefectProposal's own doc) so
    // they never accidentally enter the >=5.0 auto-apply loop above - deliberately, since most
    // SYSTEMIC_DEFECT sources genuinely have no automatic action. scanForOpportunities also only ever
    // returns FRESHLY-scanned defect-journal proposals, never these (created independently by
    // ProcessControlService), so they would never otherwise reach applyMicroStep at all. This is the
    // one narrow, separate path for the one SYSTEMIC_DEFECT source (reviewConcerns) that DOES have a
    // real, bounded, autonomous next step - see the SYSTEMIC_DEFECT case in applyMicroStep.
    private void applyAutonomouslyActionableSystemicDefects() {
        for (KaizenProposal p : allProposals()) {
            if (p.getStatus() == KaizenProposal.ProposalStatus.PROPOSED
                    && p.getCategory() == KaizenProposal.KaizenCategory.SYSTEMIC_DEFECT
                    && p.getTitle() != null
                    && p.getTitle().contains(com.eneik.production.services.quality.ProcessControlService.STREAM_REVIEW_CONCERNS)
                    && p.getProjectId() != null) {
                applyMicroStep(p.getId());
            }
        }
    }

    public Collection<KaizenProposal> getAllProposals() {
        return getDeduplicatedProposals(allProposals());
    }

    public Collection<KaizenProposal> getProposalsForProject(UUID projectId) {
        if (projectId == null) {
            projectId = sixSigmaAuditService.getActiveProjectId();
        }
        final UUID targetPid = projectId;
        Collection<KaizenProposal> projectProposals = (targetPid == null)
                ? allProposals()
                : allProposals().stream().filter(p -> Objects.equals(p.getProjectId(), targetPid)).toList();
        return getDeduplicatedProposals(projectProposals);
    }

    private Collection<KaizenProposal> getDeduplicatedProposals(Collection<KaizenProposal> inputProposals) {
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        Map<String, KaizenProposal> deduplicatedMap = new LinkedHashMap<>();
        for (KaizenProposal p : inputProposals) {
            // Exclude obsolete standardized/reverted proposals older than 2 hours
            if ((p.getStatus() == KaizenProposal.ProposalStatus.STANDARDIZED || p.getStatus() == KaizenProposal.ProposalStatus.REVERTED)
                    && p.getCreatedAt() != null && p.getCreatedAt().isBefore(twoHoursAgo)) {
                continue;
            }
            String key = p.getCategory() + ":" + p.getTargetComponent();
            KaizenProposal existing = deduplicatedMap.get(key);
            if (existing == null || p.getCreatedAt().isAfter(existing.getCreatedAt())) {
                deduplicatedMap.put(key, p);
            }
        }
        return Collections.unmodifiableCollection(deduplicatedMap.values());
    }

    public KaizenProposal getProposal(String id) {
        return findProposal(id).orElse(null);
    }
}
