package com.eneik.production.services;

import java.util.Arrays;
import java.util.List;

/**
 * Single source of truth for which decomposition stage each BARCAN-TAG role belongs to, replacing
 * three previously independent switch statements (ProjectFlowService.emsStageOrder,
 * TechnicalLeadCompiler.flowStage, EmsMetricsService.flowStage) that had drifted apart on whether
 * the data-model role (TAG-08) and the API-contract role (TAG-12) precede or share a stage with
 * backend/frontend implementation.
 *
 * graphOrder drives dependency-graph scheduling (ProjectFlowService.buildTaskGraphFromSlices): two
 * stages sharing the same graphOrder run in parallel, never depending on each other. label is purely
 * descriptive, used for dashboards/metrics - two roles can share a graphOrder while keeping distinct
 * labels (backend "implementation" vs frontend "experience" stay visually distinct even though they
 * now run in parallel off the same predecessor stage).
 */
public enum EmsFlowStage {
    DECISION(10, "decision", true, "BARCAN-TAG-09"),
    ARCHITECTURE(20, "architecture", true, "BARCAN-TAG-01"),
    DATA_MODEL(25, "data-model", false, "BARCAN-TAG-08"),
    API_CONTRACT(27, "api-contract", true, "BARCAN-TAG-12"),
    IMPLEMENTATION(30, "implementation", false, "BARCAN-TAG-02", "BARCAN-TAG-04", "BARCAN-TAG-07"),
    EXPERIENCE(30, "experience", false, "BARCAN-TAG-03", "BARCAN-TAG-11"),
    OPERATIONS(50, "operations", false, "BARCAN-TAG-05"),
    COMPLIANCE(55, "compliance", true, "BARCAN-TAG-10"),
    VERIFICATION(60, "verification", false, "BARCAN-TAG-06"),
    INTEGRATION(70, "integration", false, "BARCAN-TAG-00");

    private static final int DEFAULT_GRAPH_ORDER = 35;
    private static final String DEFAULT_LABEL = "implementation";

    private final int graphOrder;
    private final String label;
    private final boolean specOnly;
    private final List<String> roleTags;

    EmsFlowStage(int graphOrder, String label, boolean specOnly, String... roleTags) {
        this.graphOrder = graphOrder;
        this.label = label;
        this.specOnly = specOnly;
        this.roleTags = List.of(roleTags);
    }

    public int graphOrder() {
        return graphOrder;
    }

    public String label() {
        return label;
    }

    public static EmsFlowStage forRoleTag(String roleTag) {
        return Arrays.stream(values())
                .filter(stage -> stage.roleTags.contains(roleTag))
                .findFirst()
                .orElse(null);
    }

    public static int graphOrderForRoleTag(String roleTag) {
        EmsFlowStage stage = forRoleTag(roleTag);
        return stage != null ? stage.graphOrder : DEFAULT_GRAPH_ORDER;
    }

    public static String labelForRoleTag(String roleTag) {
        EmsFlowStage stage = forRoleTag(roleTag);
        return stage != null ? stage.label : DEFAULT_LABEL;
    }

    /**
     * True for stages whose deliverable is a reference document/specification/decision record - a
     * dependent only needs to READ the finished artifact, not build against verified working code - so a
     * dependent may safely start as soon as its PR is open rather than waiting for merge (see
     * ClientDeliverableReadinessService#isSpecDependencyPrOpenButUnmerged). DATA_MODEL, IMPLEMENTATION,
     * EXPERIENCE, OPERATIONS, VERIFICATION and INTEGRATION deliberately stay excluded - each produces
     * schema/code/config/test-results a dependent needs in its FINAL form (see the prior-incident comment
     * on isSpecDependencyPrOpenButUnmerged for the specific DATA_MODEL/API_CONTRACT/IMPLEMENTATION
     * parallel-guessing incident this stays scoped away from). Unknown role tags (forRoleTag returns null)
     * are never spec-only, matching the fail-closed default of the other *ForRoleTag helpers.
     */
    /**
     * What kind of artifact constitutes delivery for a role at this stage.
     *
     * 2026-08-20: delivery used to be decided in two places that disagreed. This enum declared
     * EXPERIENCE (TAG-03, TAG-11) as {@code specOnly=false} - "produces code" - while
     * ClientDeliverableReadinessService kept a private set exempting TAG-03, and the private set won
     * because it was consulted first. The cause is that {@code specOnly} is a criterion standing in for a
     * different question: "does this role produce a specification" is not "what artifact constitutes this
     * role's delivery" (ACP-102 - a criterion agrees with its concept only over the bearers it was
     * calibrated on). TAG-03's artifacts land in design/draft and design/approved, which
     * CodeChangeClassifier already classifies as non-code: it delivers DESIGN, and neither value of
     * {@code specOnly} can say so.
     *
     * Adding a content-authoring role later is now one value here, not an edit in two files.
     */
    public enum DeliveryArtifact {
        /** Source that changes the product's behaviour. The merged PR must contain code. */
        CODE,
        /** A written decision, contract or compliance note. No code is expected. */
        SPEC,
        /** Mockups and approved design assets under design/. No code is expected. */
        DESIGN
    }

    /**
     * The single predicate for whether a role's delivery requires code. Every caller asks this and nothing
     * keeps a second list - Charter invariant 10, one point of application.
     */
    public static boolean requiresCodeForDelivery(String roleTag) {
        return deliveryArtifact(roleTag) == DeliveryArtifact.CODE;
    }

    public static DeliveryArtifact deliveryArtifact(String roleTag) {
        if ("BARCAN-TAG-03".equals(roleTag)) {
            return DeliveryArtifact.DESIGN;
        }
        return isSpecStage(roleTag) ? DeliveryArtifact.SPEC : DeliveryArtifact.CODE;
    }

    public static boolean isSpecStage(String roleTag) {
        EmsFlowStage stage = forRoleTag(roleTag);
        return stage != null && stage.specOnly;
    }
}
