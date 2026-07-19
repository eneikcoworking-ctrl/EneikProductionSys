package com.eneik.production.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmsFlowStageTest {

    @Test
    void everyRoleResolvesToItsExpectedStage() {
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-09")).isEqualTo(EmsFlowStage.DECISION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-01")).isEqualTo(EmsFlowStage.ARCHITECTURE);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-08")).isEqualTo(EmsFlowStage.DATA_MODEL);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-12")).isEqualTo(EmsFlowStage.API_CONTRACT);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-02")).isEqualTo(EmsFlowStage.IMPLEMENTATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-04")).isEqualTo(EmsFlowStage.IMPLEMENTATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-07")).isEqualTo(EmsFlowStage.IMPLEMENTATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-03")).isEqualTo(EmsFlowStage.EXPERIENCE);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-11")).isEqualTo(EmsFlowStage.EXPERIENCE);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-05")).isEqualTo(EmsFlowStage.OPERATIONS);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-10")).isEqualTo(EmsFlowStage.COMPLIANCE);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-06")).isEqualTo(EmsFlowStage.VERIFICATION);
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-00")).isEqualTo(EmsFlowStage.INTEGRATION);
    }

    @Test
    void dataModelPrecedesContractPrecedesParallelImplementation() {
        int dataModelOrder = EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-08");
        int contractOrder = EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-12");
        int backendOrder = EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-02");
        int frontendOrder = EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-11");

        assertThat(dataModelOrder).isLessThan(contractOrder);
        assertThat(contractOrder).isLessThan(backendOrder);
        assertThat(backendOrder).isEqualTo(frontendOrder);
    }

    @Test
    void backendAndFrontendShareGraphOrderButNotLabel() {
        assertThat(EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-02"))
                .isEqualTo(EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-11"));
        assertThat(EmsFlowStage.labelForRoleTag("BARCAN-TAG-02")).isEqualTo("implementation");
        assertThat(EmsFlowStage.labelForRoleTag("BARCAN-TAG-11")).isEqualTo("experience");
    }

    @Test
    void unknownRoleTagFallsBackToDefaults() {
        assertThat(EmsFlowStage.forRoleTag("BARCAN-TAG-99")).isNull();
        assertThat(EmsFlowStage.graphOrderForRoleTag("BARCAN-TAG-99")).isEqualTo(35);
        assertThat(EmsFlowStage.labelForRoleTag("BARCAN-TAG-99")).isEqualTo("implementation");
    }
}
