package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.coherence.EvidenceCoherenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Falsification test suite for {@link FeatureService} grounding Stage 4 invariants:
 *
 * <p>1. Varzi (D002, {@code AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER}):
 * Actual domain objects possess identity, lineage, and lifecycle rooted in an aggregate. Features
 * are minted lazily once, at the exact moment a wishlist row is compiled into actual work. Unclassified
 * entities are assigned {@code UNCLASSIFIED_COMPONENT = 20.0} (never a middle guessed value), and
 * no feature can enter the {@code CORE} epistemic layer without a verified contract (E_EMS).
 *
 * <p>2. Frege (D009, {@code GOTLOB_FREGE_01_SUBSTITUTION_ORACLE}):
 * Principle of sense and reference (Sinn und Bedeutung) & substitutivity salva veritate.
 * Resolving existing epics never trusts IDs blindly: cross-project IDs, malformed strings, or
 * dismissed epics are never substituted into the active matching set.
 *
 * <p>3. Goldman (D010, {@code ELVIN_GOLDMAN_01_RELIABILITY_CHAIN}):
 * Causal theory of knowing and reliable data lineage. Child slices retain their immutable
 * {@code originFeatureId} back to the root brief, and feature hypotheses are evaluated against
 * the explanatory coherence graph.
 */
class FeatureServiceFalsificationTest {

    private FeatureRepository featureRepository;
    private WishlistRepository wishlistRepository;
    private EvidenceCoherenceService evidenceCoherenceService;
    private EpistemicMetadataClassifier epistemicMetadataClassifier;
    private FeatureService featureService;

