package com.eneik.production.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification test suite for {@link EmsFlowStage} grounding Stage 4 invariants:
 *
 * <p>1. Wittgenstein (D001, {@code LYUDVIG_VITGENSHTEYN_05_ANCHOR_BOUND_NAME}):
 * Single source of truth for stage mapping, labels and graph topology. Replaces three previously
 * drifted switch statements (ProjectFlowService, TechnicalLeadCompiler, EmsMetricsService).
 * All 13 canonical BARCAN-TAG roles (00-12) anchor to their designated stages, and unknown roles
 * fail closed to safe defaults without causing unhandled exceptions.
 *
 * <p>2. Frege (D007, {@code GOTLOB_FREGE_02_CONSTRUCTIVE_PROOF_OBJECT}):
 * Constructive proof object: typed delivery artifact {@link EmsFlowStage.DeliveryArtifact}
 * (CODE, SPEC, DESIGN). Replaces the inadequate binary {@code specOnly} proxy (ACP-102:
 * a criterion agrees with its concept only over the bearers it was calibrated on).
 * {@code requiresCodeForDelivery} is the single authoritative predicate, recognizing DESIGN
 * for BARCAN-TAG-03, SPEC for document/decision stages, and CODE for actual implementation.
 *
 * <p>3. Williamson (D013, {@code TIMOTI_UILYAMSON_17_CAUSAL_PROCESS_TRACE}):
 * Causal process trace for decomposition dependency scheduling. The execution topology guarantees
 * that data models precede API contracts, which in turn precede parallel backend/frontend code:
 * {@code DATA_MODEL (25) < API_CONTRACT (27) < IMPLEMENTATION / EXPERIENCE (30)}.
 */
class EmsFlowStageFalsificationTest {

    // =========================================================================
    // 1. Wittgenstein (D001): LYUDVIG_VITGENSHTEYN_05_ANCHOR_BOUND_NAME
    // =========================================================================

