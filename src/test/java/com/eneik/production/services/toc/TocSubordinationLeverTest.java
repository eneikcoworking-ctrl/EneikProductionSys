package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
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

// TOC step 3 measured in shadow. Every test here asserts the same overriding property first: the lever
// returns the incumbent decision unchanged. A subordination gate that is wrong freezes an entire project -
// measured once already, when one task in pending_review put the flow into SYSTEM_STALLED and the policy
// denied dispatch project-wide.
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
        when(observations.findByProjectIdOrderByObservedAtDesc(project.getId()))
                .thenReturn(List.of(observation(true, null, false)));

        var lever = new TocSubordinationLever(observations, levers);

        assertTrue(lever.observe(project, OperationalAction.CHECK_COVERAGE_AUDITS, true));
        assertFalse(lever.observe(project, OperationalAction.CHECK_COVERAGE_AUDITS, false));
    }

    // While the constraint is open, work that does not serve it is slack - and the disagreement with the
    // policy is exactly what has to be counted before subordination is allowed to decide anything.
    @Test
    void recordsADisagreementWhenSlackWorkIsAllowedWhileTheConstraintIsOpen() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(project.getId()))
                .thenReturn(List.of(observation(true, null, false))); // launched, never served: unhealthy

        new TocSubordinationLever(observations, levers)
                .observe(project, OperationalAction.CHECK_COVERAGE_AUDITS, true);

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
        when(observations.findByProjectIdOrderByObservedAtDesc(project.getId()))
                .thenReturn(List.of(observation(false, null, false)));

        new TocSubordinationLever(observations, levers)
                .observe(project, OperationalAction.CHECK_LAUNCHABILITY, true);

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
        when(observations.findByProjectIdOrderByObservedAtDesc(project.getId()))
                .thenReturn(List.of(observation(true, 200, false)));

        new TocSubordinationLever(observations, levers)
                .observe(project, OperationalAction.CHECK_COVERAGE_AUDITS, true);

        verify(levers, never()).recordObservation(any(), any(), any(), any(), any(), any());
    }

    // An instrument failure taught us nothing about the product, so it can neither open a constraint nor
    // close one - the same rule V104 applies to the posterior.
    @Test
    void anInstrumentFailureIsNotEvidenceOfAConstraint() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(project.getId()))
                .thenReturn(List.of(observation(false, null, true), observation(true, 200, false)));

        new TocSubordinationLever(observations, levers)
                .observe(project, OperationalAction.CHECK_COVERAGE_AUDITS, true);

        verify(levers, never()).recordObservation(any(), any(), any(), any(), any(), any());
    }

    // A project never observed is not a project known to be broken.
    @Test
    void aProjectNeverObservedSubordinatesNothing() {
        var observations = mock(ClientRuntimeObservationRepository.class);
        var levers = mock(LeverPromotionService.class);
        ProjectEntity project = project();
        when(observations.findByProjectIdOrderByObservedAtDesc(project.getId())).thenReturn(List.of());

        assertTrue(new TocSubordinationLever(observations, levers)
                .observe(project, OperationalAction.CHECK_COVERAGE_AUDITS, true));
        verify(levers, never()).recordObservation(any(), any(), any(), any(), any(), any());
    }
}
