package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
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
     * Fallback only, used when the product's own history cannot be read.
     *
     * The real staleness test is below and is exact: an observation describes the product AS IT WAS, so a
     * commit landing on main afterwards means the observation no longer describes the product that exists.
     * This duration is a declared arbitrary bound - reasoning may be asserted, a number requires
     * measurement - kept solely so an unreadable history degrades to caution rather than to silence.
     */
    static final Duration EVIDENCE_HORIZON = Duration.ofHours(6);

    private final ClientRuntimeObservationRepository observationRepository;
    private final ProjectRepository projectRepository;
    private final GitHubPullRequestService gitHubPullRequestService;

    public RuntimeVerdictLayer(ClientRuntimeObservationRepository observationRepository,
                               ProjectRepository projectRepository,
                               GitHubPullRequestService gitHubPullRequestService) {
        this.observationRepository = observationRepository;
        this.projectRepository = projectRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
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

        // THE REFERENT TEST. An observation is a claim about the product as it was at the moment it was
        // taken. If main has been committed to since, the product it describes no longer exists, and the
        // claim says nothing about the one that does - regardless of how recently it was made.
        //
        // This is exactly what went wrong on 2026-08-15: the factory diagnosed two Dockerfile defects
        // itself, repaired both at 10:38 and 12:00, and never re-observed - so philosophy stayed
        // subordinated for the rest of the day to a verdict taken at 10:21, before either repair. The
        // stored `failed` was treated as a current fact about a product that had changed twice underneath
        // it. Time alone could not have caught that; only asking about the referent can.
        String staleness = stalenessReason(projectId, observedAt);
        if (staleness != null) {
            return List.of(Judgement.abstain(layerName(), PROPOSITION_LAUNCHES,
                    staleness + " - a fresh observation is owed before this can be established either "
                            + "way; " + evidence));
        }

        if (latest.isLaunchSuccess()) {
            return List.of(Judgement.permit(layerName(), PROPOSITION_LAUNCHES, evidence));
        }
        String detail = latest.getErrorText() == null || latest.getErrorText().isBlank()
                ? "launch failed"
                : latest.getErrorText().substring(0, Math.min(200, latest.getErrorText().length()));
        return List.of(Judgement.withhold(layerName(), PROPOSITION_LAUNCHES, detail, evidence));
    }

    /**
     * Why the latest observation no longer describes the current product, or null when it still does.
     *
     * Reading the history is best-effort: if it cannot be read, the answer falls back to age, which is a
     * declared bound rather than a fact. Degrading to caution is right here - an unverifiable claim about
     * whether the product changed must not resolve as "it did not".
     */
    private String stalenessReason(UUID projectId, Instant observedAt) {
        if (observedAt == null) {
            return "the latest observation has no timestamp, so its currency cannot be established";
        }
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project != null) {
            try {
                Instant lastCommit = gitHubPullRequestService.latestCommitTime(project, "main").orElse(null);
                if (lastCommit != null) {
                    return lastCommit.isAfter(observedAt)
                            ? "main has been committed to at " + lastCommit + ", after this observation was "
                                    + "taken, so it describes a product that no longer exists"
                            : null;
                }
            } catch (RuntimeException e) {
                // fall through to the age bound
            }
        }
        return Duration.between(observedAt, Instant.now()).compareTo(EVIDENCE_HORIZON) > 0
                ? "the product's history could not be read and the observation is older than the declared "
                        + EVIDENCE_HORIZON.toHours() + "h fallback horizon"
                : null;
    }
}
