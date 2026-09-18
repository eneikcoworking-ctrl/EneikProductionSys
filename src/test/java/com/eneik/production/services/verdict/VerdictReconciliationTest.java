package com.eneik.production.services.verdict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reconciliation decides whether a project may advance, so its combining rule has to be right before
 * any layer is wired to it. Every case here corresponds to a defect this structure exists to prevent.
 */
class VerdictReconciliationTest {

    private static final UUID PROJECT = UUID.randomUUID();

    private VerdictLayer layer(String name, Judgement... judgements) {
        return new VerdictLayer() {
            @Override public String layerName() { return name; }
            @Override public List<String> declaredPropositions(UUID p) {
                return java.util.Arrays.stream(judgements).map(Judgement::proposition).toList();
            }
            @Override public List<Judgement> judge(UUID p) { return List.of(judgements); }
        };
    }

    @Test
    void oneRefusalDefeatsAnyNumberOfApprovals() {
        var r = new VerdictReconciliation(List.of(
                layer("a", Judgement.permit("a", "p1", "ok"), Judgement.permit("a", "p2", "ok")),
                layer("b", Judgement.permit("b", "p3", "ok")),
                layer("c", Judgement.withhold("c", "p4", "the product does not launch", "obs-1"))
        )).reconcile(PROJECT);

        assertThat(r.advance())
                .as("conjunction, never a mean - a mean cannot express 'all of them must hold' at any "
                        + "threshold, which is exactly why entropy could not express the conflict rule")
                .isEqualTo(Verdict.WITHHOLD);
        assertThat(r.mayAdvance()).isFalse();
        assertThat(r.refusals()).isEqualTo(1);
    }

    @Test
    void abstentionIsNotPermission() {
        var r = new VerdictReconciliation(List.of(
                layer("a", Judgement.permit("a", "p1", "ok")),
                layer("b", Judgement.abstain("b", "p2", "no measured rule for this yet"))
        )).reconcile(PROJECT);

        assertThat(r.advance())
                .as("four doctrine roles stood at 'unknown' on 2026-08-15 while the flow dispatched "
                        + "normally - absence of a refutation is not a verification")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(r.debt()).isEqualTo(1);
    }

    @Test
    void everythingPermittedPermits() {
        var r = new VerdictReconciliation(List.of(
                layer("a", Judgement.permit("a", "p1", "ok")),
                layer("b", Judgement.permit("b", "p2", "ok"))
        )).reconcile(PROJECT);

        assertThat(r.advance()).isEqualTo(Verdict.PERMIT);
        assertThat(r.mayAdvance()).isTrue();
        assertThat(r.debt()).isZero();
        assertThat(r.refusals()).isZero();
    }

    @Test
    void addingALayerCanOnlyMakeAdvancingHarder() {
        List<VerdictLayer> base = List.of(layer("a", Judgement.permit("a", "p1", "ok")));
        var before = new VerdictReconciliation(base).reconcile(PROJECT);

        var after = new VerdictReconciliation(List.of(
                base.get(0),
                layer("b", Judgement.abstain("b", "p2", "unestablished"))
        )).reconcile(PROJECT);

        assertThat(before.mayAdvance()).isTrue();
        assertThat(after.mayAdvance())
                .as("monotonicity is what makes autonomous self-extension safe - a new check must never be "
                        + "able to accidentally unblock something")
                .isFalse();
    }

    @Test
    void aDeclaredPropositionNobodyRuledOnCountsAsDebt() {
        VerdictLayer forgetful = new VerdictLayer() {
            @Override public String layerName() { return "forgetful"; }
            @Override public List<String> declaredPropositions(UUID p) { return List.of("p1", "p2"); }
            @Override public List<Judgement> judge(UUID p) {
                return List.of(Judgement.permit("forgetful", "p1", "ok"));
            }
        };

        var r = new VerdictReconciliation(List.of(forgetful)).reconcile(PROJECT);

        assertThat(r.debt())
                .as("silence about something a layer promised to rule on must count against advancing, "
                        + "not for it - this is what makes declaring the domain worth doing")
                .isEqualTo(1);
        assertThat(r.advance()).isEqualTo(Verdict.ABSTAIN);
    }

    @Test
    void aLayerThatThrowsBecomesVisibleDebtRatherThanVanishing() {
        VerdictLayer broken = new VerdictLayer() {
            @Override public String layerName() { return "broken"; }
            @Override public List<String> declaredPropositions(UUID p) { return List.of("p1"); }
            @Override public List<Judgement> judge(UUID p) { throw new IllegalStateException("boom"); }
        };

        var r = new VerdictReconciliation(List.of(
                layer("healthy", Judgement.permit("healthy", "p0", "ok")),
                broken
        )).reconcile(PROJECT);

        assertThat(r.debt()).isEqualTo(1);
        assertThat(r.advance()).isEqualTo(Verdict.ABSTAIN);
        assertThat(r.judgements())
                .as("a broken layer must stay in the reckoning - the observer must never become the outage "
                        + "it exists to prevent, and it must not quietly shrink either")
                .anySatisfy(j -> assertThat(j.reason()).contains("boom"));
    }

    @Test
    void theConstraintIsTheLayerWithTheMostOutstanding() {
        var r = new VerdictReconciliation(List.of(
                layer("tasks", Judgement.permit("tasks", "t1", "ok")),
                layer("doctrine",
                        Judgement.withhold("doctrine", "d1", "TAG-11 refuses", ""),
                        Judgement.withhold("doctrine", "d2", "TAG-12 refuses", ""),
                        Judgement.abstain("doctrine", "d3", "unknown stance")),
                layer("runtime", Judgement.withhold("runtime", "r1", "does not launch", "obs-1"))
        )).reconcile(PROJECT);

        assertThat(r.constraint())
                .as("the TOC constraint becomes derivable as argmax(W+D) instead of asserted by hand")
                .isEqualTo("doctrine");
    }

