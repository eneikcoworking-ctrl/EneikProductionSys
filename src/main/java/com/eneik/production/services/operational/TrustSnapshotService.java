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

    public TrustSnapshotService(ProjectRepository projectRepository,
                                 OperationalTruthService operationalTruthService,
                                 TrustSignalSnapshotRepository snapshotRepository,
                                 ClientDeliverableReadinessService readinessService) {
        this.projectRepository = projectRepository;
        this.operationalTruthService = operationalTruthService;
        this.snapshotRepository = snapshotRepository;
        this.readinessService = readinessService;
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
    }

    void backfillResolvedOutcomes() {
        for (TrustSignalSnapshotEntity snapshot : snapshotRepository.findByEventualOutcomeIsNull()) {
            ProjectEntity project = projectRepository.findById(snapshot.getProjectId()).orElse(null);
            if (project == null) {
                continue;
            }
            var readiness = readinessService.computeForProject(snapshot.getProjectId());
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