    @Test
    @DisplayName("Wittgenstein [D001]: all 13 BARCAN-TAG roles map deterministically to designated stages")
    void falsifyWittgenstein_anchorBoundName_allThirteenRolesAnchorToSingleSourceOfTruth() {
        // Governance & Architecture
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-09")).isEqualTo(EmsFlowStage.DECISION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-01")).isEqualTo(EmsFlowStage.ARCHITECTURE);

        // Data & Contract Precursors
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-08")).isEqualTo(EmsFlowStage.DATA_MODEL);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-12")).isEqualTo(EmsFlowStage.API_CONTRACT);

        // Core Implementation (Backend & Frontend)
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-02")).isEqualTo(EmsFlowStage.IMPLEMENTATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-04")).isEqualTo(EmsFlowStage.IMPLEMENTATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-07")).isEqualTo(EmsFlowStage.IMPLEMENTATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-03")).isEqualTo(EmsFlowStage.EXPERIENCE);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-11")).isEqualTo(EmsFlowStage.EXPERIENCE);

        // Operations, Compliance, Verification, Integration
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-05")).isEqualTo(EmsFlowStage.OPERATIONS);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-10")).isEqualTo(EmsFlowStage.COMPLIANCE);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-06")).isEqualTo(EmsFlowStage.VERIFICATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-00")).isEqualTo(EmsFlowStage.INTEGRATION);
    }

    @Test
    @DisplayName("Wittgenstein [D001]: unknown or missing role tags fail-closed to safe default topology")
    void falsifyWittgenstein_anchorBoundName_unknownRolesFailClosedSafely() {
        // Unknown role returns null stage
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-99")).isNull();
        assertThat(EmsFlowStage.forRoleTag("UNKNOWN-ROLE")).isNull();
        assertThat(EmsFlowStage.forRoleTag("")).isNull();

        // Default graph order is 35 (sits immediately after implementation/experience 30)
        assertThat(EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-99")).isEqualTo(35);
        assertThat(EmsFlowStage.graphOrderForRoleTag("")).isEqualTo(35);

        // Default label is "implementation"
        assertThat(EmsFlowStage.labelForRoleTag("BARCAN-TAG-99")).isEqualTo("implementation");
        assertThat(EmsFlowStage.labelForRoleTag("")).isEqualTo("implementation");
    }

    // =========================================================================
    // 2. Frege (D007): GOTLOB_FREGE_02_CONSTRUCTIVE_PROOF_OBJECT
    // =========================================================================

    @Test
    @DisplayName("Frege [D007]: constructive proof object distinguishes CODE, SPEC, and DESIGN artifacts")
    void falsifyFrege_constructiveProofObject_distinguishesTypedDeliveryArtifacts() {
        // DESIGN delivery: BARCAN-TAG-03 (assets under design/draft and design/approved)
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-03"))
                .as("BARCAN-TAG-03 delivers DESIGN assets, not code or generic spec")
                .isEqualTo(EmsFlowStage.DeliveryArtifact.DESIGN);

        // SPEC delivery: Decision, Architecture, Contract, Compliance
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-09")).isEqualTo(EmsFlowStage.DeliveryArtifact.SPEC);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-01")).isEqualTo(EmsFlowStage.DeliveryArtifact.SPEC);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-12")).isEqualTo(EmsFlowStage.DeliveryArtifact.SPEC);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-10")).isEqualTo(EmsFlowStage.DeliveryArtifact.SPEC);

        // CODE delivery: Data model, Implementation, Experience code, Operations, Verification, Integration
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-08")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-02")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-04")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-07")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-11")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-05")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-06")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
        assertThat(EmsFlowStage.deliveryArtifact("BARCAN-TAG-00")).isEqualTo(EmsFlowStage.DeliveryArtifact.CODE);
    }

    @Test
    @DisplayName("Frege [D007]: requiresCodeForDelivery accurately gates delivery and fails closed on unknown roles")
    void falsifyFrege_constructiveProofObject_requiresCodePredicateTruthTable() {
        // Design role does not owe code
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-03")).isFalse();

        // Spec roles do not owe code
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-09")).isFalse();
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-01")).isFalse();
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-12")).isFalse();
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-10")).isFalse();

        // Code roles strictly owe code
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-08")).isTrue();
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-02")).isTrue();
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-11")).isTrue();
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-00")).isTrue();

        // Unknown role fails closed to requiring code (safe default)
        assertThat(EmsFlowStage.requiresCodeForDelivery("BARCAN-TAG-99"))
                .as("Unknown role must fail-closed to requiring code for delivery")
                .isTrue();
        assertThat(EmsFlowStage.requiresCodeForDelivery(""))
                .as("Empty role tag must fail-closed to requiring code for delivery")
                .isTrue();
        assertThat(EmsFlowStage.requiresCodeForDelivery("UNKNOWN-TAG"))
                .as("Unknown tag must fail-closed to requiring code for delivery")
                .isTrue();
    }

    // =========================================================================
    // 3. Williamson (D013): TIMOTI_UILYAMSON_17_CAUSAL_PROCESS_TRACE
    // =========================================================================

    @Test
    @DisplayName("Williamson [D013]: causal ordering enforces data-model -> contract -> implementation")
    void falsifyWilliamson_causalProcessTrace_strictPrecursorOrdering() {
        int decisionOrder = EmsFlowStage.DECISION.graphOrder();
        int archOrder = EmsFlowStage.ARCHITECTURE.graphOrder();
        int dataModelOrder = EmsFlowStage.DATA_MODEL.graphOrder();
        int apiContractOrder = EmsFlowStage.API_CONTRACT.graphOrder();
        int implOrder = EmsFlowStage.IMPLEMENTATION.graphOrder();
        int expOrder = EmsFlowStage.EXPERIENCE.graphOrder();
        int opsOrder = EmsFlowStage.OPERATIONS.graphOrder();
        int complianceOrder = EmsFlowStage.COMPLIANCE.graphOrder();
        int verificationOrder = EmsFlowStage.VERIFICATION.graphOrder();
        int integrationOrder = EmsFlowStage.INTEGRATION.graphOrder();

        // Trace the entire causal chain
        assertThat(decisionOrder).isLessThan(archOrder);
        assertThat(archOrder).isLessThan(dataModelOrder);
        assertThat(dataModelOrder).isLessThan(apiContractOrder);
        assertThat(apiContractOrder).isLessThan(implOrder);
        assertThat(implOrder).isEqualTo(expOrder);
        assertThat(implOrder).isLessThan(opsOrder);
        assertThat(opsOrder).isLessThan(complianceOrder);
        assertThat(complianceOrder).isLessThan(verificationOrder);
        assertThat(verificationOrder).isLessThan(integrationOrder);
    }

    @Test
    @DisplayName("Williamson [D013]: parallel stages share identical graphOrder while retaining distinct identities")
    void falsifyWilliamson_causalProcessTrace_parallelStagesHaveEqualGraphOrderAndDistinctLabels() {
        // Backend implementation and Frontend experience run in parallel off same predecessors
        assertThat(EmsFlowStage.IMPLEMENTATION.graphOrder())
                .as("Implementation and Experience must share graphOrder to enable parallel execution")
                .isEqualTo(EmsFlowStage.EXPERIENCE.graphOrder());

        assertThat(EmsFlowStage.IMPLEMENTATION.label())
                .as("Implementation and Experience must retain distinct labels for metrics")
                .isNotEqualTo(EmsFlowStage.EXPERIENCE.label());

        assertThat(EmsFlowStage.IMPLEMENTATION.label()).isEqualTo("implementation");
        assertThat(EmsFlowStage.EXPERIENCE.label()).isEqualTo("experience");
    }
}