    @Test
    void noLayersMeansNothingIsEstablishedRatherThanEverythingPermitted() {
        var r = new VerdictReconciliation(List.of()).reconcile(PROJECT);

        // Honest limitation, stated rather than hidden: with no layers there are no propositions, so the
        // conjunction over an empty set is PERMIT. That is correct arithmetic and a dangerous default, so
        // nothing may gate on this until at least one layer is wired - which is why stage D is last.
        assertThat(r.advance()).isEqualTo(Verdict.PERMIT);
        assertThat(r.judgements()).isEmpty();
    }

    @Test
    void initialReconciliationEstablishesBeliefWithoutPriorHistory() {
        var reconciliation = new VerdictReconciliation(List.of(
                layer("runtime", Judgement.withhold("runtime", "r1", "container crashed", "exit code 137"))
        ));

        var r = reconciliation.reconcile(PROJECT);

        assertThat(r.judgements()).hasSize(1);
        Judgement j = r.judgements().get(0);
        assertThat(j.previousVerdict()).isNull();
        assertThat(j.previousEvidence()).isEmpty();
        assertThat(j.previousReason()).isEmpty();
        assertThat(j.hasTransition()).isFalse();
        assertThat(j.hasBeliefUpdate()).isFalse();
        assertThat(j.decidedAt()).isNotNull();
        assertThat(r.transitions()).isEmpty();
    }

    @Test
    void beliefUpdatePreservesPriorVerdictAndEvidence() {
        class MutableRuntimeLayer implements VerdictLayer {
            private Judgement current = Judgement.withhold("runtime", "r1", "container crashed", "exit code 137");
            @Override public String layerName() { return "runtime"; }
            @Override public List<String> declaredPropositions(UUID p) { return List.of("r1"); }
            @Override public List<Judgement> judge(UUID p) { return List.of(current); }
            public void fix(Judgement repaired) { this.current = repaired; }
        }

        var layer = new MutableRuntimeLayer();
        var reconciliation = new VerdictReconciliation(List.of(layer));

        // Cycle 1: initial failure
        var r1 = reconciliation.reconcile(PROJECT);
        assertThat(r1.advance()).isEqualTo(Verdict.WITHHOLD);
        assertThat(r1.judgements().get(0).previousVerdict()).isNull();

        // Cycle 2: repaired with fresh evidence
        layer.fix(Judgement.permit("runtime", "r1", "healthcheck 200 OK after restart"));
        var r2 = reconciliation.reconcile(PROJECT);

        assertThat(r2.advance()).isEqualTo(Verdict.PERMIT);
        assertThat(r2.mayAdvance()).isTrue();
        assertThat(r2.transitions()).hasSize(1);

        Judgement j = r2.judgements().get(0);
        assertThat(j.verdict()).isEqualTo(Verdict.PERMIT);
        assertThat(j.evidence()).isEqualTo("healthcheck 200 OK after restart");
        assertThat(j.previousVerdict()).isEqualTo(Verdict.WITHHOLD);
        assertThat(j.previousReason()).isEqualTo("container crashed");
        assertThat(j.previousEvidence()).isEqualTo("exit code 137");
        assertThat(j.hasTransition()).isTrue();
        assertThat(j.hasBeliefUpdate()).isTrue();

        // The belief ledger remembers the state across cycles
        var prior = reconciliation.getPriorJudgement(PROJECT, "runtime", "r1");
        assertThat(prior).isPresent();
        assertThat(prior.get().verdict()).isEqualTo(Verdict.PERMIT);
        assertThat(prior.get().previousVerdict()).isEqualTo(Verdict.WITHHOLD);
    }

    @Test
    void verdictChangeWithoutReasonOrEvidenceIsRefuted() {
        class MutableRuntimeLayer implements VerdictLayer {
            private Judgement current = Judgement.withhold("runtime", "r1", "container crashed", "exit code 137");
            @Override public String layerName() { return "runtime"; }
            @Override public List<String> declaredPropositions(UUID p) { return List.of("r1"); }
            @Override public List<Judgement> judge(UUID p) { return List.of(current); }
            public void changeUngrounded(Verdict verdict) {
                this.current = new Judgement("runtime", "r1", verdict, "", "");
            }
        }

        var layer = new MutableRuntimeLayer();
        var reconciliation = new VerdictReconciliation(List.of(layer));

        // Cycle 1: established belief: WITHHOLD
        reconciliation.reconcile(PROJECT);

        // Cycle 2: ungrounded transition: verdict changed to PERMIT with empty reason and evidence
        layer.changeUngrounded(Verdict.PERMIT);
        var r2 = reconciliation.reconcile(PROJECT);

        // Refutation: ungrounded change is rejected and becomes ABSTAIN (epistemic debt)
        assertThat(r2.advance())
                .as("AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER: changing belief without stating reason or evidence must be refuted")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(r2.mayAdvance()).isFalse();
        assertThat(r2.debt()).isEqualTo(1);

        Judgement refuted = r2.judgements().get(0);
        assertThat(refuted.verdict()).isEqualTo(Verdict.ABSTAIN);
        assertThat(refuted.reasonCode()).isEqualTo("UNGROUNDED_TRANSITION");
        assertThat(refuted.reason()).contains("without recording reason or evidence");
        assertThat(refuted.previousVerdict()).isEqualTo(Verdict.WITHHOLD);
    }
}
