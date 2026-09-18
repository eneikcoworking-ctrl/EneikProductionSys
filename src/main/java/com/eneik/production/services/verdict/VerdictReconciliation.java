package com.eneik.production.services.verdict;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place where the flow's layers are reconciled - which did not exist before 2026-08-15.
 *
 * READ-ONLY. Nothing gates on this yet, deliberately: a gate is only as honest as its inputs, and until
 * every layer's measure has been repaired some of them still owe {@link Verdict#ABSTAIN} rather than a
 * number. Building the reading first means it can be observed against the live flow before anything
 * depends on it.
 *
 * <pre>
 *   D(P) = |{ declared propositions whose verdict is ABSTAIN  }|   epistemic debt
 *   W(P) = |{ declared propositions whose verdict is WITHHOLD }|   refusals
 *   advance(P) ⟺ D(P) = 0 ∧ W(P) = 0
 * </pre>
 *
 * <b>There is no threshold, and that is the point.</b> A threshold is the signature of a proxy measure:
 * you need one when your quantity does not actually capture the property you care about. When it does, the
 * gate is zero. The same signature appears in the conflict repair (auto-resolve iff no conflicting file is
 * in a declared scope) and in the acceptance rule (acceptable iff every link of every chain was walked).
 *
 * D and W are commensurable where the layers' own numbers are not, because they count objects of ONE type:
 * verdicts on declared propositions. Unification is achieved not by normalising `82%` and `954545` onto a
 * common scale - which is impossible, they are different modalities - but by mapping every layer onto a
 * common type.
 *
 * <p><b>Belief Update Ledger (AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER / D007):</b>
 * Prior rulings per {@code (projectId, layer, proposition)} are maintained across reconciliation cycles.
 * A verdict transition must record what changed it (evidence / reason), when it changed (timestamp),
 * and what it previously stood on. An ungrounded verdict change (verdict changed without reason or evidence)
 * is refuted and converted to {@link Verdict#ABSTAIN}, contributing to epistemic debt {@code D(P)}.
 */
@org.springframework.stereotype.Service
public class VerdictReconciliation {

    /**
     * Identity key for proposition tracking in the belief update ledger.
     */
    public record PropositionKey(UUID projectId, String layer, String proposition) {
        public PropositionKey {
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(layer, "layer must not be null");
            Objects.requireNonNull(proposition, "proposition must not be null");
        }
    }

    /**
     * @param advance     the Kleene conjunction over every judgement
     * @param debt        D - propositions declared and not established
     * @param refusals    W - propositions actively refused
     * @param constraint  the layer maximising W + D, or empty when nothing is outstanding. This is the TOC
     *                    constraint made DERIVABLE rather than asserted by hand: measured on
     *                    test-forty-sixth it points at the doctrine layer (2 refusing, 7 objecting, 4
     *                    unknown) and, within the task layer, at UX/UI - neither of which the queue view
     *                    shows.
     * @param judgements  every ruling, so a refusal can be traced to something arguable
     */
    public record Reconciliation(
            Verdict advance,
            int debt,
            int refusals,
            String constraint,
            List<Judgement> judgements
    ) {
        public boolean mayAdvance() {
            return advance == Verdict.PERMIT;
        }

        public List<Judgement> transitions() {
            return judgements.stream().filter(Judgement::hasTransition).toList();
        }
    }

    private final List<VerdictLayer> layers;
    private final Clock clock;

    // In-memory belief update ledger: (projectId, layer, proposition) -> latest known Judgement
    private final Map<PropositionKey, Judgement> beliefLedger = new ConcurrentHashMap<>();

    /**
     * Spring injects every {@link VerdictLayer} bean. Adding one is therefore the whole act of extending
     * the system's verification - and by {@link Verdict#and} that can only ever make advancing harder,
     * never easier, so a new layer cannot accidentally unblock anything.
     */
    public VerdictReconciliation(List<VerdictLayer> layers) {
        this(layers, Clock.systemUTC());
    }

    public VerdictReconciliation(List<VerdictLayer> layers, Clock clock) {
        this.layers = layers == null ? List.of() : List.copyOf(layers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Reconciliation reconcile(UUID projectId) {
        List<Judgement> judgements = new ArrayList<>();
        Map<String, Integer> outstandingByLayer = new LinkedHashMap<>();
        Instant now = clock.instant();

        for (VerdictLayer layer : layers) {
            List<String> declared;
            List<Judgement> ruled;
            try {
                declared = layer.declaredPropositions(projectId);
                ruled = layer.judge(projectId);
            } catch (RuntimeException e) {
                // A layer that throws has established nothing, which is precisely an abstention - and
                // recording it as one keeps a broken layer visible instead of letting it vanish from the
                // reckoning. The observer must never become the outage it exists to prevent.
                String propName = "(layer failed)";
                PropositionKey key = new PropositionKey(projectId, layer.layerName(), propName);
                Judgement prior = beliefLedger.get(key);
                Judgement failed = Judgement.abstain(layer.layerName(), propName,
                        "layer threw while judging: " + e.getMessage()).withPriorBelief(prior, now);
                beliefLedger.put(key, failed);
                judgements.add(failed);
                outstandingByLayer.merge(layer.layerName(), 1, Integer::sum);
                continue;
            }
            declared = declared == null ? List.of() : declared;
            ruled = ruled == null ? List.of() : ruled;

            Set<String> answered = new LinkedHashSet<>();
            for (Judgement j : ruled) {
                if (j == null) {
                    continue;
                }
                String prop = j.proposition() == null ? "(unnamed)" : j.proposition();
                answered.add(prop);

                PropositionKey key = new PropositionKey(projectId, layer.layerName(), prop);
                Judgement prior = beliefLedger.get(key);

                // D007 / Levi 1980 refutation check:
                // A verdict cannot change without a recorded reason or evidence explaining the revision.
                if (prior != null && prior.verdict() != j.verdict()) {
                    boolean hasReason = j.reason() != null && !j.reason().isBlank();
                    boolean hasEvidence = j.evidence() != null && !j.evidence().isBlank();
                    if (!hasReason && !hasEvidence) {
                        j = Judgement.abstain(j.layer(), prop, "UNGROUNDED_TRANSITION",
                                "verdict changed from " + prior.verdict() + " to " + j.verdict()
                                        + " without recording reason or evidence");
                    }
                }

                Judgement recorded = j.withPriorBelief(prior, now);
                beliefLedger.put(key, recorded);
                judgements.add(recorded);
                if (recorded.verdict() != Verdict.PERMIT) {
                    outstandingByLayer.merge(layer.layerName(), 1, Integer::sum);
                }
            }

            // A declared proposition with no ruling is unestablished by definition. Filling it in here
            // rather than ignoring it is what makes the declared domain worth declaring: silence about
            // something a layer promised to rule on must count against advancing, not for it.
            for (String proposition : declared) {
                if (!answered.contains(proposition)) {
                    PropositionKey key = new PropositionKey(projectId, layer.layerName(), proposition);
                    Judgement prior = beliefLedger.get(key);
                    Judgement unruled = Judgement.abstain(layer.layerName(), proposition,
                            "declared but not ruled on this cycle").withPriorBelief(prior, now);
                    beliefLedger.put(key, unruled);
                    judgements.add(unruled);
                    outstandingByLayer.merge(layer.layerName(), 1, Integer::sum);
                }
            }
        }

        Verdict advance = Verdict.PERMIT;
        int debt = 0;
        int refusals = 0;
        for (Judgement j : judgements) {
            advance = advance.and(j.verdict());
            if (j.verdict() == Verdict.ABSTAIN) {
                debt++;
            } else if (j.verdict() == Verdict.WITHHOLD) {
                refusals++;
            }
        }

        String constraint = outstandingByLayer.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        return new Reconciliation(advance, debt, refusals, constraint, List.copyOf(judgements));
    }

    /**
     * Look up the prior recorded judgment for a given project, layer, and proposition.
     */
    public Optional<Judgement> getPriorJudgement(UUID projectId, String layer, String proposition) {
        if (projectId == null || layer == null || proposition == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(beliefLedger.get(new PropositionKey(projectId, layer, proposition)));
    }

    /**
     * Read-only view of the current in-memory belief update ledger.
     */
    public Map<PropositionKey, Judgement> getBeliefLedger() {
        return Collections.unmodifiableMap(beliefLedger);
    }

    /**
     * Returns all judgements for the given project that recorded a verdict transition.
     */
    public List<Judgement> getBeliefTransitions(UUID projectId) {
        if (projectId == null) {
            return List.of();
        }
        return beliefLedger.entrySet().stream()
                .filter(e -> e.getKey().projectId().equals(projectId) && e.getValue().hasTransition())
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Clears the in-memory belief ledger (primarily for test isolation).
     */
    public void clearBeliefLedger() {
        beliefLedger.clear();
    }
}
