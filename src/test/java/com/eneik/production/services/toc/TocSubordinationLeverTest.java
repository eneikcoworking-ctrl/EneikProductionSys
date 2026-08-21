package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
import com.eneik.production.services.lever.LeverStage;
import com.eneik.production.services.operational.OperationalAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TOC step 3. The tests below the 2026-08-21 marker assert the property that held while the rule was pure
// shadow: it returns the incumbent decision unchanged. They still hold, because an unstubbed
// LeverPromotionService reports no stage, and no stage means no evidence, which means change nothing. The
// tests after the marker cover the other half - what the rule does once its own ladder has promoted it. A
// subordination gate that is wrong freezes an entire project - measured once already, when one task in
// pending_review put the flow into SYSTEM_STALLED and the policy denied dispatch project-wide - which is
// why the promotion, and not a deploy, is what turns it on.
class TocSubordinationLeverTest {

    private static ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        return project;
    }

    private static ClientRuntimeObservationEntity observation(boolean launched, Integer health,
                                                               boolean instrumentFailure) {
        ClientRuntimeObservationEntity row = new ClientRuntimeObservationEntity();
        row.setObservedAt(Instant.now());
        row.setLaunchSuccess(launched);
        row.setHealthStatusCode(health);
        row.setInstrumentFailure(instrumentFailure);
        return row;
    }

    @Test
    void neverChangesTheDecisionItIsMeasuring() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(true, null, false)));

        var lever = new TocSubordinationLever(observations, levers);

        assertTrue(lever.subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true));
        assertFalse(lever.subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, false));
    }

    // While the constraint is open, work that does not serve it is slack - and the disagreement with the
    // policy is exactly what has to be counted before subordination is allowed to decide anything.
    @Test
    void recordsADisagreementWhenSlackWorkIsAllowedWhileTheConstraintIsOpen() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(true, null, false))); // launched, never served: unhealthy

        new TocSubordinationLever(observations, levers)
                .subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true);

        verify(levers).recordObservation(eq(TocSubordinationLever.T1_TOC_SUBORDINATION), any(),
                eq("allow"), eq("deny"), eq(LeverAgreement.FALSE), any());
    }

    // Subordination must never suppress the act that ends the constraint. A launchability check or an
    // observation is what clears it; forbidding those would make the constraint permanent.
    @Test
    void theActThatClearsTheConstraintIsNeverSubordinated() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(false, null, false)));

        new TocSubordinationLever(observations, levers)
                .subordinate(project, OperationalAction.CHECK_LAUNCHABILITY, true);

        verify(levers).recordObservation(any(), any(), eq("allow"), eq("allow"),
                eq(LeverAgreement.TRUE), any());
    }

    // A healthy product means no constraint, so nothing is recorded at all - the lever must not accumulate
    // evidence about a situation it has no opinion on.
    @Test
    void recordsNothingWhileTheProductIsHealthy() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(true, 200, false)));

        new TocSubordinationLever(observations, levers)
                .subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true);

        verify(levers, never()).recordObservation(any(), any(), any(), any(), any(), any());
    }

    // An instrument failure taught us nothing about the product, so it can neither open a constraint nor
    // close one - the same rule V104 applies to the posterior.
    @Test
    void anInstrumentFailureIsNotEvidenceOfAConstraint() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(false, null, true), observation(true, 200, false)));

        new TocSubordinationLever(observations, levers)
                .subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true);

        verify(levers, never()).recordObservation(any(), any(), any(), any(), any(), any());
    }

    // A project never observed is not a project known to be broken.
    @Test
    void aProjectNeverObservedSubordinatesNothing() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any())).thenReturn(List.of());

        assertTrue(new TocSubordinationLever(observations, levers)
                .subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true));
        verify(levers, never()).recordObservation(any(), any(), any(), any(), any(), any());
    }

    // --- 2026-08-21, plan L-6: what the rule does once its ladder has promoted it ------------------------

    private static TocSubordinationLever leverAt(ClientRuntimeObservationRepository observations,
                                                  LeverPromotionService levers,
                                                  LeverStage stage) {
        when(levers.currentStage(TocSubordinationLever.T1_TOC_SUBORDINATION)).thenReturn(stage);
        return new TocSubordinationLever(observations, levers);
    }

    @Test
    void atObserveOnlyTheRuleStillDecidesNothingEvenWhenItDisagrees() {
        // The property the shadow period was built to guarantee, now asserted against an explicit stage
        // rather than against an unstubbed mock: promotion, not a deploy, is what turns this on.
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(false, null, false)));

        var lever = leverAt(observations, levers, LeverStage.OBSERVE_ONLY);

        assertTrue(lever.subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true));
        verify(levers).recordObservation(eq(TocSubordinationLever.T1_TOC_SUBORDINATION), any(),
                eq("allow"), eq("deny"), eq(LeverAgreement.FALSE), any());
    }

    @Test
    void fromSoftGateUpwardSlackIsDeniedWhileTheConstraintIsOpen() {
        // The whole point of step 3: an action that cannot move the product toward starting again is slack,
        // and slack idles so the constraint never waits.
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(true, null, false)));

        var lever = leverAt(observations, levers, LeverStage.SOFT_GATE);

        assertFalse(lever.subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true));
    }

    @Test
    void evenWhenDecidingItCanTakeAPermissionAwayButNeverGrantOne() {
        // Subordination restricts slack; it is never a licence for slack to do something the flow state
        // already forbids. MERGE_PR serves the constraint, so the rule would say allow - and must still
        // return the policy's deny.
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(true, null, false)));

        var lever = leverAt(observations, levers, LeverStage.HARD_GATE);

        assertFalse(lever.subordinate(project, OperationalAction.MERGE_PR, false));
    }

    @Test
    void whileDecidingItStillNeverBlocksTheActThatCouldClearTheConstraint() {
        // Forbidding observation would make the constraint permanent: only a fresh healthy observation
        // clears it, so the rule must never suppress the very act that ends it.
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(true, null, false)));

        var lever = leverAt(observations, levers, LeverStage.HARD_GATE);

        assertTrue(lever.subordinate(project, OperationalAction.CHECK_LAUNCHABILITY, true));
        assertTrue(lever.subordinate(project, OperationalAction.OBSERVE, true));
    }

    @Test
    void anInstrumentFailureIsNotEvidenceThatTheProductIsBroken() {
        // Same rule as the posterior's (V104): a launch nobody answered is not a launch that failed, so it
        // must not be what puts the whole project into subordination.
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(eq(project.getId()), any()))
                .thenReturn(List.of(observation(false, null, true), observation(true, 200, false)));

        var lever = leverAt(observations, levers, LeverStage.HARD_GATE);

        assertTrue(lever.subordinate(project, OperationalAction.CHECK_COVERAGE_AUDITS, true));
        verify(levers, never()).recordObservation(any(), any(), any(), any(), any(), any());
    }
}
