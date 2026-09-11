package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The gate's whole safety argument is that it can only ever subtract permission, and that it declines to
 * act in three distinct ways rather than permitting by accident. Both are pinned here.
 *
 * The dangerous case is the empty lattice: the conjunction over no propositions is PERMIT, which is correct
 * arithmetic and would be a claim made out of silence.
 */
class VerdictGateTest {

    private final VerdictReconciliation reconciliation = mock(VerdictReconciliation.class);
    private final SystemSettingsService settings = mock(SystemSettingsService.class);
    private final VerdictGate gate = new VerdictGate(reconciliation, settings);

    private final UUID projectId = UUID.randomUUID();

    private ProjectEntity project(String slug) {
        ProjectEntity p = new ProjectEntity();
        p.setId(projectId);
        p.setSlug(slug);
        return p;
    }

    private void gatingOn(String scopedSlug) {
        when(settings.effectiveBoolean(VerdictGate.FLAG)).thenReturn(true);
        when(settings.effectiveValue(VerdictGate.PROJECT_SLUG)).thenReturn(scopedSlug);
    }

    private void latticeSays(Verdict advance, Judgement... judgements) {
        when(reconciliation.reconcile(any())).thenReturn(new VerdictReconciliation.Reconciliation(
                advance, 0, 0, "", List.of(judgements)));
    }

    private Judgement refusal() {
        return Judgement.withhold("runtime", "the delivered product launches", "docker compose up failed", "obs-1");
    }

    @Test
    void theFlagOffLeavesTheReportExactlyAsItWas() {
        when(settings.effectiveBoolean(VerdictGate.FLAG)).thenReturn(false);
        latticeSays(Verdict.WITHHOLD, refusal());

        VerdictGate.Decision d = gate.constrain(project("test-forty-seventh"), Verdict.PERMIT);

        assertThat(d.verdict()).isEqualTo(Verdict.PERMIT);
        assertThat(d.applied())
                .as("'the gate ran and agreed' and 'the gate never ran' are different facts, and a rollout "
                        + "cannot be judged if they look the same")
                .isFalse();
    }

    @Test
    void anEmptyScopeMeansNoProjectRatherThanEveryProject() {
        when(settings.effectiveBoolean(VerdictGate.FLAG)).thenReturn(true);
        when(settings.effectiveValue(VerdictGate.PROJECT_SLUG)).thenReturn("");
        latticeSays(Verdict.WITHHOLD, refusal());

        assertThat(gate.constrain(project("test-forty-seventh"), Verdict.PERMIT).applied())
                .as("a scoping value that falls back to 'all' turns the first careless deploy into a "
                        + "factory-wide change, which is the opposite of a staged rollout")
                .isFalse();
    }

    @Test
    void anotherProjectIsUntouchedWhileOneIsBeingTrusted() {
        gatingOn("test-forty-seventh");
        latticeSays(Verdict.WITHHOLD, refusal());

        assertThat(gate.constrain(project("test-forty-sixth"), Verdict.PERMIT).applied()).isFalse();
    }

    @Test
    void anEmptyLatticeStandsAsideInsteadOfPermittingOutOfSilence() {
        gatingOn("test-forty-seventh");
        latticeSays(Verdict.PERMIT); // no judgements at all

        VerdictGate.Decision d = gate.constrain(project("test-forty-seventh"), Verdict.PERMIT);

        assertThat(d.applied())
                .as("the conjunction over no propositions is PERMIT - correct arithmetic, and a claim made "
                        + "out of silence. A gate that approves because it has nothing to say is worse "
                        + "than no gate")
                .isFalse();
    }

    @Test
    void aRefusalOverridesAConstructionThatWouldHaveReportedReady() {
        gatingOn("test-forty-seventh");
        latticeSays(Verdict.WITHHOLD, refusal());

        VerdictGate.Decision d = gate.constrain(project("test-forty-seventh"), Verdict.PERMIT);

        assertThat(d.verdict()).isEqualTo(Verdict.WITHHOLD);
        assertThat(d.applied()).isTrue();
        assertThat(d.reasons())
                .as("a refusal a human cannot check is an accusation - the layer, the proposition and the "
                        + "layer's own words travel with it")
                .anySatisfy(r -> assertThat(r)
                        .contains("runtime")
                        .contains("refuses")
                        .contains("docker compose up failed"));
    }

    @Test
    void anAbstentionBlocksButIsNotReportedAsARefusal() {
        gatingOn("test-forty-seventh");
        latticeSays(Verdict.ABSTAIN,
                Judgement.abstain("acceptance", "the client has walked every chain", "no traversal recorded"));

        VerdictGate.Decision d = gate.constrain(project("test-forty-seventh"), Verdict.PERMIT);

        assertThat(d.verdict())
                .as("an unestablished claim is not a refuted one; both block, and the difference is what "
                        + "the operator is told to do next")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(d.reasons()).anySatisfy(r -> assertThat(r).contains("cannot say"));
    }

    @Test
    void theGateCanOnlySubtractPermissionNeverAddIt() {
        gatingOn("test-forty-seventh");
        latticeSays(Verdict.PERMIT, Judgement.permit("runtime", "the delivered product launches", "obs-1"));

        assertThat(gate.constrain(project("test-forty-seventh"), Verdict.WITHHOLD).verdict())
                .as("monotonicity is the whole safety argument for turning this on: no configuration of "
                        + "the lattice may cause readiness to be claimed that would not have been claimed "
                        + "before")
                .isEqualTo(Verdict.WITHHOLD);
        assertThat(gate.constrain(project("test-forty-seventh"), Verdict.ABSTAIN).verdict())
                .isEqualTo(Verdict.ABSTAIN);
    }