    @BeforeEach
    void setUp() {
        featureRepository = mock(FeatureRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        evidenceCoherenceService = mock(EvidenceCoherenceService.class);
        epistemicMetadataClassifier = new EpistemicMetadataClassifier();

        when(evidenceCoherenceService.evaluateFeatureHypothesis(any(), any(), anyDouble(), any()))
                .thenReturn(new EvidenceCoherenceService.HypothesisEvaluationResult(true, 1.0, List.of(), "Default acceptance"));

        featureService = new FeatureService(
                featureRepository,
                wishlistRepository,
                evidenceCoherenceService,
                epistemicMetadataClassifier
        );
    }

    // =========================================================================
    // 1. Varzi (D002): AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER
    // =========================================================================

    @Test
    @DisplayName("Varzi [D002]: lazy minting creates actual Feature entity with self-lineage, never minting duplicates on reuse")
    void falsifyVarzi_actualObjectRegister_lazyMintingCreatesValidAggregateWithLineage() {
        UUID projectId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setContent("Build secure chess player authentication");

        UUID generatedFeatureId = UUID.randomUUID();
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(invocation -> {
            FeatureEntity f = invocation.getArgument(0);
            if (f.getId() == null) {
                f.setId(generatedFeatureId);
            }
            return f;
        });

        // First call: mints lazily
        UUID resolvedId = featureService.resolveOrCreateFeatureId(wishlist, projectId);

        assertThat(resolvedId)
                .as("Resolved feature ID must match the newly minted entity ID")
                .isEqualTo(generatedFeatureId);
        assertThat(wishlist.getFeatureId()).isEqualTo(generatedFeatureId);
        assertThat(wishlist.getOriginFeatureId()).isEqualTo(generatedFeatureId);

        // Verification of lazy minting & lineage: save called twice (mint id + stamp originFeatureId)
        ArgumentCaptor<FeatureEntity> featureCaptor = ArgumentCaptor.forClass(FeatureEntity.class);
        verify(featureRepository, times(2)).save(featureCaptor.capture());
        FeatureEntity savedFeature = featureCaptor.getValue();
        assertThat(savedFeature.getProjectId()).isEqualTo(projectId);
        assertThat(savedFeature.getRootWishlistId()).isEqualTo(wishlist.getId());
        assertThat(savedFeature.getOriginFeatureId()).isEqualTo(generatedFeatureId);
        verify(wishlistRepository).save(wishlist);

        // Second call with already-set featureId: early return, NO new entity created
        UUID secondCallId = featureService.resolveOrCreateFeatureId(wishlist, projectId);
        assertThat(secondCallId).isEqualTo(generatedFeatureId);
        verify(featureRepository, times(2)).save(any()); // No additional calls
    }

    @Test
    @DisplayName("Varzi [D002]: Quine-Gärdenfors EE formula scores unclassified entities at bottom (15.0) and requires contract for CORE")
    void falsifyVarzi_epistemicEntrenchment_noMidpointGuessAndContractGuardsCore() {
        // Case 1: Unclassified feature (null/empty inputs) must score 15.0 (PERIPHERY), not a fabricated middle score
        double unclassifiedScore = featureService.calculateEpistemicEntrenchment(null, null, 0.0);
        // EE = 0.40 * 20.0 + 0.35 * 20.0 + 0.25 * 0.0 = 8.0 + 7.0 + 0.0 = 15.0
        assertThat(unclassifiedScore)
                .as("Unclassified component must evaluate to exactly 15.0 in Quine-Gärdenfors web")
                .isEqualTo(15.0);
        assertThat(featureService.classifyEpistemicLayer(unclassifiedScore))
                .isEqualTo("PERIPHERY");

        // Case 2: Maximum possible score without EMS contract (E_EMS = 0) is 67.5, which CANNOT enter CORE (>= 75.0)
        double maxWithoutContract = featureService.calculateEpistemicEntrenchment("must-be", "clear", 0.0);
        // EE = 0.40 * 90.0 + 0.35 * 90.0 + 0.25 * 0.0 = 36.0 + 31.5 = 67.5
        assertThat(maxWithoutContract).isEqualTo(67.5);
        assertThat(featureService.classifyEpistemicLayer(maxWithoutContract))
                .as("No feature can enter CORE without a verified EMS contract, preserving Varzi ontology invariant")
                .isEqualTo("CONTRACT");

        // Case 3: Fully verified contract (JTBD 40 + SixSigma 30 + TOC 30 = 100) enters CORE
        double fullCoreScore = featureService.calculateEpistemicEntrenchment("must-be", "clear", 100.0);
        // EE = 0.40 * 90.0 + 0.35 * 90.0 + 0.25 * 100.0 = 36.0 + 31.5 + 25.0 = 92.5
        assertThat(fullCoreScore).isEqualTo(92.5);
        assertThat(featureService.classifyEpistemicLayer(fullCoreScore)).isEqualTo("CORE");
    }

    // =========================================================================
    // 2. Frege (D009): GOTLOB_FREGE_01_SUBSTITUTION_ORACLE
    // =========================================================================

    @Test
    @DisplayName("Frege [D009]: findExistingEpic preserves Sinn and Bedeutung - cross-project IDs and malformed strings never substitute")
    void falsifyFrege_substitutionOracle_findExistingEpicPreservesSenseAndReferent() {
        UUID projectA = UUID.randomUUID();
        UUID projectB = UUID.randomUUID();
        UUID featureIdOnB = UUID.randomUUID();

        FeatureEntity foreignFeature = new FeatureEntity();
        foreignFeature.setId(featureIdOnB);
        foreignFeature.setProjectId(projectB);

        when(featureRepository.findById(featureIdOnB)).thenReturn(Optional.of(foreignFeature));

        // Attempting to resolve foreign feature on Project A: must fail closed (Optional.empty)
        Optional<FeatureEntity> crossProjectResult = featureService.findExistingEpic(projectA, featureIdOnB.toString());
        assertThat(crossProjectResult)
                .as("Cross-project feature identity must be rejected, preserving project referential boundary")
                .isEmpty();

        // Valid resolve on Project B: succeeds
        Optional<FeatureEntity> validResult = featureService.findExistingEpic(projectB, featureIdOnB.toString());
        assertThat(validResult).isPresent();
        assertThat(validResult.get().getId()).isEqualTo(featureIdOnB);

        // Malformed, blank, or null raw string must return Optional.empty without throwing
        assertThat(featureService.findExistingEpic(projectA, null)).isEmpty();
        assertThat(featureService.findExistingEpic(projectA, "   ")).isEmpty();
        assertThat(featureService.findExistingEpic(projectA, "not-a-valid-uuid")).isEmpty();
    }

    @Test
    @DisplayName("Frege [D009]: listExistingEpics excludes dismissed epics from the candidate matching set")
    void falsifyFrege_substitutionOracle_listExistingEpicsFiltersDismissed() {
        UUID projectId = UUID.randomUUID();
        FeatureEntity activeEpic = new FeatureEntity();
        activeEpic.setId(UUID.randomUUID());
        activeEpic.setProjectId(projectId);
        activeEpic.setTitle("Active Epic");

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(activeEpic));

        List<FeatureEntity> candidates = featureService.listExistingEpics(projectId);

        assertThat(candidates).containsExactly(activeEpic);
        verify(featureRepository).findByProjectIdAndDismissedAtIsNull(projectId);
    }

