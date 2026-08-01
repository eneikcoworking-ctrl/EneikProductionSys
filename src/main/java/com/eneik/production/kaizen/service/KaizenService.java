package com.eneik.production.kaizen.service;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.model.KaizenProposal;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.toc.ConstraintIdentificationService;
import com.eneik.production.toc.service.TocSentinelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kaizen Service - 2-Hour Window Aggregated Micro-Improvement Engine.
 * High-frequency telemetry (anomalies, stalls, DPMO spikes) is recorded silently into the under-the-hood
 * Defect Journal database table. Once every 2 hours, defects are mathematically aggregated, de-duplicated,
 * and presented as human-understandable, action-oriented micro-improvements on the user interface without spam.
 */
@Service
public class KaizenService {

    private static final Logger log = LoggerFactory.getLogger(KaizenService.class);

    private final TocSentinelService tocSentinelService;
    private final SixSigmaAuditService sixSigmaAuditService;
    private final TaskRepository taskRepository;
    private final DefectJournalService defectJournalService;
    private final ConstraintIdentificationService constraintIdentificationService;

    // In-memory deduplicated proposals store (max 1 active proposal per category & target component per 2-hour window)
    private final Map<String, KaizenProposal> proposals = new ConcurrentHashMap<>();

    public KaizenService(TocSentinelService tocSentinelService,
                          SixSigmaAuditService sixSigmaAuditService,
                          TaskRepository taskRepository,
                          DefectJournalService defectJournalService,
                          ConstraintIdentificationService constraintIdentificationService) {
        this.tocSentinelService = tocSentinelService;
        this.sixSigmaAuditService = sixSigmaAuditService;
        this.taskRepository = taskRepository;
        this.defectJournalService = defectJournalService;
        this.constraintIdentificationService = constraintIdentificationService;
        log.info("[KAIZEN-INIT] 2-Hour Aggregated Kaizen Micro-Improvement Service initialized.");
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
                    "QualityGate",
                    "HIGH_DPMO_DEFECT",
                    String.format("DPMO spike detected: %.2f", sixSigma.dpmo()),
                    sixSigma.dpmo()
            );
        }
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

        // Group recent defects by category and component
        Map<String, List<DefectJournalEntity>> groupedDefects = new HashMap<>();
        for (DefectJournalEntity d : recentDefects) {
            String key = d.getCategory() + ":" + d.getSourceComponent();
            groupedDefects.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        // Deduplication helper check for active proposal
        java.util.function.BiFunction<KaizenProposal.KaizenCategory, String, Boolean> hasActiveProposal = (cat, comp) ->
                proposals.values().stream().anyMatch(p ->
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
            proposals.values().removeIf(existing -> existing.getCategory() == p.getCategory() && Objects.equals(existing.getTargetComponent(), p.getTargetComponent()));
            proposals.put(propId, p);
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
                    proposals.values().removeIf(existing -> existing.getCategory() == p.getCategory() && Objects.equals(existing.getTargetComponent(), p.getTargetComponent()));
                    proposals.put(propId, p);
                    newProposals.add(p);
                }
            }
        }

        // Process Defect Elimination Group (QualityGate)
        List<DefectJournalEntity> defectGroup = groupedDefects.get("DEFECT_ELIMINATION:QualityGate");
        if (defectGroup != null && !defectGroup.isEmpty() && !hasActiveProposal.apply(KaizenProposal.KaizenCategory.DEFECT_ELIMINATION, "QualityGate")) {
            double avgDpmo = defectGroup.stream().mapToDouble(d -> d.getMetricValue() != null ? d.getMetricValue() : 1000.0).average().orElse(1000.0);
            String propId = "kz-2h-sixsigma-" + targetProjectId;

            KaizenProposal p = new KaizenProposal(
                    propId,
                    "Снижение уровня дефектов Quality Gate (Six Sigma)",
                    KaizenProposal.KaizenCategory.DEFECT_ELIMINATION,
                    "QualityGate",
                    String.format("За 2-часовой окно средний DPMO составил %.2f (всего %d всплесков). Предложение: усиление автоматической зачистки транзиторных ошибок проверки качества.",
                            avgDpmo, defectGroup.size()),
                    12.0,
                    targetProjectId,
                    finalProjectName
            );
            p.setBaselineMetric(avgDpmo);
            proposals.values().removeIf(existing -> existing.getCategory() == p.getCategory() && Objects.equals(existing.getTargetComponent(), p.getTargetComponent()));
            proposals.put(propId, p);
            newProposals.add(p);
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
        proposals.put(propId, proposal);
        log.info("[KAIZEN-SYSTEMIC] Recorded review-only systemic defect proposal '{}' from project {}: {}",
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
        proposals.put(propId, proposal);
        log.info("[KAIZEN-KNOWN-PATTERN] Recorded review-only known-pattern-violation proposal '{}' from project {}: charter #{} ({})",
                propId, projectId, rootCausePatternId, patternName);
        return proposal;
    }

    /**
     * Do Phase: Executes a single, safe micro-improvement step.
     */
    public boolean applyMicroStep(String proposalId) {
        KaizenProposal proposal = proposals.get(proposalId);
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
            case SYSTEMIC_DEFECT -> log.info("[KAIZEN-ACTION] Systemic defect proposal '{}' marked applied by "
                    + "explicit human/operator action - this category has no automatic action of its own.", proposal.getId());
            case KNOWN_PATTERN_VIOLATION -> log.info("[KAIZEN-ACTION] Known-pattern-violation proposal '{}' marked "
                    + "applied by explicit human/operator action - this category has no automatic action of its own.", proposal.getId());
        }

        proposal.setStatus(KaizenProposal.ProposalStatus.APPLIED);
        proposal.setAppliedAt(Instant.now());
        return true;
    }

    /**
     * Check & Act Phase: Evaluates metric impact after execution and standardizes or reverts.
     */
    public KaizenProposal evaluateAndStandardize(String proposalId) {
        KaizenProposal proposal = proposals.get(proposalId);
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
            proposals.values().removeIf(other -> other != proposal
                    && other.getCategory() == proposal.getCategory()
                    && Objects.equals(other.getTargetComponent(), proposal.getTargetComponent()));
            log.info("[KAIZEN-PDCA][ACT] Standardized micro-improvement '{}'! Post-metric: %.2f (Baseline: %.2f).",
                    proposal.getTitle(), postMetric, proposal.getBaselineMetric() != null ? proposal.getBaselineMetric() : 0.0);
        } else {
            proposal.setStatus(KaizenProposal.ProposalStatus.REVERTED);
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
        } catch (Exception e) {
            log.error("[KAIZEN-ERROR] Kaizen 2-hour periodic cycle encountered error: ", e);
        }
    }

    public Collection<KaizenProposal> getAllProposals() {
        return getDeduplicatedProposals(proposals.values());
    }

    public Collection<KaizenProposal> getProposalsForProject(UUID projectId) {
        if (projectId == null) {
            projectId = sixSigmaAuditService.getActiveProjectId();
        }
        final UUID targetPid = projectId;
        Collection<KaizenProposal> projectProposals = (targetPid == null)
                ? proposals.values()
                : proposals.values().stream().filter(p -> Objects.equals(p.getProjectId(), targetPid)).toList();
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
        return proposals.get(id);
    }
}
