package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Popperian Falsification Suite for VerdictLayer and Verdict Lattice (Section VI).
 * Validates epistemic, modal, and deontic invariants under Stage 4:
 * - D012 Karl Popper Truth Status Table (KARL_POPPER_03_TRUTH_STATUS_TABLE):
 *     3-valued Kleene judgement lattice (PERMIT, WITHHOLD, ABSTAIN). Refusal dominates unconditionally.
 *     Abstention represents epistemic debt (absence of proof is not proof of absence).
 *     Conjunction is strictly monotonic: adding layers can never loosen or accidentally unblock.
 * - D004 Ruth Barcan Marcus Quantified Modal Domains (RUT_BARKAN_MARCUS_01_ACTUAL_POSSIBLE_DOMAINS):
 *     Barcan formula: domain of propositions declared prior to evaluation (declaredPropositions precedes judge).
 *     Undeclared propositions cannot spontaneously emerge; unruled declared propositions materialize as
 *     epistemic debt D(P) with ABSTAIN rather than silent disappearance.
 * - D006 Joseph Raz Deontic Prohibition As Code (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE):
 *     Monotonic layer reduction and actionable prohibition. Deontic refusals block operational actions
 *     with structured reason codes without threshold averaging. Critical exemption guarantees recovery
 *     tasks can execute, preventing self-locking deadlocks.
 */
class VerdictLayerFalsificationTest {

    private final UUID projectId = UUID.randomUUID();
    private final SystemSettingsService settings = mock(SystemSettingsService.class);

    private ProjectEntity createProject(String slug) {
        ProjectEntity p = new ProjectEntity();
        p.setId(projectId);
        p.setSlug(slug);
        return p;
    }

    private VerdictLayer createLayer(String name, List<String> declared, List<Judgement> judgements) {
        return new VerdictLayer() {
            @Override public String layerName() { return name; }
            @Override public List<String> declaredPropositions(UUID p) { return declared; }
            @Override public List<Judgement> judge(UUID p) { return judgements; }
        };
    }

    // =========================================================================
    // D012: Karl Popper Truth Status Table (KARL_POPPER_03_TRUTH_STATUS_TABLE)
    // =========================================================================

    @Test
    @DisplayName("D012 Popper Truth Status Table: Kleene 3-valued conjunction enforces WITHHOLD dominance over any approvals")
    void falsifyPopperTruthStatusTable_withholdDominatesAnyPermits() {
        // WITHHOLD ∧ PERMIT = WITHHOLD
        assertThat(Verdict.WITHHOLD.and(Verdict.PERMIT)).isEqualTo(Verdict.WITHHOLD);
        assertThat(Verdict.PERMIT.and(Verdict.WITHHOLD)).isEqualTo(Verdict.WITHHOLD);

        // WITHHOLD ∧ ABSTAIN = WITHHOLD
        assertThat(Verdict.WITHHOLD.and(Verdict.ABSTAIN)).isEqualTo(Verdict.WITHHOLD);
        assertThat(Verdict.ABSTAIN.and(Verdict.WITHHOLD)).isEqualTo(Verdict.WITHHOLD);

        // Null verdict treats missing layer as unestablished (ABSTAIN), or retains WITHHOLD
        assertThat(Verdict.PERMIT.and(null)).isEqualTo(Verdict.ABSTAIN);
        assertThat(Verdict.WITHHOLD.and(null)).isEqualTo(Verdict.WITHHOLD);

        // In multi-layer reconciliation: 9 approvals cannot outvote a single refusal
        VerdictLayer l1 = createLayer("l1", List.of("p1"), List.of(Judgement.permit("l1", "p1", "ok")));
        VerdictLayer l2 = createLayer("l2", List.of("p2"), List.of(Judgement.permit("l2", "p2", "ok")));
        VerdictLayer l3 = createLayer("l3", List.of("p3"), List.of(Judgement.permit("l3", "p3", "ok")));
        VerdictLayer l4 = createLayer("l4", List.of("p4"), List.of(Judgement.permit("l4", "p4", "ok")));
        VerdictLayer lRefusing = createLayer("lRefusing", List.of("p5"),
                List.of(Judgement.withhold("lRefusing", "p5", "CRITICAL_DEFECT", "defect observed")));

        VerdictReconciliation reconciliation = new VerdictReconciliation(List.of(l1, l2, l3, l4, lRefusing));
        VerdictReconciliation.Reconciliation result = reconciliation.reconcile(projectId);

        assertThat(result.advance())
                .as("Category error prevention: refusals cannot be averaged or outvoted by approvals")
                .isEqualTo(Verdict.WITHHOLD);
        assertThat(result.mayAdvance()).isFalse();
        assertThat(result.refusals()).isEqualTo(1);
    }

