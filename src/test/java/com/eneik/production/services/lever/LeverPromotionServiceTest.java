package com.eneik.production.services.lever;

import com.eneik.production.models.persistence.LeverObservation;
import com.eneik.production.models.persistence.LeverPromotionStateEntity;
import com.eneik.production.repositories.LeverObservationRepository;
import com.eneik.production.repositories.LeverPromotionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The "lift" for the promotion ladder OperationalTruthService already documents as vocabulary
 * (2026-08-08 ML-update patch, Phase 0) - covers the 3 behaviors the whole patch's safety story depends
 * on: a fresh lever starts at observe_only with zero live effect, real agreement over the recency window
 * promotes it one stage at a time, and a single real disagreement after promotion demotes it immediately.
 */
class LeverPromotionServiceTest {

    private LeverPromotionStateRepository stateRepository;
    private LeverObservationRepository observationRepository;
    private LeverPromotionService service;

    @BeforeEach
    void setUp() {
        stateRepository = mock(LeverPromotionStateRepository.class);
        observationRepository = mock(LeverObservationRepository.class);
        service = new LeverPromotionService(stateRepository, observationRepository);
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aNeverObservedLeverDefaultsToObserveOnly() {
        when(stateRepository.findById("UNKNOWN_LEVER")).thenReturn(Optional.empty());
        assertEquals(LeverStage.OBSERVE_ONLY, service.currentStage("UNKNOWN_LEVER"));
    }

    @Test
    void recordingAnObservationCreatesTheStateRowAtObserveOnlyForABrandNewLever() {
        when(stateRepository.findById("NEW_LEVER")).thenReturn(Optional.empty());

        service.recordObservation("NEW_LEVER", "subj-1", "incumbent", "candidate", LeverAgreement.NEITHER, null);

        var captor = org.mockito.ArgumentCaptor.forClass(LeverPromotionStateEntity.class);
        org.mockito.Mockito.verify(stateRepository).save(captor.capture());
        assertEquals("observe_only", captor.getValue().getCurrentStage());
        assertEquals(1L, captor.getValue().getSampleCount());
    }

    @Test
    void realDisagreementImmediatelyDemotesAPromotedLever() {
        LeverPromotionStateEntity state = new LeverPromotionStateEntity();
        state.setLeverKey("PROMOTED_LEVER");
        state.setCurrentStage(LeverStage.SOFT_GATE.wireValue());
        state.setSampleCount(50);
        state.setAgreementCount(45);
        when(stateRepository.findById("PROMOTED_LEVER")).thenReturn(Optional.of(state));

        service.recordObservation("PROMOTED_LEVER", "subj-2", "incumbent", "candidate", LeverAgreement.FALSE, "incumbent");

        var captor = org.mockito.ArgumentCaptor.forClass(LeverPromotionStateEntity.class);
        org.mockito.Mockito.verify(stateRepository).save(captor.capture());
        assertEquals(LeverStage.WARN_ONLY.wireValue(), captor.getValue().getCurrentStage());
    }

    @Test
    void observeOnlyLeverIsNotDemotedFurtherOnDisagreement() {
        LeverPromotionStateEntity state = new LeverPromotionStateEntity();
        state.setLeverKey("FRESH_LEVER");
        state.setCurrentStage(LeverStage.OBSERVE_ONLY.wireValue());
        when(stateRepository.findById("FRESH_LEVER")).thenReturn(Optional.of(state));

        service.recordObservation("FRESH_LEVER", "subj-3", "incumbent", "candidate", LeverAgreement.FALSE, "incumbent");

        var captor = org.mockito.ArgumentCaptor.forClass(LeverPromotionStateEntity.class);
        org.mockito.Mockito.verify(stateRepository).save(captor.capture());
        assertEquals(LeverStage.OBSERVE_ONLY.wireValue(), captor.getValue().getCurrentStage());
    }

    @Test
    void tooFewResolvedSamplesDoesNotPromoteEvenWithPerfectAgreement() {
        LeverPromotionStateEntity state = new LeverPromotionStateEntity();
        state.setLeverKey("SPARSE_LEVER");
        state.setCurrentStage(LeverStage.OBSERVE_ONLY.wireValue());
        when(stateRepository.findAll()).thenReturn(List.of(state));

        List<LeverObservation> fewResolved = observationsWithAgreement(5, "TRUE");
        when(observationRepository.findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(eqLeverKey("SPARSE_LEVER"), any()))
                .thenReturn(fewResolved);

        service.evaluatePromotions();

        var captor = org.mockito.ArgumentCaptor.forClass(LeverPromotionStateEntity.class);
        org.mockito.Mockito.verify(stateRepository).save(captor.capture());
        assertEquals(LeverStage.OBSERVE_ONLY.wireValue(), captor.getValue().getCurrentStage());
    }

    @Test
    void enoughResolvedSamplesWithHighAgreementPromotesOneStage() {
        LeverPromotionStateEntity state = new LeverPromotionStateEntity();
        state.setLeverKey("READY_LEVER");
        state.setCurrentStage(LeverStage.OBSERVE_ONLY.wireValue());
        when(stateRepository.findAll()).thenReturn(List.of(state));

        List<LeverObservation> mostlyAgreeing = new ArrayList<>();
        mostlyAgreeing.addAll(observationsWithAgreement(18, "TRUE"));
        mostlyAgreeing.addAll(observationsWithAgreement(2, "FALSE"));
        when(observationRepository.findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(eqLeverKey("READY_LEVER"), any()))
                .thenReturn(mostlyAgreeing);

        service.evaluatePromotions();

        var captor = org.mockito.ArgumentCaptor.forClass(LeverPromotionStateEntity.class);
        org.mockito.Mockito.verify(stateRepository).save(captor.capture());
        assertEquals(LeverStage.WARN_ONLY.wireValue(), captor.getValue().getCurrentStage());
    }

    private static String eqLeverKey(String key) {
        return org.mockito.ArgumentMatchers.eq(key);
    }

    private static List<LeverObservation> observationsWithAgreement(int count, String agreement) {
        List<LeverObservation> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LeverObservation o = new LeverObservation();
            o.setAgreement(agreement);
            o.setObservedAt(Instant.now());
            list.add(o);
        }
        return list;
    }
}
