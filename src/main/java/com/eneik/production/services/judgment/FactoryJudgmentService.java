package com.eneik.production.services.judgment;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.InvariantStatusChangeEntity;
import com.eneik.production.repositories.InvariantStatusChangeRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Factory-level judgment, driven by refutation rather than by a clock.
 *
 * Popper's asymmetry is the whole design. Confirmations are free and unbounded - this factory produces
 * roughly forty merges a day, each of which confirms that things broadly work - and they carry no
 * information. Refutations are rare and each one is informative: measured over the reconstructed
 * TRUST_SIGNAL_SNAPSHOTS history, 2.9 per day. V105 made them durable, one row per transition and none
 * per evaluation. This service consumes exactly those rows.
 *
 * The consequence is that the model is not called on a schedule. The schedule only asks whether a
 * refutation exists; when none does the cycle costs one indexed query and returns, having spent nothing.
 * A judgment layer that thinks on a timer is thinking about confirmations, which is to say about nothing.
 *
 * Subordination, not addition: this reads the constraint the factory already computes about itself, and
 * files what it concludes into the sink the factory already has. It creates no second denominator - the
 * finding is a SYSTEMIC_DEFECT proposal like every other statement about this codebase's own defects,
 * and it is review-only by that category's construction, never auto-applied.
 */
@Service
public class FactoryJudgmentService {

    private static final Logger log = LoggerFactory.getLogger(FactoryJudgmentService.class);

    private final InvariantStatusChangeRepository invariantChangeRepository;
    private final JudgmentAgentClient judgmentAgentClient;
    private final KaizenService kaizenService;
    private final SystemSettingsService settingsService;

    /**
     * Bounds one cycle, and with it the first cycle after deployment.
     *
     * V108 deliberately leaves the existing transitions unjudged rather than backfilling them away, so
     * the first run meets a real backlog. A bound turns that into several cheap cycles instead of one
     * burst, and it is also the ceiling on what a runaway invariant could cost in a single tick.
     */
    @Value("${judgment-agent.max-per-cycle:5}")
    private int maxPerCycle;

    /** How much prior history of the same invariant the ruling is given to read the transition against. */
    @Value("${judgment-agent.context-history-limit:6}")
    private int contextHistoryLimit;

    public FactoryJudgmentService(InvariantStatusChangeRepository invariantChangeRepository,
                                  JudgmentAgentClient judgmentAgentClient,
                                  KaizenService kaizenService,
                                  SystemSettingsService settingsService) {
        this.invariantChangeRepository = invariantChangeRepository;
        this.judgmentAgentClient = judgmentAgentClient;
        this.kaizenService = kaizenService;
        this.settingsService = settingsService;
    }

    @Scheduled(fixedRateString = "${judgment-agent.rate-ms:900000}")
    public void judgeOutstandingRefutations() {
        if (!settingsService.effectiveBoolean("judgment_agent_enabled")) {
            return;
        }
        List<InvariantStatusChangeEntity> unjudged =
                invariantChangeRepository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc();
        if (unjudged.isEmpty()) {
            // The common case, and the point: no refutation means no call, no tokens, no cost.
            return;
        }
        int budget = Math.max(1, maxPerCycle);
        log.info("[JUDGMENT] {} unjudged invariant transition(s); ruling on up to {} this cycle",
                unjudged.size(), budget);
        int processed = 0;
        int unjudgeable = 0;
        for (InvariantStatusChangeEntity transition : unjudged) {
            if (processed >= budget) {
                break;
            }
            JudgmentAgentClient.Outcome outcome = judgeOne(transition);
            if (outcome == JudgmentAgentClient.Outcome.UNAVAILABLE) {
                // A fact about the endpoint, not about this transition. judged_at stays null so the row is
                // retried; the cycle stops because every remaining row would meet the same dead endpoint.
                break;
            }
            processed++;
            if (outcome == JudgmentAgentClient.Outcome.UNJUDGEABLE) {
                unjudgeable++;
            }
        }
        reportIfNothingCouldBeJudged(processed, unjudgeable);
    }

    /**
     * A cycle that drained rows without producing a single ruling is a defect in the judgment layer.
     *
     * Marking an unjudgeable row read is right - it will never become rulable - but it makes the failure
     * quiet, and a quiet failure here empties the whole backlog while judging nothing. That is strictly
     * worse than the head-of-line block it replaces, because the block at least stopped visibly. So the
     * layer reports on itself: same review-only sink, keyed to this service so it stands as one finding
     * rather than one per drained row.
     */
    private void reportIfNothingCouldBeJudged(int processed, int unjudgeable) {
        if (processed < 2 || unjudgeable < processed) {
            return;
        }
        log.warn("[JUDGMENT] {} transition(s) processed this cycle and NONE could be ruled on; "
                + "reporting the judgment layer itself", processed);
        kaizenService.recordSystemicDefectProposal(
                null,
                "Global",
                "FactoryJudgmentService",
                "Factory judgment produced no ruling on any refutation it read",
                "Every invariant transition read this cycle came back unjudgeable - declined, truncated or "
                        + "off-schema - so refutations are being marked read without being judged. Check the "
                        + "judgment_agent_api_key, the configured judgment_agent_model and the request shape "
                        + "in JudgmentAgentClient before this drains the backlog silently.");
    }