    @Test
    @DisplayName("D012 Popper Truth Status Table: ABSTAIN is epistemic debt, strictly blocking PERMIT")
    void falsifyPopperTruthStatusTable_abstainBlocksAdvanceAndRegistersDebt() {
        // PERMIT ∧ ABSTAIN = ABSTAIN
        assertThat(Verdict.PERMIT.and(Verdict.ABSTAIN)).isEqualTo(Verdict.ABSTAIN);
        assertThat(Verdict.ABSTAIN.and(Verdict.PERMIT)).isEqualTo(Verdict.ABSTAIN);

        VerdictLayer lPermit = createLayer("lPermit", List.of("p1"), List.of(Judgement.permit("lPermit", "p1", "ok")));
        VerdictLayer lAbstain = createLayer("lAbstain", List.of("p2"),
                List.of(Judgement.abstain("lAbstain", "p2", "telemetry offline / observation unestablished")));

        VerdictReconciliation reconciliation = new VerdictReconciliation(List.of(lPermit, lAbstain));
        VerdictReconciliation.Reconciliation result = reconciliation.reconcile(projectId);

        assertThat(result.advance())
                .as("Popperian falsification: lack of a negative finding is not proof of safety; advancement is withheld")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(result.mayAdvance()).isFalse();
        assertThat(result.debt()).isEqualTo(1);
        assertThat(result.refusals()).isZero();
    }

    @Test
    @DisplayName("D012 Popper Truth Status Table: Lattice extension is strictly monotonic (cannot unblock)")
    void falsifyPopperTruthStatusTable_monotonicityGuaranteesSafeSelfExtension() {
        VerdictLayer baseLayer = createLayer("base", List.of("p1"), List.of(Judgement.permit("base", "p1", "ok")));
        VerdictReconciliation baseReconciliation = new VerdictReconciliation(List.of(baseLayer));
        var baseResult = baseReconciliation.reconcile(projectId);
        assertThat(baseResult.mayAdvance()).isTrue();

        // Adding an abstaining or withholding layer cannot loosen the verdict
        VerdictLayer strictLayer = createLayer("strict", List.of("p2"),
                List.of(Judgement.abstain("strict", "p2", "unverified invariant")));
        VerdictReconciliation extendedReconciliation = new VerdictReconciliation(List.of(baseLayer, strictLayer));
        var extendedResult = extendedReconciliation.reconcile(projectId);

        assertThat(extendedResult.mayAdvance())
                .as("Monotonicity invariant: autonomous layer extension can only constrain, never accidentally unblock")
                .isFalse();
        assertThat(extendedResult.advance()).isEqualTo(Verdict.ABSTAIN);
    }

    // =========================================================================
    // D004: Ruth Barcan Marcus Modal Domains (RUT_BARKAN_MARCUS_01_ACTUAL_POSSIBLE_DOMAINS)
    // =========================================================================

    @Test
    @DisplayName("D004 Barcan Marcus: Declared propositions prior to ruling; unruled proposition generates epistemic debt")
    void falsifyBarcanMarcusModalDomains_unruledDeclaredPropositionGeneratesDebt() {
        // Layer declared 3 propositions up front: ["security", "performance", "correctness"]
        // But only evaluated "security" and "performance", failing to rule on "correctness"
        VerdictLayer partialLayer = new VerdictLayer() {
            @Override public String layerName() { return "audit"; }
            @Override public List<String> declaredPropositions(UUID p) {
                return List.of("security", "performance", "correctness");
            }
            @Override public List<Judgement> judge(UUID p) {
                return List.of(
                        Judgement.permit("audit", "security", "pass"),
                        Judgement.permit("audit", "performance", "pass")
                );
            }
        };

        VerdictReconciliation reconciliation = new VerdictReconciliation(List.of(partialLayer));
        var result = reconciliation.reconcile(projectId);

        assertThat(result.advance()).isEqualTo(Verdict.ABSTAIN);
        assertThat(result.debt()).isEqualTo(1);

        // Verify the unruled proposition is surfaced as an explicit Judgement.abstain
        assertThat(result.judgements())
                .anySatisfy(j -> {
                    assertThat(j.proposition()).isEqualTo("correctness");
                    assertThat(j.verdict()).isEqualTo(Verdict.ABSTAIN);
                    assertThat(j.reason()).contains("declared but not ruled on this cycle");
                });
    }

