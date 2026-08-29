package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.OperationalTruthDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TrustSignalSnapshotEntity;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TrustSignalSnapshotRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 2026-08-08 (ML-update patch, Phase 3 / lever D2_TRUST_SCORE_WEIGHTS): Stage-1 data collection only -
 * captures trust.score's real input signals periodically, and backfills each snapshot's real eventual
 * outcome once a project resolves (delivered, or frozen/archived without delivering). Deliberately does
 * NOT compute or apply any candidate weighting yet - Ramsey's subjective-probability program (BARCAN-TAG-04
 * philosopher 1) requires calibrating a degree of belief against real outcomes, which by definition don't
 * exist until this table has accumulated real resolved history. Runs on its own schedule, decoupled from
 * OperationalTruthService.build()'s own read path (same discipline as FlowSpineService's Phase 2 shadow
 * check) - build() stays a pure, fast, read-only computation with no new write side effect.
 */
@Service
public class TrustSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(TrustSnapshotService.class);
    private static final String DUPLICATE_CONTENT_INVARIANT_KEY = "duplicate_content_blocks_throughput_trust";

    private final ProjectRepository projectRepository;
    private final OperationalTruthService operationalTruthService;
    private final TrustSignalSnapshotRepository snapshotRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final com.eneik.production.repositories.InvariantStatusChangeRepository invariantChangeRepository;

    public TrustSnapshotService(ProjectRepository projectRepository,
                                 OperationalTruthService operationalTruthService,
                                 TrustSignalSnapshotRepository snapshotRepository,
                                 ClientDeliverableReadinessService readinessService,
                                 com.eneik.production.repositories.InvariantStatusChangeRepository invariantChangeRepository) {
        this.projectRepository = projectRepository;
        this.operationalTruthService = operationalTruthService;
        this.snapshotRepository = snapshotRepository;
        this.readinessService = readinessService;
        this.invariantChangeRepository = invariantChangeRepository;
    }

    @Scheduled(fixedRate = 7200000, initialDelay = 180000)
    @Transactional
    public void captureAndBackfillSnapshots() {
        for (ProjectEntity project : projectRepository.findAll()) {
            if (project.getStatus() != ProjectStatus.active) {
                continue;
            }
            try {
                captureSnapshot(project.getId());
            } catch (Exception e) {
                log.warn("[D2-SNAPSHOT] failed to capture trust snapshot for project {}: {}", project.getId(), e.getMessage());
            }
        }
        backfillResolvedOutcomes();
    }

    void captureSnapshot(UUID projectId) {
        OperationalTruthDto dto = operationalTruthService.build(projectId);
        boolean duplicateContent = dto.invariants().stream()
                .filter(inv -> DUPLICATE_CONTENT_INVARIANT_KEY.equals(inv.key()))
                .anyMatch(inv -> !"pass".equals(inv.status()));

        TrustSignalSnapshotEntity snapshot = new TrustSignalSnapshotEntity();
        snapshot.setProjectId(projectId);
        snapshot.setMergedReviews(dto.evidence().mergedReviews());
        snapshot.setQualityGatePassed(dto.evidence().qualityGatePassed());
        snapshot.setQualityGateFailed(dto.evidence().qualityGateFailed());
        snapshot.setFailingReviews(dto.evidence().failingReviews());
        snapshot.setDuplicateContent(duplicateContent);
        snapshot.setRecentDefectsCount(dto.defects().recentDefects());
        snapshot.setComputedScore(dto.trust().score());
        snapshotRepository.save(snapshot);

        recordInvariantTransitions(projectId, dto);
    }

    /**
     * 2026-08-20: the seven Charter invariants were evaluated here every two hours and discarded with the
     * rest of the DTO. Without a stored previous value a move from `pass` to `warn` is undetectable in
     * principle, so the one event that means "something this factory asserted about itself has stopped
     * being true" could not be acted on by anything at all.
     *
     * Recorded on TRANSITION ONLY. A repeated evaluation of an unchanged status is a confirmation, and
     * confirmations are free and unbounded - Popper's asymmetry is the whole reason this is worth storing.
     * Writing a row per evaluation would also repeat, at birth, the defect measured the same day in
     * KAIZEN_PROPOSALS: 347 rows carrying 10 distinct identities, because the write path had no identity
     * while the read path deduplicated (Charter invariant 4 belongs at the write).
     *
     * Reuses this existing two-hourly pass rather than adding a schedule: `build()` stays a pure read and
     * this is the one place that already calls it periodically.
     */
    private void recordInvariantTransitions(UUID projectId, OperationalTruthDto dto) {
        for (OperationalTruthDto.InvariantStatus invariant : dto.invariants()) {
            try {
                String previous = invariantChangeRepository
                        .findFirstByProjectIdAndInvariantKeyOrderByObservedAtDesc(projectId, invariant.key())
                        .map(com.eneik.production.models.persistence.InvariantStatusChangeEntity::getStatus)
                        .orElse(null);
                if (invariant.status() != null && invariant.status().equals(previous)) {
                    continue; // unchanged - a confirmation, not news
                }
                var change = new com.eneik.production.models.persistence.InvariantStatusChangeEntity();
                change.setProjectId(projectId);
                change.setInvariantKey(invariant.key());
                change.setStatus(invariant.status());
                change.setPreviousStatus(previous);
                change.setStatement(truncate(invariant.statement(), 500));
                change.setEvidence(truncate(invariant.evidence(), 2000));
                change.setObservedAt(java.time.Instant.now());
                invariantChangeRepository.save(change);
                log.info("[INVARIANT] project {} - {} : {} -> {} ({})",
                        projectId, invariant.key(), previous == null ? "<first>" : previous,
                        invariant.status(), invariant.evidence());
            } catch (Exception e) {
                log.warn("[INVARIANT] could not record {} for project {}: {}",
                        invariant.key(), projectId, e.getMessage());
            }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    void backfillResolvedOutcomes() {
        // One readiness computation per project per pass (2026-08-29). computeForProject() takes the
        // PROJECT, so calling it inside this loop recomputed the same answer once per snapshot. The set
        // iterated here is every snapshot whose outcome is still open, and a snapshot stays open until its
        // project is delivered or frozen while two more are written every hour - so the set grows linearly
        // with the age of a live project and the cost of this pass grows with it, without bound. Measured
        // that day on the live circuit: 50 snapshots, 0 resolved, one active project of 387 tasks - fifty
        // full readiness computations to obtain one answer fifty times, and the connection this pass holds
        // was reported leaked at the 30-second threshold. The map lives for exactly one pass.
        Map<UUID, ClientDeliverableReadinessService.Readiness> passReadiness = new HashMap<>();
        for (TrustSignalSnapshotEntity snapshot : snapshotRepository.findByEventualOutcomeIsNull()) {
            ProjectEntity project = projectRepository.findById(snapshot.getProjectId()).orElse(null);
            if (project == null) {
                continue;
            }
            var readiness = passReadiness.computeIfAbsent(
                    snapshot.getProjectId(), readinessService::computeForProject);
            if (readiness.totalFeatures() > 0 && readiness.completeFeatures() >= readiness.totalFeatures()) {
                resolve(snapshot, "delivered");
            } else if (project.getStatus() == ProjectStatus.frozen || project.getStatus() == ProjectStatus.archived) {
                resolve(snapshot, "abandoned");
            }
            // Otherwise still open/undetermined - left null, re-checked next cycle.
        }
    }

    private void resolve(TrustSignalSnapshotEntity snapshot, String outcome) {
        snapshot.setEventualOutcome(outcome);
        snapshot.setOutcomeRecordedAt(Instant.now());
        snapshotRepository.save(snapshot);
    }
}