    /** The outcome, so the caller can tell a fact about this row from a fact about the endpoint. */
    JudgmentAgentClient.Outcome judgeOne(InvariantStatusChangeEntity transition) {
        JudgmentAgentClient.Ruling verdict = judgmentAgentClient.judge(buildPrompt(transition));

        if (verdict.outcome() == JudgmentAgentClient.Outcome.UNAVAILABLE) {
            log.warn("[JUDGMENT] endpoint unavailable while reading invariant '{}' ({} -> {}): {}; "
                            + "leaving it unjudged for retry",
                    transition.getInvariantKey(), transition.getPreviousStatus(), transition.getStatus(),
                    verdict.reason());
            return verdict.outcome();
        }

        if (verdict.outcome() == JudgmentAgentClient.Outcome.UNJUDGEABLE) {
            // Marked read on purpose. The same input produces the same non-answer every cycle, so leaving
            // it unjudged makes it an absorbing state at the head of a FIFO queue and stops all judgment.
            log.warn("[JUDGMENT] invariant '{}' ({} -> {}) cannot be ruled on ({}); marking it read so the "
                            + "backlog still drains",
                    transition.getInvariantKey(), transition.getPreviousStatus(), transition.getStatus(),
                    verdict.reason());
            transition.setJudgedAt(Instant.now());
            invariantChangeRepository.save(transition);
            return verdict.outcome();
        }

        if (verdict.outcome() == JudgmentAgentClient.Outcome.FINDING) {
            // targetComponent is the invariant key, not "EneikProductionSys". The Kaizen read path
            // deduplicates on category + targetComponent, and the 2026-08-17 measurement showed every
            // finding that passed the whole system as its component collapsing onto one key and silently
            // displacing the last. An invariant is a designator that actually picks out what is meant.
            kaizenService.recordSystemicDefectProposal(
                    null,
                    "Global",
                    "invariant:" + transition.getInvariantKey(),
                    verdict.title(),
                    verdict.action() + "\n\nRuling: " + verdict.reason());
            log.info("[JUDGMENT] FINDING on invariant '{}' ({} -> {}): {}",
                    transition.getInvariantKey(), transition.getPreviousStatus(), transition.getStatus(), verdict.title());
        } else {
            log.info("[JUDGMENT] ABSTAIN on invariant '{}' ({} -> {}): {}",
                    transition.getInvariantKey(), transition.getPreviousStatus(), transition.getStatus(), verdict.reason());
        }

        // Marked only after the finding is filed. The reverse order would lose a real finding to a crash
        // in between; this order can at worst re-file one, and the Kaizen dedup key absorbs that.
        transition.setJudgedAt(Instant.now());
        invariantChangeRepository.save(transition);
        return verdict.outcome();
    }

    String buildPrompt(InvariantStatusChangeEntity transition) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A Charter invariant of this factory changed status.\n\n");
        prompt.append("Invariant: ").append(transition.getInvariantKey()).append('\n');
        if (transition.getStatement() != null && !transition.getStatement().isBlank()) {
            prompt.append("It asserts: ").append(transition.getStatement()).append('\n');
        }
        prompt.append("Transition: ")
                .append(transition.getPreviousStatus() == null ? "(first ever record)" : transition.getPreviousStatus())
                .append(" -> ").append(transition.getStatus()).append('\n');
        prompt.append("Observed at: ").append(transition.getObservedAt()).append('\n');
        prompt.append("Scope: ")
                .append(transition.getProjectId() == null
                        ? "factory-wide (not scoped to one project)"
                        : "project " + transition.getProjectId())
                .append('\n');
        if (transition.getEvidence() != null && !transition.getEvidence().isBlank()) {
            prompt.append("Evidence recorded with the transition:\n").append(transition.getEvidence()).append('\n');
        }

        // Prior transitions of the same invariant. A status change is only interpretable against what it
        // changed from and how often it has changed before: an invariant that has flapped six times is a
        // different fact from one that has just moved for the first time, and the ruling cannot tell them
        // apart from a single row.
        List<InvariantStatusChangeEntity> history = invariantChangeRepository
                .findByInvariantKeyOrderByObservedAtDesc(transition.getInvariantKey()).stream()
                .filter(other -> java.util.Objects.equals(other.getProjectId(), transition.getProjectId()))
                .filter(other -> !java.util.Objects.equals(other.getId(), transition.getId()))
                .limit(Math.max(0, contextHistoryLimit))
                .toList();
        if (!history.isEmpty()) {
            prompt.append("\nEarlier transitions of this same invariant, newest first:\n");
            for (InvariantStatusChangeEntity earlier : history) {
                prompt.append("  ").append(earlier.getObservedAt())
                        .append("  ").append(earlier.getPreviousStatus() == null ? "(none)" : earlier.getPreviousStatus())
                        .append(" -> ").append(earlier.getStatus()).append('\n');
            }
        } else {
            prompt.append("\nThis invariant has no earlier recorded transition.\n");
        }

        prompt.append("\nRule on whether this indicates a defect in the factory's own construction.");
        return prompt.toString();
    }
}
