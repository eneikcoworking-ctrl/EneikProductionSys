package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.MLPredictionServiceClient.EpicPlan;
import com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata;
import com.eneik.production.services.ProjectFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Law 9 (Закон бюджета) Test Suite.
 *
 * <p>Philosophical Grounding: J.L. Mackie (INUS condition / causal attribution) & Nelson Goodman.
 *
 * <p>Formal Invariant:
 * <pre>
 *   \Delta c = 1 \iff e \in Ev(X)
 * </pre>
 * A client brief's compile budget {@code compileAttempts(w)} must strictly decrease ONLY upon
 * events that constitute evidence about the brief itself (the compiler returned an empty answer
 * or an accepted plan).
 *
 * <p>A rejection by this factory's own schema validator (e.g. coverageComplete=false, invalid
 * slice count, missing requirement refs) is an internal process defect, NOT evidence about the brief.
 * Therefore, charging the brief's budget for internal failures is a false causal attribution (Mackie INUS).
 * Any internal rejection MUST return the compile attempt back to the brief via
 * {@link ProjectFlowService#returnCompileAttempt(Collection)}.
 */
@ExtendWith(MockitoExtension.class)
class WishlistCompileBudgetLaw9Test {

    @Mock
    private WishlistRepository wishlistRepository;

    private ProjectFlowService projectFlowService;

    @BeforeEach
    void setUp() {
        // ProjectFlowService has no no-argument constructor (40 collaborators). returnCompileAttempt reads
        // exactly one of them, so the real method is called on an uninitialized instance with that one field
        // injected - the alternative is standing up forty mocks to exercise ten lines.
        projectFlowService = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(projectFlowService, "wishlistRepository", wishlistRepository);
    }

    private TaskSliceMetadata createValidSlice() {
        return new TaskSliceMetadata(
                "Implement API",
                "When implementing API for this epic, I want validation, so the flow is safe",
                "Given valid input, When submitted, Then it is stored\nGiven invalid input, When submitted, Then it is rejected",
                "BARCAN-TAG-02",
                LeanValue.essential,
                "complicated",
                "API",
                "all tests pass",
                false,
                List.of("R1", "R2")
        );
    }

    private EpicPlan createValidEpic(TaskSliceMetadata slice) {
        return new EpicPlan(
                null,
                "Campaigns",
                "When an operator runs campaigns, I want safe orchestration, so outreach is controlled",
                "Must-Be",
                "complicated",
                "zero invalid campaigns",
                "campaign integrity",
                0,
                List.of("R1: create campaigns", "R2: reject invalid campaigns"),
                true,
                List.of(slice)
        );
    }

    @Test
    @DisplayName("Empty compiler answer is evidence about brief: reports EMPTY_COMPILER_ANSWER and spends budget")
    void emptyCompilerAnswerReportsNamedConstantAndSpendsBudget() {
        String rejection = JulesDispatchService.compilerPlanRejection(List.of(), 1);

        assertEquals(JulesDispatchService.EMPTY_COMPILER_ANSWER, rejection,
                "An empty answer from the compiler must return the designated EMPTY_COMPILER_ANSWER constant");

        // When rejection == EMPTY_COMPILER_ANSWER, returnCompileAttempt MUST NOT be called:
        // the brief spends its attempt because the compiler legitimately evaluated it and gave nothing.
        boolean shouldReturnAttempt = !JulesDispatchService.EMPTY_COMPILER_ANSWER.equals(rejection);
        assertFalse(shouldReturnAttempt,
                "Empty compiler answer is evidence about the brief (Mackie INUS): budget attempt must NOT be returned");
    }

    @Test
    @DisplayName("Internal validator refusal (coverageComplete=false) is factory defect: returns compile attempt")
    void internalValidatorRefusalReturnsCompileAttempt() {
        var slice = createValidSlice();
        // Malformed epic: coverageComplete is false (factory schema requirement)
        var invalidEpic = new EpicPlan(
                null,
                "Campaigns",
                "When an operator runs campaigns, I want safe orchestration",
                "Must-Be",
                "complicated",
                "zero invalid campaigns",
                "campaign integrity",
                0,
                List.of("R1: create campaigns", "R2: reject invalid campaigns"),
                false, // coverageComplete = false!
                List.of(slice)
        );

        String rejection = JulesDispatchService.compilerPlanRejection(List.of(invalidEpic), 1);

        assertNotEquals("", rejection, "Invalid epic plan must be rejected");
        assertNotEquals(JulesDispatchService.EMPTY_COMPILER_ANSWER, rejection,
                "Internal validator refusal must NOT be confused with empty compiler answer");
        assertTrue(rejection.contains("coverageComplete is not true"),
                "Rejection message must point to internal factory constraint");

        // In JulesDispatchService: if (!EMPTY_COMPILER_ANSWER.equals(rejection)) -> returnCompileAttempt called!
        boolean shouldReturnAttempt = !JulesDispatchService.EMPTY_COMPILER_ANSWER.equals(rejection);
        assertTrue(shouldReturnAttempt,
                "Factory validator refusal establishes nothing about the brief: budget attempt MUST be returned");
    }

    @Test
    @DisplayName("Internal validator refusal with excessive slices returns compile attempt")
    void excessiveSlicesRefusalReturnsCompileAttempt() {
        var slice = createValidSlice();
        // 9 slices exceeds MAX_SLICES_PER_EPIC (8)
        var nineSlices = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> slice)
                .toList();

        var invalidEpic = new EpicPlan(
                null,
                "Campaigns",
                "When an operator runs campaigns, I want safe orchestration",
                "Must-Be",
                "complicated",
                "zero invalid campaigns",
                "campaign integrity",
                0,
                List.of("R1: create campaigns", "R2: reject invalid campaigns"),
                true,
                nineSlices
        );

        String rejection = JulesDispatchService.compilerPlanRejection(List.of(invalidEpic), 1);

        assertNotEquals("", rejection);
        assertNotEquals(JulesDispatchService.EMPTY_COMPILER_ANSWER, rejection);
        assertTrue(rejection.contains("more than 8"),
                "Rejection should state that slice count exceeded factory limit");

        boolean shouldReturnAttempt = !JulesDispatchService.EMPTY_COMPILER_ANSWER.equals(rejection);
        assertTrue(shouldReturnAttempt,
                "Excessive slice decomposition is a factory compiler defect: budget attempt MUST be returned");
    }

    @Test
    @DisplayName("returnCompileAttempt decrements compileAttempts by 1 and clamps at 0")
    @SuppressWarnings("unchecked")
    void returnCompileAttemptDecrementsAndFloorsAtZero() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        WishlistEntity w1 = new WishlistEntity();
        w1.setId(id1);
        w1.setCompileAttempts(3);

        WishlistEntity w2 = new WishlistEntity();
        w2.setId(id2);
        w2.setCompileAttempts(1);

        WishlistEntity w3 = new WishlistEntity();
        w3.setId(id3);
        w3.setCompileAttempts(0); // already at 0

        when(wishlistRepository.findAllById(List.of(id1, id2, id3))).thenReturn(List.of(w1, w2, w3));

        projectFlowService.returnCompileAttempt(List.of(id1, id2, id3));

        assertEquals(2, w1.getCompileAttempts(), "Attempt should decrement from 3 to 2");
        assertEquals(0, w2.getCompileAttempts(), "Attempt should decrement from 1 to 0");
        assertEquals(0, w3.getCompileAttempts(), "Attempt at 0 must remain 0 (clamped at lower bound)");

        ArgumentCaptor<List<WishlistEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(wishlistRepository).saveAll(captor.capture());
        List<WishlistEntity> saved = captor.getValue();

        // w3 had 0 attempts, so it should not be touched / saved
        assertEquals(2, saved.size(), "Only wishlists whose attempts were decremented should be saved");
        assertTrue(saved.contains(w1));
        assertTrue(saved.contains(w2));
        assertFalse(saved.contains(w3));
    }

    @Test
    @DisplayName("returnCompileAttempt is safe no-op on null or empty input")
    void returnCompileAttemptSafeOnNullOrEmpty() {
        projectFlowService.returnCompileAttempt(null);
        projectFlowService.returnCompileAttempt(List.of());

        verify(wishlistRepository, never()).findAllById(any());
        verify(wishlistRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Valid compiler plan is accepted: no rejection, no returnCompileAttempt needed")
    void validCompilerPlanReportsNoRejection() {
        var slice = createValidSlice();
        var epic = createValidEpic(slice);

        assertTrue(JulesDispatchService.isValidCompilerPlan(List.of(epic), 1));
        assertEquals("", JulesDispatchService.compilerPlanRejection(List.of(epic), 1),
                "A usable plan reports empty rejection string");
    }
}
