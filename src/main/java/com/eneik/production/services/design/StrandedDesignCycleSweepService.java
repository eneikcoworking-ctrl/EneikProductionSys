package com.eneik.production.services.design;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.services.logging.LogScope;
import com.eneik.production.services.logging.ProjectLogFlushQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sweeps stranded design shop cycle claims (Prescription 51 / BOUNDARY_TOPOLOGY, D006, Varzi 1999).
 *
 * <p>Every claim requires a temporal boundary and sweeping closure: if a design cycle claim is
 * stranded due to an unexpected worker crash, restart, or timeout during Stitch generation,
 * this sweep safely releases the claim via atomic compare-and-swap, allowing subsequent ticks
 * to retry without permanent deadlock.
 */
@Service
public class StrandedDesignCycleSweepService {

    private static final Logger log = LoggerFactory.getLogger(StrandedDesignCycleSweepService.class);

    private final DesignShopCycleRepository designShopCycleRepository;
    private final StrandedDesignCycleSweepService self;

    @Autowired(required = false)
    private DefectJournalService defectJournalService;

    @Autowired(required = false)
    private DefectJournalRepository defectJournalRepository;

    @Value("${design-shop.claim-ttl-minutes:15}")
    private long maxAgeMinutes = 15;

    @Value("${design-shop.sweep.min-samples-for-data-driven:5}")
    private int minSamplesForDataDriven = 5;

    @Value("${design-shop.sweep.safety-multiplier:3.0}")
    private double safetyMultiplier = 3.0;

    public StrandedDesignCycleSweepService(DesignShopCycleRepository designShopCycleRepository,
                                           @Lazy StrandedDesignCycleSweepService self) {
        this.designShopCycleRepository = designShopCycleRepository;
        this.self = self;
    }

    public void setDefectJournalService(DefectJournalService defectJournalService) {
        this.defectJournalService = defectJournalService;
    }

    public void setDefectJournalRepository(DefectJournalRepository defectJournalRepository) {
        this.defectJournalRepository = defectJournalRepository;
    }

    public void setMaxAgeMinutes(long maxAgeMinutes) {
        this.maxAgeMinutes = maxAgeMinutes;
    }

    public void setMinSamplesForDataDriven(int minSamplesForDataDriven) {
        this.minSamplesForDataDriven = minSamplesForDataDriven;
    }

    public void setSafetyMultiplier(double safetyMultiplier) {
        this.safetyMultiplier = safetyMultiplier;
    }

    /**
     * Derives effective lease duration for a design cycle claim.
     * If observations of design generation duration exist in DefectJournal, computes the median
     * with safety multiplier, clamped to minimum 5 minutes and maximum 30 minutes.
     * Otherwise defaults to maxAgeMinutes.
     */
    public Duration calculateEffectiveLeaseDuration() {
        if (defectJournalRepository != null) {
            List<DefectJournalEntity> entries = defectJournalRepository
                    .findByDefectTypeOrderByCreatedAtDesc("DESIGN_CYCLE_GENERATION_DURATION");
            List<Double> durations = entries.stream()
                    .map(DefectJournalEntity::getMetricValue)
                    .filter(v -> v != null && v > 0)
                    .limit(50)
                    .sorted()
                    .toList();
            if (durations.size() >= minSamplesForDataDriven) {
                double medianMillis;
                int size = durations.size();
                if (size % 2 == 1) {
                    medianMillis = durations.get(size / 2);
                } else {
                    medianMillis = (durations.get(size / 2 - 1) + durations.get(size / 2)) / 2.0;
                }
                long effectiveMillis = Math.round(medianMillis * safetyMultiplier);
                long clampedMillis = Math.max(300_000L, Math.min(1_800_000L, effectiveMillis));
                return Duration.ofMillis(clampedMillis);
            }
        }
        return Duration.ofMinutes(maxAgeMinutes);
    }

    @Scheduled(cron = "${design-shop.sweep.cron:0 */2 * * * ?}")
    public int sweep() {
        Duration leaseDuration = calculateEffectiveLeaseDuration();
        Instant now = Instant.now();
        Instant cutoff = now.minus(leaseDuration);

        List<DesignShopCycleEntity> strandedCycles =
                designShopCycleRepository.findByStartCycleClaimedAtIsNotNullAndStartCycleClaimedAtBefore(cutoff);

        if (strandedCycles == null || strandedCycles.isEmpty()) {
            return 0;
        }

        int releasedCount = 0;
        for (DesignShopCycleEntity cycle : strandedCycles) {
            LogScope.project(cycle.getProjectId());
            try {
                releasedCount += self.releaseStrandedCycle(cycle, cutoff, leaseDuration);
            } catch (Exception e) {
                log.error("StrandedDesignCycleSweepService: failed to sweep cycle for project {}: {}",
                        cycle.getProjectId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
        return releasedCount;
    }

    @Transactional
    public int releaseStrandedCycle(DesignShopCycleEntity cycle, Instant cutoff, Duration leaseDuration) {
        Instant claimedAt = cycle.getStartCycleClaimedAt();
        if (claimedAt == null || claimedAt.isAfter(cutoff)) {
            return 0;
        }

        int released = designShopCycleRepository.compareAndReleaseStrandedClaim(cycle.getProjectId(), claimedAt);
        if (released == 1) {
            Duration age = Duration.between(claimedAt, Instant.now());
            log.warn("StrandedDesignCycleSweepService: released stranded design cycle claim for project {} after {} ms "
                            + "(claimed at {}, cutoff {}). Returned to unclaimed for ordinary retry.",
                    cycle.getProjectId(), age.toMillis(), claimedAt, cutoff);

            String description = String.format(
                    "Released stranded design cycle claim for project %s (held for %d ms since %s, exceeding lease %d ms)",
                    cycle.getProjectId(), age.toMillis(), claimedAt, leaseDuration.toMillis()
            );

            if (defectJournalService != null) {
                try {
                    defectJournalService.recordInstitutionalAudit(
                            cycle.getProjectId(),
                            "DesignShopCycleRepository",
                            "STRANDED_DESIGN_CYCLE_CLAIM_RELEASED",
                            description,
                            (double) age.toMillis()
                    );
                } catch (Exception e) {
                    log.warn("Failed to record institutional audit for stranded cycle release", e);
                }
            }

            try {
                ProjectLogFlushQueue.offer(new ProjectLogFlushQueue.PendingEntry(
                        cycle.getProjectId(),
                        Instant.now(),
                        "WARN",
                        "com.eneik.production.services.design.StrandedDesignCycleSweepService",
                        "[BOUNDARY-TOPOLOGY] " + description
                ));
            } catch (Exception e) {
                log.warn("Failed to queue stranded cycle release in ProjectLogFlushQueue", e);
            }
        } else {
            log.info("StrandedDesignCycleSweepService: design cycle claim for project {} left on its own before release "
                    + "- a live holder finished concurrently", cycle.getProjectId());
        }
        return released;
    }
}
