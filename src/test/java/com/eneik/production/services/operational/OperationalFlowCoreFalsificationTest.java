package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.FlowSpineEventEntity;
import com.eneik.production.repositories.FlowSpineEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Falsification test-screen for OperationalFlowCoreService.
 *
 * Epistemic & Deontic Grounding:
 * - BARCAN-TAG-10_DEONTIC-PROHIBITION:03:dzhozef-raz / DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX [D006, Raz]:
 *   Rights, duties, privileges, and powers matrix across project lifecycle, terminality, hard-stop states,
 *   and capacity-consuming operations (dispatch, merge, mutation).
 * - BARCAN-TAG-10_DEONTIC-PROHIBITION:03:dzhozef-raz / DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]:
 *   Every state in Flow Core maps to explicit, non-empty, actionable forbidden actions and explainable
 *   denial reasons (ENFORCED_STOP_THE_LINE, ENFORCED_PROJECT_NOT_MUTABLE).
 */
class OperationalFlowCoreFalsificationTest {

    private static final UUID TEST_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private FlowSpineDto buildSnapshot(String projectStatus,
                                       String state,
                                       String valueStatus,
                                       String blockingReason,
                                       int queuedTasks,
                                       int reviewTasks,
                                       int openReviews) {
        FlowSpineDto.Transition transition = new FlowSpineDto.Transition(
                state,
                "NEXT_STATE",
                "OwnerService",
                "Execute transition action",
                List.of("evidence_present"),
                "Transition rationale"
        );
        return new FlowSpineDto(
                Instant.EPOCH,
                "observe_only",
                new FlowSpineDto.ProjectRef(TEST_PROJECT_ID, "test-product", projectStatus, "repo"),
                state,
                valueStatus,
                blockingReason,
                transition,
                List.of(transition),
                List.of(),
                List.of(),
                List.of(),
                new FlowSpineDto.EvidenceVector(0, openReviews, 0, 0, 0, 0, 0, 0, "ok", false),
                new FlowSpineDto.FlowCounts(queuedTasks, reviewTasks, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, "unknown", false, true, 0, 0, 0, 0, 0.0),
                List.of(new FlowSpineDto.FlowInvariant("single_current_state", "pass", "Single state invariant", "verified")),
                new FlowSpineDto.JournalSummary(null, null, null, null, "evidence_hash_12345", false, 0),
                "deterministic_precedence"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"paused", "maintenance", "archived", "disabled"})
    @DisplayName("DZHOZEF_RAZ_02: Non-active project lifecycle forbids mutation, dispatch, and merge")
    void rightsDutiesMatrixNonActiveProjectEnforcesProjectNotMutable(String inactiveStatus) {
        FlowSpineDto snapshot = buildSnapshot(inactiveStatus, "QUEUED", "value_in_progress", "", 5, 0, 0);
        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);

        FlowCoreDto.Authorization authorization = OperationalFlowCoreService.authorization(snapshot, decision);

