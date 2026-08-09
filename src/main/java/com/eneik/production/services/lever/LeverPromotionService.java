package com.eneik.production.services.lever;

import com.eneik.production.models.persistence.LeverObservation;
import com.eneik.production.models.persistence.LeverPromotionStateEntity;
import com.eneik.production.repositories.LeverObservationRepository;
import com.eneik.production.repositories.LeverPromotionStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The "lift" for OperationalTruthService's already-documented but previously inert promotion ladder
 * (observe_only -> warn_only -> soft_gate -> hard_gate -> auto_remediate). Every prediction/decision lever
 * introduced in the 2026-08-08 ML-update patch computes BOTH its old (incumbent) and new (candidate)
 * decision, records the pair here, and only acts on the candidate once this service has promoted that
 * lever past observe_only - based on real accumulated evidence, never on a deploy or a timer (Popper,
 * BARCAN-TAG-06 philosopher 1: candidate starts as a bold conjecture, promoted only by surviving a severe
 * test). Revision of a lever's stage happens through this one canonical path only (Gärdenfors AGM,
 * BARCAN-TAG-04 philosopher 7) - individual levers never mutate their own promotion state directly.
 */
@Service
public class LeverPromotionService {

    private static final Logger log = LoggerFactory.getLogger(LeverPromotionService.class);

    // Deliberately uniform starting thresholds across every lever (not yet tuned per-lever) - Dretske's
    // information-channel-capacity point (BARCAN-TAG-07 philosopher 5): too few samples can't license an
    // update regardless of how good the agreement rate looks on them.
    static final long MIN_RESOLVED_SAMPLES = 20;
    static final double AGREEMENT_THRESHOLD = 0.80;
    // Goodman's "grue" point (BARCAN-TAG-00 philosopher 6): only the RECENT window counts toward
    // promotion - a candidate that matched reality up to now is not thereby proven to keep matching it.
    static final Duration RECENCY_WINDOW = Duration.ofDays(14);

    private final LeverPromotionStateRepository stateRepository;
    private final LeverObservationRepository observationRepository;

    public LeverPromotionService(LeverPromotionStateRepository stateRepository,
                                  LeverObservationRepository observationRepository) {
        this.stateRepository = stateRepository;
        this.observationRepository = observationRepository;
    }

    /**
     * Records one real decision pair for a lever. Every caller passes a leverKey that must stay a rigid,
     * stable identifier across calls (Frege sense/reference, BARCAN-TAG-08 philosopher 6) - a typo or a
     * renamed key silently starts a brand new, evidence-less lever.
     */
    @Transactional
    public void recordObservation(String leverKey, String subjectId, String incumbentDecision,
                                   String candidateDecision, LeverAgreement agreement, String groundTruthOutcome) {
        LeverObservation observation = new LeverObservation();
        observation.setLeverKey(leverKey);
        observation.setSubjectId(subjectId);
        observation.setIncumbentDecision(incumbentDecision);
        observation.setCandidateDecision(candidateDecision);
        observation.setAgreement(agreement.name());
        observation.setGroundTruthOutcome(groundTruthOutcome);
        observationRepository.save(observation);

        LeverPromotionStateEntity state = stateRepository.findById(leverKey).orElseGet(() -> {
            LeverPromotionStateEntity fresh = new LeverPromotionStateEntity();
            fresh.setLeverKey(leverKey);
            fresh.setCurrentStage(LeverStage.OBSERVE_ONLY.wireValue());
            return fresh;
        });
        state.setSampleCount(state.getSampleCount() + 1);
        if (agreement == LeverAgreement.TRUE) {
            state.setAgreementCount(state.getAgreementCount() + 1);
        }

        // Immediate demotion on the FIRST real disagreement after promotion (not batched into the next
        // evaluatePromotions cycle) - a promoted lever that just got a real answer wrong loses trust now,
        // not on the next 2h tick (Goldman's process reliabilism, BARCAN-TAG-07 philosopher 2: a single
        // confirmed failure is itself evidence the process is not yet reliable at this stage).
        LeverStage stage = LeverStage.fromWireValue(state.getCurrentStage());
        if (agreement == LeverAgreement.FALSE && stage.atLeast(LeverStage.WARN_ONLY)) {
            LeverStage demoted = stage.previous();
            state.setCurrentStage(demoted.wireValue());
            state.setDemotedAt(Instant.now());
            log.warn("[LEVER-PROMOTION] '{}' demoted {} -> {} after a real disagreement (subject={})",
                    leverKey, stage.wireValue(), demoted.wireValue(), subjectId);
        }
        stateRepository.save(state);
    }

    /** Reads the current stage; unknown/never-observed levers default to observe_only (zero live effect). */
    @Transactional(readOnly = true)
    public LeverStage currentStage(String leverKey) {
        return stateRepository.findById(leverKey)
                .map(s -> LeverStage.fromWireValue(s.getCurrentStage()))
                .orElse(LeverStage.OBSERVE_ONLY);
    }

    /** Separate cadence from Kaizen's own 2h business cycle - promotion is cross-cutting, not Kaizen logic. */
    @Scheduled(fixedRate = 7200000, initialDelay = 120000)
    @Transactional
    public void evaluatePromotions() {
        List<LeverPromotionStateEntity> states = stateRepository.findAll();
        Instant since = Instant.now().minus(RECENCY_WINDOW);
        for (LeverPromotionStateEntity state : states) {
            evaluateOne(state, since);
        }
    }

    private void evaluateOne(LeverPromotionStateEntity state, Instant since) {
        LeverStage stage = LeverStage.fromWireValue(state.getCurrentStage());
        state.setLastEvaluatedAt(Instant.now());
        if (stage == LeverStage.AUTO_REMEDIATE) {
            stateRepository.save(state);
            return;
        }

        List<LeverObservation> recent = observationRepository
                .findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(state.getLeverKey(), since);
        long resolved = recent.stream()
                .filter(o -> "TRUE".equals(o.getAgreement()) || "FALSE".equals(o.getAgreement()))
                .count();
        long agreed = recent.stream().filter(o -> "TRUE".equals(o.getAgreement())).count();

        if (resolved < MIN_RESOLVED_SAMPLES) {
            stateRepository.save(state);
            return;
        }
        double rate = (double) agreed / resolved;
        if (rate >= AGREEMENT_THRESHOLD) {
            LeverStage promoted = stage.next();
            state.setCurrentStage(promoted.wireValue());
            state.setPromotedAt(Instant.now());
            log.info("[LEVER-PROMOTION] '{}' promoted {} -> {} (recent resolved={}, agreement rate={})",
                    state.getLeverKey(), stage.wireValue(), promoted.wireValue(), resolved, rate);
        }
        stateRepository.save(state);
    }
}