    @Test
    @DisplayName("D004 Barcan Marcus: Broken layer throwing RuntimeException is trapped as visible debt, not dropped")
    void falsifyBarcanMarcusModalDomains_throwingLayerDoesNotCrashObserverAndCreatesDebt() {
        VerdictLayer throwingLayer = new VerdictLayer() {
            @Override public String layerName() { return "faulty-layer"; }
            @Override public List<String> declaredPropositions(UUID p) { return List.of("contract"); }
            @Override public List<Judgement> judge(UUID p) {
                throw new IllegalStateException("Hetzner network partition or deserialization failure");
            }
        };

        VerdictReconciliation reconciliation = new VerdictReconciliation(List.of(throwingLayer));
        var result = reconciliation.reconcile(projectId);

        assertThat(result.advance()).isEqualTo(Verdict.ABSTAIN);
        assertThat(result.debt()).isEqualTo(1);
        assertThat(result.judgements())
                .hasSize(1)
                .allSatisfy(j -> {
                    assertThat(j.layer()).isEqualTo("faulty-layer");
                    assertThat(j.verdict()).isEqualTo(Verdict.ABSTAIN);
                    assertThat(j.reason()).contains("layer threw while judging: Hetzner network partition");
                });
    }

    // =========================================================================
    // D006: Joseph Raz Deontic Prohibition As Code (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE)
    // =========================================================================

    @Test
    @DisplayName("D006 Raz Prohibition As Code: VerdictGate enforces action prohibitions with typed reason codes")
    void falsifyRazProhibitionAsCode_actionProhibitionEnforcedWithTypedRule() {
        VerdictReconciliation mockReconciliation = mock(VerdictReconciliation.class);
        VerdictGate gate = new VerdictGate(mockReconciliation, settings);

        when(settings.effectiveBoolean(VerdictGate.FLAG)).thenReturn(true);
        when(settings.effectiveValue(VerdictGate.PROJECT_SLUG)).thenReturn("test-fiftieth");

        Judgement infraWithhold = Judgement.withhold(
                "infrastructure",
                "orchestrator db healthy",
                "INFRA_DB_CORRUPTED",
                "database bloat exceeded critical threshold",
                "file size 100MB, live 10MB"
        );

        when(mockReconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 1, "infrastructure", List.of(infraWithhold)
        ));

        ProjectEntity project = createProject("test-fiftieth");

        // Action: DISPATCH_QUEUED_TASKS under infra failure must be strictly prohibited
        VerdictGate.ActionProhibition prohibition = gate.evaluateActionProhibition(project, "DISPATCH_QUEUED_TASKS");

        assertThat(prohibition.prohibited()).isTrue();
        assertThat(prohibition.layer()).isEqualTo("infrastructure");
        assertThat(prohibition.ruleName()).isEqualTo("INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION");
        assertThat(prohibition.explanation()).contains("database bloat exceeded critical threshold");
    }

    @Test
    @DisplayName("D006 Raz Prohibition As Code: Recovery tasks are explicitly exempted from unrecovered doctrine failure prohibition")
    void falsifyRazProhibitionAsCode_recoveryTasksExemptedFromDoctrineProhibition() {
        VerdictReconciliation mockReconciliation = mock(VerdictReconciliation.class);
        VerdictGate gate = new VerdictGate(mockReconciliation, settings);

        when(settings.effectiveBoolean(VerdictGate.FLAG)).thenReturn(true);
        when(settings.effectiveValue(VerdictGate.PROJECT_SLUG)).thenReturn("test-fiftieth");

        Judgement doctrineFailure = Judgement.withhold(
                "doctrine",
                "unrecovered failure check",
                "UNRECOVERED_FAILED_WORK",
                "active unrecovered failed task exists in queue",
                "task-failed-99"
        );

        when(mockReconciliation.reconcile(projectId)).thenReturn(new VerdictReconciliation.Reconciliation(
                Verdict.WITHHOLD, 0, 1, "doctrine", List.of(doctrineFailure)
        ));

        ProjectEntity project = createProject("test-fiftieth");

        // Normal feature development task (retryCount = 0)
        TaskEntity regularTask = new TaskEntity();
        regularTask.setRetryCount(0);
        regularTask.setTitle("Expand user profile capabilities");

        // Recovery repair task (retryCount > 0)
        TaskEntity recoveryTask = new TaskEntity();
        recoveryTask.setRetryCount(1);
        recoveryTask.setTitle("Fix container bootstrap failure");

        VerdictGate.ActionProhibition regularProhibition = gate.evaluateTaskProhibition(project, regularTask);
        VerdictGate.ActionProhibition recoveryProhibition = gate.evaluateTaskProhibition(project, recoveryTask);

        assertThat(regularProhibition.prohibited())
                .as("Feature expansion and regular task dispatch are blocked during unrecovered failure")
                .isTrue();
        assertThat(regularProhibition.ruleName()).isEqualTo("DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION");
        assertThat(regularProhibition.exemptsRecoveryWork()).isTrue();
        assertThat(regularProhibition.allowsTask(false)).isFalse();

        assertThat(recoveryProhibition.prohibited())
                .as("Prohibition must not lock its own recovery path; recovery work is permitted")
                .isFalse();
        assertThat(regularProhibition.allowsTask(true)).isTrue();
    }
}
