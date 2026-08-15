package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Does the delivered product actually run?
 *
 * The layer that most clearly needed this structure. On 2026-08-15 the factory diagnosed two Dockerfile
 * defects itself, repaired both (10:38 and 12:00), and never re-observed - so philosophy stayed
 * subordinated to a launch verdict taken at 10:21, before either repair, for the rest of the day. The
 * system was holding a stored `failed` and treating it as a current fact.
 *
 * That is the same epistemic defect as a task reporting `done` without delivering, only mirrored: there, a
 * declaration of success was believed without checking; here, a declaration of failure was believed
 * without re-checking. Both are a stored claim standing in for its referent.
 *
 * So this layer abstains rather than refusing once its evidence has outlived a plausible claim to
 * currency. Abstention blocks too - it is not a softer refusal - but it says the true thing: nothing is
 * currently established, and a fresh observation is owed.
 */
@Component
public class RuntimeVerdictLayer implements VerdictLayer {

    static final String PROPOSITION_LAUNCHES = "the delivered product launches and reports healthy";

    /**
     * A DECLARED bound, not a measured one, and labelled as such per the same rule the market corpus
     * applies to `derived` entries: reasoning may be asserted, a number requires measurement. Nothing here
     * establishes that six hours is the right horizon; it is chosen so that an observation predating a
     * same-day repair cannot silently remain authoritative, which is the failure actually observed.
     */
    static final Duration EVIDENCE_HORIZON = Duration.ofHours(6);

    private final ClientRuntimeObservationRepository observationRepository;

    public RuntimeVerdictLayer(ClientRuntimeObservationRepository observationRepository) {
        this.observationRepository = observationRepository;
    }

    @Override
    public String layerName() {
        return "runtime";
    }

    @Override
    public List<String> declaredPropositions(UUID projectId) {
        return List.of(PROPOSITION_LAUNCHES);
    }

    @Override
    public List<Judgement> judge(UUID projectId) {
        List<ClientRuntimeObservationEntity> observations;
        try {
            observations = observationRepository.findByProjectIdOrderByObservedAtDesc(projectId);
        } catch (RuntimeException e) {
            return List.of(Judgement.abstain(layerName(), PROPOSITION_LAUNCHES,
                    "could not read runtime observations: " + e.getMessage()));
        }
        if (observations == null || observations.isEmpty()) {
            return List.of(Judgement.abstain(layerName(), PROPOSITION_LAUNCHES,
                    "no runtime observation has ever been taken for this project"));
        }

        ClientRuntimeObservationEntity latest = observations.get(0);
        Instant observedAt = latest.getObservedAt();
        String evidence = "observation " + latest.getId() + " at " + observedAt;

        if (observedAt != null && Duration.between(observedAt, Instant.now()).compareTo(EVIDENCE_HORIZON) > 0) {
            // Stale evidence is not a verdict. Repairs may well have landed since, and treating an old
            // failure as current is what kept philosophy blocked all day on 2026-08-15.
            return List.of(Judgement.abstain(layerName(), PROPOSITION_LAUNCHES,
                    "the most recent observation is older than the declared evidence horizon ("
                            + EVIDENCE_HORIZON.toHours() + "h) - a fresh observation is owed before this "
                            + "can be established either way; " + evidence));
        }

        if (latest.isLaunchSuccess()) {
            return List.of(Judgement.permit(layerName(), PROPOSITION_LAUNCHES, evidence));
        }
        String detail = latest.getErrorText() == null || latest.getErrorText().isBlank()
                ? "launch failed"
                : latest.getErrorText().substring(0, Math.min(200, latest.getErrorText().length()));
        return List.of(Judgement.withhold(layerName(), PROPOSITION_LAUNCHES, detail, evidence));
    }
}