    // =========================================================================
    // 3. Goldman (D010): ELVIN_GOLDMAN_01_RELIABILITY_CHAIN
    // =========================================================================

    @Test
    @DisplayName("Goldman [D010]: child slices preserve unbroken originFeatureId lineage from the root brief")
    void falsifyGoldman_reliabilityChain_preservesUnbrokenLineageAndEvaluatesCoherence() {
        UUID projectId = UUID.randomUUID();
        UUID rootOriginId = UUID.randomUUID();

        // Child slice has originFeatureId already set from parent brief, but featureId is null
        WishlistEntity childSlice = new WishlistEntity();
        childSlice.setId(UUID.randomUUID());
        childSlice.setOriginFeatureId(rootOriginId);
        childSlice.setContent("Slice 2: database migrations for player entities");

        UUID sliceFeatureId = UUID.randomUUID();
        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(invocation -> {
            FeatureEntity f = invocation.getArgument(0);
            if (f.getId() == null) {
                f.setId(sliceFeatureId);
            }
            return f;
        });

        when(evidenceCoherenceService.evaluateFeatureHypothesis(eq(projectId), anyString(), anyDouble(), anyString()))
                .thenReturn(new EvidenceCoherenceService.HypothesisEvaluationResult(true, 0.95, List.of(), "High coherence"));

        UUID resolved = featureService.resolveOrCreateFeatureId(childSlice, projectId);

        assertThat(resolved).isEqualTo(sliceFeatureId);
        assertThat(childSlice.getFeatureId()).isEqualTo(sliceFeatureId);
        assertThat(childSlice.getOriginFeatureId())
                .as("Child slice must preserve its root originFeatureId, ensuring unbroken causal lineage")
                .isEqualTo(rootOriginId);

        // Verification of Goldman second-order knowledge check: evidence coherence evaluation invoked
        verify(evidenceCoherenceService).evaluateFeatureHypothesis(
                eq(projectId),
                eq(childSlice.getContent()),
                anyDouble(),
                anyString()
        );
    }

    @Test
    @DisplayName("Goldman [D010]: createFeature sets originFeatureId to self and records full classification pedigree")
    void falsifyGoldman_reliabilityChain_createFeatureRecordsFullClassificationPedigree() {
        UUID projectId = UUID.randomUUID();
        UUID rootWishlistId = UUID.randomUUID();
        UUID mintedId = UUID.randomUUID();

        when(featureRepository.save(any(FeatureEntity.class))).thenAnswer(invocation -> {
            FeatureEntity f = invocation.getArgument(0);
            if (f.getId() == null) {
                f.setId(mintedId);
            }
            return f;
        });

        FeatureEntity created = featureService.createFeature(
                projectId,
                rootWishlistId,
                "Matchmaking Engine",
                "Enable 1v1 chess matchmaking",
                "must-be",
                "complicated",
                "DPMO < 3.4",
                "BOTTLENECK-01"
        );

        assertThat(created.getId()).isEqualTo(mintedId);
        assertThat(created.getOriginFeatureId()).isEqualTo(mintedId);
        assertThat(created.getProjectId()).isEqualTo(projectId);
        assertThat(created.getRootWishlistId()).isEqualTo(rootWishlistId);
        assertThat(created.getKanoClass()).isEqualTo("must-be");
        assertThat(created.getCynefinDomain()).isEqualTo("complicated");
        assertThat(created.getSixSigmaMetric()).isEqualTo("DPMO < 3.4");
        assertThat(created.getTocConstraintRef()).isEqualTo("BOTTLENECK-01");
        assertThat(created.getEpistemicScore()).isGreaterThan(0.0);
        assertThat(created.getEpistemicLayer()).isNotNull();

        verify(featureRepository, times(2)).save(any(FeatureEntity.class));
    }
}
