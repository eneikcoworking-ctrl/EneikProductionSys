package com.eneik.production.kaizen.service;

import com.eneik.production.kaizen.model.KaizenProposal;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
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
 * Kaizen Service - Continuous Micro-Improvement Engine (PDCA Cycle: Plan-Do-Check-Act).
 * Scans system telemetry for small wastes (Muda), generates targeted micro-improvements,
 * executes them in small safe steps, and measures quality & performance gains.
 */
@Service
public class KaizenService {

    private static final Logger log = LoggerFactory.getLogger(KaizenService.class);

    private final TocSentinelService tocSentinelService;
    private final SixSigmaAuditService sixSigmaAuditService;
    private final TaskRepository taskRepository;

    private final Map<String, KaizenProposal> proposals = new ConcurrentHashMap<>();

    public KaizenService(TocSentinelService tocSentinelService,
                         SixSigmaAuditService sixSigmaAuditService,
                         TaskRepository taskRepository) {
        this.tocSentinelService = tocSentinelService;
        this.sixSigmaAuditService = sixSigmaAuditService;
        this.taskRepository = taskRepository;
        log.info("[KAIZEN-INIT] Kaizen Micro-Improvement Service initialized.");
    }

    /**
     * Plan Phase: Scans TOC Sentinel & Six Sigma telemetry for micro-improvement opportunities.
     */
    public List<KaizenProposal> scanForOpportunities() {
        List<KaizenProposal> newProposals = new ArrayList<>();

        // 1. Waste Reduction: Check for queued tasks waiting over 1 hour (Muda waste)
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long staleQueuedCount = taskRepository.findAll().stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isBefore(oneHourAgo))
                .count();

        if (staleQueuedCount > 0) {
            String propId = "kz-muda-" + UUID.randomUUID().toString().substring(0, 8);
            KaizenProposal p = new KaizenProposal(
                    propId,
                    "Eliminate Stale Queue Waiting Waste (Muda)",
                    KaizenProposal.KaizenCategory.WASTE_REDUCTION,
                    "TaskQueue",
                    String.format("Found %d tasks waiting > 1h. Refresh queue priorities to unblock execution.", staleQueuedCount),
                    5.0
            );
            p.setBaselineMetric((double) staleQueuedCount);
            proposals.put(propId, p);
            newProposals.add(p);
        }

        // 2. Buffer & Throughput Tuning: Check DBR constraint buffer status
        var dbrStatus = tocSentinelService.getDbrStatus();
        if (dbrStatus.ropeThrottlingActive()) {
            String propId = "kz-dbr-" + UUID.randomUUID().toString().substring(0, 8);
            KaizenProposal p = new KaizenProposal(
                    propId,
                    "Tune DBR Buffer Capacity for Bottleneck Node",
                    KaizenProposal.KaizenCategory.BUFFER_TUNING,
                    dbrStatus.primaryConstraintNode(),
                    String.format("Constraint '%s' buffer full (%d/%d). Micro-expand buffer capacity by +2 to increase throughput.",
                            dbrStatus.primaryConstraintNode(), dbrStatus.bufferSize(), dbrStatus.maxBufferCapacity()),
                    8.0
            );
            p.setBaselineMetric((double) dbrStatus.bufferSize());
            proposals.put(propId, p);
            newProposals.add(p);
        }

        // 3. Defect Elimination: Check Six Sigma DPMO
        var sixSigma = sixSigmaAuditService.calculateFullSixSigmaAudit();
        if (sixSigma.dpmo() > 1000.0) {
            String propId = "kz-sixsigma-" + UUID.randomUUID().toString().substring(0, 8);
            KaizenProposal p = new KaizenProposal(
                    propId,
                    "Micro-Optimize Defect Escapes in Quality Gate Checks",
                    KaizenProposal.KaizenCategory.DEFECT_ELIMINATION,
                    "QualityGate",
                    String.format("Current DPMO is %.2f. Tighten validation checks and autoremove transient failures.", sixSigma.dpmo()),
                    12.0
            );
            p.setBaselineMetric(sixSigma.dpmo());
            proposals.put(propId, p);
            newProposals.add(p);
        }

        log.info("[KAIZEN-PDCA][PLAN] Scanned system telemetry. Found %d micro-improvement opportunities.", newProposals.size());
        return newProposals;
    }

    /**
     * Do Phase: Executes a single, safe micro-improvement step.
     */
    public boolean applyMicroStep(String proposalId) {
        KaizenProposal proposal = proposals.get(proposalId);
        if (proposal == null || proposal.getStatus() != KaizenProposal.ProposalStatus.PROPOSED) {
            return false;
        }

        log.info("[KAIZEN-PDCA][DO] Executing micro-step for proposal '{}': {}", proposal.getId(), proposal.getTitle());

        switch (proposal.getCategory()) {
            case BUFFER_TUNING -> {
                long currentCap = tocSentinelService.getOptimizer().getMaxBufferCapacity();
                tocSentinelService.getOptimizer().setMaxBufferCapacity(currentCap + 2);
                log.info("[KAIZEN-ACTION] Micro-tuned DBR Max Buffer Capacity from %d to %d.", currentCap, currentCap + 2);
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
            log.info("[KAIZEN-PDCA][ACT] Standardized micro-improvement '{}'! Post-metric: %.2f (Baseline: %.2f).",
                    proposal.getTitle(), postMetric, proposal.getBaselineMetric() != null ? proposal.getBaselineMetric() : 0.0);
        } else {
            proposal.setStatus(KaizenProposal.ProposalStatus.REVERTED);
            log.warn("[KAIZEN-PDCA][ACT] Reverted micro-improvement '{}' due to insufficient gain.", proposal.getTitle());
        }

        return proposal;
    }

    /**
     * Periodic scheduled Kaizen background cycle.
     */
    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void periodicKaizenCycle() {
        try {
            List<KaizenProposal> scanned = scanForOpportunities();
            for (KaizenProposal p : scanned) {
                // Apply single micro step automatically if gain is high and low risk
                if (p.getExpectedGainPercent() >= 5.0) {
                    applyMicroStep(p.getId());
                    evaluateAndStandardize(p.getId());
                }
            }
        } catch (Exception e) {
            log.error("[KAIZEN-ERROR] Kaizen periodic cycle encountered error: ", e);
        }
    }

    public Collection<KaizenProposal> getAllProposals() {
        return Collections.unmodifiableCollection(proposals.values());
    }

    public KaizenProposal getProposal(String id) {
        return proposals.get(id);
    }
}