    @Test
    void aFailingReconciliationLeavesTheReportUnchanged() {
        gatingOn("test-forty-seventh");
        when(reconciliation.reconcile(any())).thenThrow(new IllegalStateException("db down"));

        VerdictGate.Decision d = gate.constrain(project("test-forty-seventh"), Verdict.PERMIT);

        assertThat(d.verdict())
                .as("the gate must never become the outage it exists to prevent - declining is not the "
                        + "same as permitting, the caller's own verdict simply stands")
                .isEqualTo(Verdict.PERMIT);
        assertThat(d.applied()).isFalse();
    }

    // DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006) / Prescription 9:
    // Every forbidden action has an executable denial path with an explainable reason, named rule, and non-absorbing reversibility.

    @Test
    void infrastructureRefusalProhibitsTaskDispatchWithNamedRuleAndReason() {
        gatingOn("test-forty-seventh");
        Judgement dbUnhealthy = Judgement.withhold("infrastructure",
                "the orchestrator's own database is healthy",
                "bloat 4.2 > threshold", "file=100MB");
        latticeSays(Verdict.WITHHOLD, dbUnhealthy);

        VerdictGate.ActionProhibition prohibition = gate.evaluateActionProhibition(
                project("test-forty-seventh"), "DISPATCH_QUEUED_TASKS");

        assertThat(prohibition.prohibited()).isTrue();
        assertThat(prohibition.layer()).isEqualTo("infrastructure");
        assertThat(prohibition.ruleName()).isEqualTo("INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION");
        assertThat(prohibition.explanation())
                .contains("INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION")
                .contains("the orchestrator's own database is healthy")
                .contains("bloat 4.2 > threshold");
    }

    @Test
    void infrastructureRecoveryLiftsProhibitionAndPermitsDispatch() {
        gatingOn("test-forty-seventh");

        // Violation: DB is unhealthy
        Judgement dbUnhealthy = Judgement.withhold("infrastructure",
                "the orchestrator's own database is healthy",
                "bloat 4.2 > threshold", "file=100MB");
        latticeSays(Verdict.WITHHOLD, dbUnhealthy);
        assertThat(gate.evaluateActionProhibition(project("test-forty-seventh"), "DISPATCH_QUEUED_TASKS").prohibited()).isTrue();

        // Recovery: DB health is restored
        Judgement dbHealthy = Judgement.permit("infrastructure",
                "the orchestrator's own database is healthy",
                "bloat 1.1 <= threshold");
        latticeSays(Verdict.PERMIT, dbHealthy);

        VerdictGate.ActionProhibition afterRecovery = gate.evaluateActionProhibition(
                project("test-forty-seventh"), "DISPATCH_QUEUED_TASKS");

        assertThat(afterRecovery.prohibited())
                .as("Prohibition must not be absorbing: once condition clears, gate permits the action")
                .isFalse();
    }

    @Test
    void doctrineUnrecoveredFailureProhibitsActionAndLiftsOnRecovery() {
        gatingOn("test-forty-seventh");

        // Violation: doctrine layer refuses due to unrecovered failed work
        Judgement doctrineRefusal = Judgement.withhold("doctrine",
                "doctrine BARCAN-TAG-00 accepts the current project state",
                "Owner-role execution has unrecovered failed work; recover through a fresh atomic wishlist item before claiming doctrine satisfaction.",
                "stance=refuses");
        latticeSays(Verdict.WITHHOLD, doctrineRefusal);

        VerdictGate.ActionProhibition prohibition = gate.evaluateActionProhibition(
                project("test-forty-seventh"), "DISPATCH_QUEUED_TASKS");

        assertThat(prohibition.prohibited()).isTrue();
        assertThat(prohibition.layer()).isEqualTo("doctrine");
        assertThat(prohibition.ruleName()).isEqualTo("DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION");
        assertThat(prohibition.explanation()).contains("unrecovered failed work");

        // Recovery: doctrine failure resolved
        Judgement doctrinePermit = Judgement.permit("doctrine",
                "doctrine BARCAN-TAG-00 accepts the current project state",
                "stance=satisfied");
        latticeSays(Verdict.PERMIT, doctrinePermit);

        assertThat(gate.evaluateActionProhibition(project("test-forty-seventh"), "DISPATCH_QUEUED_TASKS").prohibited()).isFalse();
    }

    @Test
    void prohibitionStandsAsideWhenGatingDisabledOrDifferentProject() {
        // Flag off: stands aside
        when(settings.effectiveBoolean(VerdictGate.FLAG)).thenReturn(false);
        Judgement dbUnhealthy = Judgement.withhold("infrastructure",
                "the orchestrator's own database is healthy", "outage", "");
        latticeSays(Verdict.WITHHOLD, dbUnhealthy);

        assertThat(gate.evaluateActionProhibition(project("test-forty-seventh"), "DISPATCH_QUEUED_TASKS").prohibited()).isFalse();

        // Scoped to another project: stands aside
        gatingOn("different-project");
        assertThat(gate.evaluateActionProhibition(project("test-forty-seventh"), "DISPATCH_QUEUED_TASKS").prohibited()).isFalse();
    }
}
