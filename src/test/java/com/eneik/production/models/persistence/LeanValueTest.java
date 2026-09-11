package com.eneik.production.models.persistence;

import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.gate.BaseQualityGate;
import com.eneik.production.services.gate.GateResult;
import com.eneik.production.services.jules.JulesDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying truth status handling for LeanValue
 * (NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D012 Policy contradiction / Truth status confusion).
 *
 * Proof obligations:
 * 1. Unrecognized or missing model response resolves to LeanValue.undetermined, never valuable or essential.
 * 2. Quality gate and Definition of Ready reject undetermined values (muda gate).
 * 3. emsGraphSlices preserves undetermined slices in the graph instead of discarding them as waste.
 * 4. Resolution paths deterministically resolve undetermined values from epic Kano class or role context.
 */
class LeanValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("JulesDispatchService.parseLeanValue: unknown/null/garbage resolves to undetermined, not valuable")
    void parseLeanValueTruthTable() throws Exception {
        Method parseMethod = JulesDispatchService.class.getDeclaredMethod("parseLeanValue", String.class);
        parseMethod.setAccessible(true);

        assertThat(parseMethod.invoke(null, "essential")).isEqualTo(LeanValue.essential);
        assertThat(parseMethod.invoke(null, "ESSENTIAL")).isEqualTo(LeanValue.essential);
        assertThat(parseMethod.invoke(null, "valuable")).isEqualTo(LeanValue.valuable);
        assertThat(parseMethod.invoke(null, "VALUABLE")).isEqualTo(LeanValue.valuable);
        assertThat(parseMethod.invoke(null, "waste")).isEqualTo(LeanValue.waste);
        assertThat(parseMethod.invoke(null, "WASTE")).isEqualTo(LeanValue.waste);

        // Truth status invariant: unknown input must NOT resolve to affirmation (valuable or essential)
        assertThat(parseMethod.invoke(null, (String) null)).isEqualTo(LeanValue.undetermined);
        assertThat(parseMethod.invoke(null, "")).isEqualTo(LeanValue.undetermined);
        assertThat(parseMethod.invoke(null, "   ")).isEqualTo(LeanValue.undetermined);
        assertThat(parseMethod.invoke(null, "unrecognized_string")).isEqualTo(LeanValue.undetermined);
        assertThat(parseMethod.invoke(null, "maybe_useful")).isEqualTo(LeanValue.undetermined);
    }

    @Test
    @DisplayName("BaseQualityGate.BusinessValueGate: passes only essential and valuable, rejects waste and undetermined")
    void businessValueGateRejectsUndeterminedAndWaste() {
        BaseQualityGate.BusinessValueGate gate = new BaseQualityGate.BusinessValueGate();

        // Essential passes
        TaskEntity taskEssential = createTaskWithLeanValue("essential");
        GateResult resultEssential = gate.check(taskEssential);
        assertThat(resultEssential.passed()).isTrue();

        // Valuable passes
        TaskEntity taskValuable = createTaskWithLeanValue("valuable");
        GateResult resultValuable = gate.check(taskValuable);
        assertThat(resultValuable.passed()).isTrue();

        // Waste fails
        TaskEntity taskWaste = createTaskWithLeanValue("waste");
        GateResult resultWaste = gate.check(taskWaste);
        assertThat(resultWaste.passed()).isFalse();
        assertThat(resultWaste.failureReasons()).contains("Business value cannot be 'waste'");

        // Undetermined fails
        TaskEntity taskUndetermined = createTaskWithLeanValue("undetermined");
        GateResult resultUndetermined = gate.check(taskUndetermined);
        assertThat(resultUndetermined.passed()).isFalse();
        assertThat(resultUndetermined.failureReasons()).anyMatch(f -> f.contains("undetermined"));

        // Unrecognized string fails
        TaskEntity taskUnknown = createTaskWithLeanValue("gibberish");
        GateResult resultUnknown = gate.check(taskUnknown);
        assertThat(resultUnknown.passed()).isFalse();

        // Missing lean_value fails
        TaskEntity taskMissing = new TaskEntity();
        taskMissing.setPayload(objectMapper.createObjectNode());
        GateResult resultMissing = gate.check(taskMissing);
        assertThat(resultMissing.passed()).isFalse();
    }

    @Test
    @DisplayName("TechnicalLeadCompiler.validateDefinitionOfReady: rejects undetermined and waste at Step 2")
    void compilerValidateDoDRejectsUndeterminedAndWaste() {
        WishlistEntity wishlistUndetermined = new WishlistEntity();
        wishlistUndetermined.setLeanValue(LeanValue.undetermined);
        wishlistUndetermined.setTocConstraintRef("TOC-1");
        wishlistUndetermined.setJtbd("Valid JTBD");
        wishlistUndetermined.setSixSigmaMetric("Metric");
        wishlistUndetermined.setDod("DoD BARCAN-TAG-02");
        wishlistUndetermined.setAcceptanceCriteria("AC");

        List<String> errorsUndetermined = TechnicalLeadCompiler.validateDefinitionOfReady(wishlistUndetermined);
        assertThat(errorsUndetermined).anyMatch(e -> e.contains("Step 2 failed: lean_value is missing or undetermined"));

        WishlistEntity wishlistNull = new WishlistEntity();
        wishlistNull.setLeanValue(null);
        wishlistNull.setTocConstraintRef("TOC-1");
        wishlistNull.setJtbd("Valid JTBD");
        wishlistNull.setSixSigmaMetric("Metric");
        wishlistNull.setDod("DoD BARCAN-TAG-02");
        wishlistNull.setAcceptanceCriteria("AC");

        List<String> errorsNull = TechnicalLeadCompiler.validateDefinitionOfReady(wishlistNull);
        assertThat(errorsNull).anyMatch(e -> e.contains("Step 2 failed: lean_value is missing or undetermined"));

        WishlistEntity wishlistWaste = new WishlistEntity();
        wishlistWaste.setLeanValue(LeanValue.waste);
        wishlistWaste.setTocConstraintRef("TOC-1");
        wishlistWaste.setJtbd("Valid JTBD");
        wishlistWaste.setSixSigmaMetric("Metric");
        wishlistWaste.setDod("DoD BARCAN-TAG-02");
        wishlistWaste.setAcceptanceCriteria("AC");

        List<String> errorsWaste = TechnicalLeadCompiler.validateDefinitionOfReady(wishlistWaste);
        assertThat(errorsWaste).anyMatch(e -> e.contains("Step 2 failed: lean_value cannot be 'waste'"));

        WishlistEntity wishlistEssential = new WishlistEntity();
        wishlistEssential.setLeanValue(LeanValue.essential);
        wishlistEssential.setTocConstraintRef("TOC-1");
        wishlistEssential.setJtbd("Valid JTBD");
        wishlistEssential.setSixSigmaMetric("Metric");
        wishlistEssential.setDod("DoD BARCAN-TAG-02");
        wishlistEssential.setAcceptanceCriteria("AC");

        List<String> errorsEssential = TechnicalLeadCompiler.validateDefinitionOfReady(wishlistEssential);
        assertThat(errorsEssential).noneMatch(e -> e.contains("Step 2"));
    }

    @Test
    @DisplayName("ProjectFlowService.emsGraphSlices: preserves undetermined slices in graph, discards waste")
    void emsGraphSlicesPreservesUndeterminedAndDiscardsWaste() {
        WishlistEntity parent = new WishlistEntity();
        parent.setId(UUID.randomUUID());
        parent.setSource(WishlistSource.client);

        MLPredictionServiceClient.TaskSliceMetadata sliceEssential = new MLPredictionServiceClient.TaskSliceMetadata(
                "Core Database Migration", "Persist entities", "Tables created",
                "BARCAN-TAG-08", LeanValue.essential, "clear", "TOC-1", "Metric", false, List.of()
        );

        MLPredictionServiceClient.TaskSliceMetadata sliceUndetermined = new MLPredictionServiceClient.TaskSliceMetadata(
                "Telemetry Dashboard", "Display metrics", "Charts visible",
                "BARCAN-TAG-11", LeanValue.undetermined, "clear", "TOC-1", "Metric", true, List.of()
        );

        MLPredictionServiceClient.TaskSliceMetadata sliceWaste = new MLPredictionServiceClient.TaskSliceMetadata(
                "Deprecated Legacy Sync", "Sync old system", "Obsolete",
                "BARCAN-TAG-02", LeanValue.waste, "clear", "TOC-1", "Metric", false, List.of()
        );

        List<MLPredictionServiceClient.TaskSliceMetadata> inputSlices = List.of(sliceEssential, sliceUndetermined, sliceWaste);
        List<MLPredictionServiceClient.TaskSliceMetadata> graphSlices = ProjectFlowService.emsGraphSlices(parent, inputSlices);

        // Undetermined slice must NOT be discarded as waste: graph must contain essential and undetermined (size 2)
        assertThat(graphSlices).hasSize(2);
        assertThat(graphSlices).extracting(MLPredictionServiceClient.TaskSliceMetadata::title)
                .containsExactlyInAnyOrder("Core Database Migration", "Telemetry Dashboard");
        assertThat(graphSlices).extracting(MLPredictionServiceClient.TaskSliceMetadata::leanValue)
                .contains(LeanValue.undetermined);
        assertThat(graphSlices).extracting(MLPredictionServiceClient.TaskSliceMetadata::leanValue)
                .doesNotContain(LeanValue.waste);
    }

    @Test
    @DisplayName("Resolution path: resolveSliceLeanValue deterministically resolves undetermined from Kano or role")
    void resolveSliceLeanValueResolutionPath() {
        MLPredictionServiceClient.TaskSliceMetadata slice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Feature slice", "JTBD", "AC", "BARCAN-TAG-04", LeanValue.undetermined, "clear", "TOC-1", "Metric", false, List.of()
        );

        // 1. Must-Be epic resolves to essential
        LeanValue v1 = ProjectFlowService.resolveSliceLeanValue(slice, "Must-Be", "BARCAN-TAG-04");
        assertThat(v1).isEqualTo(LeanValue.essential);

        // 2. Performance epic resolves to valuable
        LeanValue v2 = ProjectFlowService.resolveSliceLeanValue(slice, "Performance", "BARCAN-TAG-04");
        assertThat(v2).isEqualTo(LeanValue.valuable);

        // 3. Reverse/Waste epic resolves to waste
        LeanValue v3 = ProjectFlowService.resolveSliceLeanValue(slice, "Reverse/Waste", "BARCAN-TAG-04");
        assertThat(v3).isEqualTo(LeanValue.waste);

        // 4. Core role (BARCAN-TAG-00 / 02 / 12) resolves to essential even without Kano class
        LeanValue v4 = ProjectFlowService.resolveSliceLeanValue(slice, null, "BARCAN-TAG-02");
        assertThat(v4).isEqualTo(LeanValue.essential);

        // 5. Unknown context remains undetermined for human triage
        LeanValue v5 = ProjectFlowService.resolveSliceLeanValue(slice, null, "BARCAN-TAG-04");
        assertThat(v5).isEqualTo(LeanValue.undetermined);
    }

    @Test
    @DisplayName("Resolution path: resolveWishlistLeanValue resolves from role and JTBD keywords")
    void resolveWishlistLeanValueResolutionPath() {
        WishlistEntity coreWishlist = new WishlistEntity();
        coreWishlist.setSourceRoleTag("BARCAN-TAG-02");
        assertThat(ProjectFlowService.resolveWishlistLeanValue(coreWishlist)).isEqualTo(LeanValue.essential);

        WishlistEntity fixWishlist = new WishlistEntity();
        fixWishlist.setSourceRoleTag("BARCAN-TAG-04");
        fixWishlist.setJtbd("Fix security vulnerability in authentication token validation");
        assertThat(ProjectFlowService.resolveWishlistLeanValue(fixWishlist)).isEqualTo(LeanValue.essential);

        WishlistEntity uiWishlist = new WishlistEntity();
        uiWishlist.setSourceRoleTag("BARCAN-TAG-11");
        uiWishlist.setJtbd("Add new feature screen for user preferences");
        assertThat(ProjectFlowService.resolveWishlistLeanValue(uiWishlist)).isEqualTo(LeanValue.valuable);

        WishlistEntity unknownWishlist = new WishlistEntity();
        unknownWishlist.setSourceRoleTag("BARCAN-TAG-04");
        unknownWishlist.setJtbd("Evaluate generic request");
        assertThat(ProjectFlowService.resolveWishlistLeanValue(unknownWishlist)).isEqualTo(LeanValue.undetermined);
    }

    private TaskEntity createTaskWithLeanValue(String leanValue) {
        TaskEntity task = new TaskEntity();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("lean_value", leanValue);
        task.setPayload(payload);
        return task;
    }
}