        assertThat(authorization.status()).isEqualTo("ENFORCED_PROJECT_NOT_MUTABLE");
        assertThat(authorization.journalAppendAllowed()).isTrue();
        assertThat(authorization.projectMutationAllowed()).isFalse();
        assertThat(authorization.agentDispatchAllowed()).isFalse();
        assertThat(authorization.mergeAllowed()).isFalse();
        assertThat(authorization.reason()).contains("Project lifecycle state " + inactiveStatus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "ARCHIVED", "FROZEN", "PROJECT_NOT_ACTIVE"})
    @DisplayName("DZHOZEF_RAZ_02: Terminal flow states enforce ENFORCED_PROJECT_NOT_MUTABLE even on active project")
    void rightsDutiesMatrixTerminalStatesEnforceProjectNotMutable(String terminalState) {
        FlowSpineDto snapshot = buildSnapshot("active", terminalState, "client_value_delivered", "", 0, 0, 0);
        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);

        FlowCoreDto.Authorization authorization = OperationalFlowCoreService.authorization(snapshot, decision);

        assertThat(authorization.status()).isEqualTo("ENFORCED_PROJECT_NOT_MUTABLE");
        assertThat(authorization.journalAppendAllowed()).isTrue();
        assertThat(authorization.projectMutationAllowed()).isFalse();
        assertThat(authorization.agentDispatchAllowed()).isFalse();
        assertThat(authorization.mergeAllowed()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BLOCKED_BY_DUPLICATE_CONTENT",
            "GITHUB_RATE_LIMITED",
            "SYSTEM_STALLED",
            "BLOCKED_BY_TASK",
            "BLOCKED_BY_REVIEW",
            "BLOCKED_BY_MAIN_CI",
            "BLOCKED_BY_FAILED_FRONTIER"
    })
    @DisplayName("DZHOZEF_RAZ_01 / DZHOZEF_RAZ_02: Hard-stop states enforce ENFORCED_STOP_THE_LINE unconditionally")
    void rightsDutiesMatrixHardStopStatesEnforceStopTheLine(String hardStopState) {
        String blockingReason = "Critical bottleneck in state " + hardStopState;
        FlowSpineDto snapshot = buildSnapshot("active", hardStopState, "value_blocked", blockingReason, 3, 2, 1);
        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);

        FlowCoreDto.Authorization authorization = OperationalFlowCoreService.authorization(snapshot, decision);

        assertThat(authorization.status()).isEqualTo("ENFORCED_STOP_THE_LINE");
        assertThat(authorization.journalAppendAllowed()).isTrue();
        assertThat(authorization.projectMutationAllowed()).isFalse();
        assertThat(authorization.agentDispatchAllowed()).isFalse();
        assertThat(authorization.mergeAllowed()).isFalse();
        assertThat(authorization.reason()).isEqualTo(blockingReason);
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_02: Selective privileges granted under ENFORCED_ACTIONS_AVAILABLE based on task counts")
    void rightsDutiesMatrixSelectivePrivilegesUnderActionsAvailable() {
        // Case A: Queued tasks present -> agentDispatchAllowed = true, mergeAllowed = false
        FlowSpineDto queuedSnapshot = buildSnapshot("active", "QUEUED", "value_in_progress", "", 1, 0, 0);
        FlowCoreDto.Decision queuedDecision = OperationalFlowCoreService.decide(queuedSnapshot);
        FlowCoreDto.Authorization queuedAuth = OperationalFlowCoreService.authorization(queuedSnapshot, queuedDecision);

        assertThat(queuedAuth.status()).isEqualTo("ENFORCED_ACTIONS_AVAILABLE");
        assertThat(queuedAuth.projectMutationAllowed()).isTrue();
        assertThat(queuedAuth.agentDispatchAllowed()).isTrue();
        assertThat(queuedAuth.mergeAllowed()).isFalse();

        // Case B: Under review with open reviews -> agentDispatchAllowed = true, mergeAllowed = true
        FlowSpineDto reviewSnapshot = buildSnapshot("active", "UNDER_REVIEW", "value_in_progress", "", 0, 0, 2);
        FlowCoreDto.Decision reviewDecision = OperationalFlowCoreService.decide(reviewSnapshot);
        FlowCoreDto.Authorization reviewAuth = OperationalFlowCoreService.authorization(reviewSnapshot, reviewDecision);

        assertThat(reviewAuth.status()).isEqualTo("ENFORCED_ACTIONS_AVAILABLE");
        assertThat(reviewAuth.projectMutationAllowed()).isTrue();
        assertThat(reviewAuth.agentDispatchAllowed()).isTrue();
        assertThat(reviewAuth.mergeAllowed()).isTrue();

        // Case C: Idle with no actionable work -> projectMutationAllowed = true, but dispatch and merge denied
        FlowSpineDto idleSnapshot = buildSnapshot("active", "IDLE_NO_ACTIONABLE_WORK", "client_value_delivered", "", 0, 0, 0);
        FlowCoreDto.Decision idleDecision = OperationalFlowCoreService.decide(idleSnapshot);
        FlowCoreDto.Authorization idleAuth = OperationalFlowCoreService.authorization(idleSnapshot, idleDecision);

        assertThat(idleAuth.status()).isEqualTo("ENFORCED_ACTIONS_AVAILABLE");
        assertThat(idleAuth.projectMutationAllowed()).isTrue();
        assertThat(idleAuth.agentDispatchAllowed()).isFalse();
        assertThat(idleAuth.mergeAllowed()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FROZEN",
            "PROJECT_NOT_ACTIVE",
            "BLOCKED_BY_DUPLICATE_CONTENT",
            "GITHUB_RATE_LIMITED",
            "SYSTEM_STALLED",
            "BLOCKED_BY_TASK",
            "BLOCKED_BY_REVIEW",
            "BLOCKED_BY_MAIN_CI",
            "QUEUED",
            "IMPLEMENTING",
            "UNDER_REVIEW",
            "DELIVERED",
            "DECOMPOSING",
            "BLOCKED_BY_FAILED_FRONTIER",
            "VERIFYING_DELIVERY",
            "NO_SCOPE",
            "IDLE_NO_ACTIONABLE_WORK",
            "ACCEPTED",
            "ARCHIVED"
    })
    @DisplayName("DZHOZEF_RAZ_01: Every known flow state defines non-empty forbiddenActions and advisory actionKey")
    void prohibitionAsCodeAllKnownStatesContainExecutableForbiddenActions(String state) {
        FlowSpineDto snapshot = buildSnapshot("active", state, "value_in_progress", "", 1, 0, 0);

        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);

        assertThat(decision.actionKey()).as("Action key must start with advisory.").startsWith("advisory.");
        assertThat(decision.preconditions()).as("Preconditions must not be empty").isNotEmpty();
        assertThat(decision.expectedOutcomes()).as("Expected outcomes must not be empty").isNotEmpty();
        assertThat(decision.forbiddenActions()).as("Forbidden actions must not be empty for state " + state).isNotEmpty();

        for (String forbiddenAction : decision.forbiddenActions()) {
            assertThat(forbiddenAction).as("Forbidden action must be non-blank").isNotBlank();
        }
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_01: Decision status exhaustively separates terminal, acceptance, and active decisions")
    void decisionStatusExhaustiveDeonticClassification() {
        // Terminal states -> NO_OPERATION_TERMINAL
        FlowSpineDto accepted = buildSnapshot("active", "ACCEPTED", "delivered", "", 0, 0, 0);
        assertThat(OperationalFlowCoreService.decide(accepted).status()).isEqualTo("NO_OPERATION_TERMINAL");

        FlowSpineDto archived = buildSnapshot("active", "ARCHIVED", "delivered", "", 0, 0, 0);
        assertThat(OperationalFlowCoreService.decide(archived).status()).isEqualTo("NO_OPERATION_TERMINAL");

        // Delivered state -> AWAITING_ACCEPTANCE_DECISION
        FlowSpineDto delivered = buildSnapshot("active", "DELIVERED", "delivered", "", 0, 0, 0);
        assertThat(OperationalFlowCoreService.decide(delivered).status()).isEqualTo("AWAITING_ACCEPTANCE_DECISION");

        // Operational state -> NEXT_ACTION_IDENTIFIED
        FlowSpineDto queued = buildSnapshot("active", "QUEUED", "in_progress", "", 1, 0, 0);
        assertThat(OperationalFlowCoreService.decide(queued).status()).isEqualTo("NEXT_ACTION_IDENTIFIED");
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_04: decisionHash is pure deterministic SHA-256 and detects state divergence")
    void decisionHashPureDeterministicFalsificationHarness() {
        FlowSpineDto snapshot1 = buildSnapshot("active", "QUEUED", "in_progress", "", 2, 0, 0);
        FlowSpineDto snapshot2 = buildSnapshot("active", "QUEUED", "in_progress", "", 2, 0, 0);
        FlowSpineDto divergent = buildSnapshot("active", "IMPLEMENTING", "in_progress", "", 0, 1, 0);

        FlowCoreDto.Decision decision1 = OperationalFlowCoreService.decide(snapshot1);
        FlowCoreDto.Decision decision2 = OperationalFlowCoreService.decide(snapshot2);
        FlowCoreDto.Decision decisionDivergent = OperationalFlowCoreService.decide(divergent);

        assertThat(decision1.decisionHash()).isEqualTo(decision2.decisionHash());
        assertThat(decision1.decisionHash()).hasSize(64); // SHA-256 hex string
        assertThat(decision1.decisionHash()).isNotEqualTo(decisionDivergent.decisionHash());
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_02: Mathematical contract declares all 7 foundational invariants")
    void mathematicalContractInvariantsCompleteness() {
        FlowSpineService mockSpine = mock(FlowSpineService.class);
        FlowSpineEventRepository mockRepo = mock(FlowSpineEventRepository.class);
        OperationalFlowCoreService service = new OperationalFlowCoreService(mockSpine, mockRepo);

        FlowSpineDto snapshot = buildSnapshot("active", "QUEUED", "in_progress", "", 1, 0, 0);
        when(mockSpine.build(TEST_PROJECT_ID)).thenReturn(snapshot);
        when(mockRepo.findTop1ByProjectIdAndModeOrderByObservedAtDesc(TEST_PROJECT_ID, OperationalFlowCoreService.MODE))
                .thenReturn(Optional.empty());

        FlowCoreDto core = service.build(TEST_PROJECT_ID);

        assertThat(core.mathematicalContract().invariants()).hasSize(7);
        assertThat(core.mathematicalContract().precedenceOrder()).isNotEmpty();
        assertThat(core.mathematicalContract().safetyRule()).isNotEmpty();
        assertThat(core.mathematicalContract().decisionFunction()).isNotEmpty();
        assertThat(core.mathematicalContract().factsSource()).isNotEmpty();
    }

    @Test
    @DisplayName("DZHOZEF_RAZ_05: Journal deduplication suppresses duplicate saves when decision has not moved")
    void journalDeduplicationPreventsRedundantEventWrites() {
        FlowSpineService mockSpine = mock(FlowSpineService.class);
        FlowSpineEventRepository mockRepo = mock(FlowSpineEventRepository.class);
        OperationalFlowCoreService service = new OperationalFlowCoreService(mockSpine, mockRepo);

        FlowSpineDto snapshot = buildSnapshot("active", "QUEUED", "in_progress", "", 1, 0, 0);
        when(mockSpine.build(TEST_PROJECT_ID)).thenReturn(snapshot);

        // Case 1: First observation - no prior event in repository
        when(mockRepo.findTop1ByProjectIdAndModeOrderByObservedAtDesc(TEST_PROJECT_ID, OperationalFlowCoreService.MODE))
                .thenReturn(Optional.empty());
        when(mockRepo.countByProjectIdAndMode(TEST_PROJECT_ID, OperationalFlowCoreService.MODE))
                .thenReturn(0L);

        service.observe(TEST_PROJECT_ID);

        ArgumentCaptor<FlowSpineEventEntity> eventCaptor = ArgumentCaptor.forClass(FlowSpineEventEntity.class);
        verify(mockRepo, times(1)).save(eventCaptor.capture());
        FlowSpineEventEntity saved = eventCaptor.getValue();
        assertThat(saved.getMode()).isEqualTo("flow_core_enforced");
        assertThat(saved.getCurrentState()).isEqualTo("QUEUED");

        // Case 2: Second observation - repository returns previously recorded event with matching hashes
        clearInvocations(mockRepo);
        saved.setObservedAt(Instant.now());
        when(mockRepo.findTop1ByProjectIdAndModeOrderByObservedAtDesc(TEST_PROJECT_ID, OperationalFlowCoreService.MODE))
                .thenReturn(Optional.of(saved));
        when(mockRepo.countByProjectIdAndMode(TEST_PROJECT_ID, OperationalFlowCoreService.MODE))
                .thenReturn(1L);

        service.observe(TEST_PROJECT_ID);

        // Save must NOT be called because currentDecisionRecorded() is true
        verify(mockRepo, never()).save(any(FlowSpineEventEntity.class));
    }
}
