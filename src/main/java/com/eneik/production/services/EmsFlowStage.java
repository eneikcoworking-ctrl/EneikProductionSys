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
    DECISION(10, "decision", "BARCAN-TAG-09"),
    ARCHITECTURE(20, "architecture", "BARCAN-TAG-01"),
    DATA_MODEL(25, "data-model", "BARCAN-TAG-08"),
    API_CONTRACT(27, "api-contract", "BARCAN-TAG-12"),
    IMPLEMENTATION(30, "implementation", "BARCAN-TAG-02", "BARCAN-TAG-04", "BARCAN-TAG-07"),
    EXPERIENCE(30, "experience", "BARCAN-TAG-03", "BARCAN-TAG-11"),
    OPERATIONS(50, "operations", "BARCAN-TAG-05"),
    COMPLIANCE(55, "compliance", "BARCAN-TAG-10"),
    VERIFICATION(60, "verification", "BARCAN-TAG-06"),
    INTEGRATION(70, "integration", "BARCAN-TAG-00");

    private static final int DEFAULT_GRAPH_ORDER = 35;
    private static final String DEFAULT_LABEL = "implementation";

    private final int graphOrder;
    private final String label;
    private final List<String> roleTags;

    EmsFlowStage(int graphOrder, String label, String... roleTags) {
        this.graphOrder = graphOrder;
        this.label = label;
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
}
