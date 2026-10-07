package com.eneik.production.services;

import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Test Harness for BottleneckAwarePriorityService (Stage 4).
 *
 * <p>Philosopher Anchors:
 * 1. FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK [D011, Dretske]:
 *    Teleosemantic informational feedback: the priority value (100 vs 0) is an informational
 *    channel carrying semantic content about system bottlenecks and TOC constraints.
 *    A shifted constraint immediately reflects in shifted priority without stale cached states.
 *
 * 2. FRENK_RAMSEY_01_DECISION_EXPECTED_LOSS [D005, Ramsey]:
 *    Decision under expected loss without subordination: priority reorders the queue to minimize
 *    systemic throughput loss, but strictly NEVER stops, cancels, drops, or denies work.
 *    Zero decisions of idle, deny, or starvation (non-bottleneck tasks remain at 0, never negative).
 *
 * 3. AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP [D004, Varzi]:
 *    Part-whole aggregate boundary: BottleneckAwarePriorityService is a pure calculator and
 *    coordinator; it delegates all database queue updates to ClaimService and strictly performs
 *    zero direct write mutations on TaskRepository.
 */
class BottleneckAwarePriorityServiceFalsificationTest {

    private BottleneckDetectionService bottleneckDetectionService;
    private TaskRepository taskRepository;
    private ClaimService claimService;
    private TocSentinelService tocSentinelService;
    private BottleneckAwarePriorityService service;

    @BeforeEach
    void setUp() {
        bottleneckDetectionService = mock(BottleneckDetectionService.class);
        taskRepository = mock(TaskRepository.class);
        claimService = mock(ClaimService.class);
        tocSentinelService = mock(TocSentinelService.class);

        service = new BottleneckAwarePriorityService(
                bottleneckDetectionService,
                taskRepository,
                claimService,
                tocSentinelService
        );
    }

    // =========================================================================
    // 1. FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK [D011, Dretske]
    // =========================================================================

    @Test
    @DisplayName("Dretske D011: Teleosemantic feedback shifts priority immediately when TOC constraint shifts")
    void falsifyDretskeTeleosemanticFeedback_priorityReflectsInformationalSignalOfConstraint() {
        // Given an initial constraint at STAGE_ORCHESTRATE
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("STAGE_ORCHESTRATE");
        when(bottleneckDetectionService.detect()).thenReturn(Collections.emptyList());

        assertEquals(100, service.computePriority("STAGE_ORCHESTRATE"));
        assertEquals(0, service.computePriority("STAGE_AUTOMERGE"));

        // When the constraint shifts to STAGE_AUTOMERGE
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("STAGE_AUTOMERGE");

        // Then priority of STAGE_ORCHESTRATE drops to 0 and STAGE_AUTOMERGE rises to 100
        assertEquals(0, service.computePriority("STAGE_ORCHESTRATE"),
                "Falsification: priority retained stale constraint signal!");
        assertEquals(100, service.computePriority("STAGE_AUTOMERGE"),
                "Falsification: priority did not reflect shifted constraint signal!");
    }

    @Test
    @DisplayName("Dretske D011: Account bottleneck signal conveys priority boost to account tasks")
    void falsifyDretskeTeleosemanticFeedback_accountBottleneckSignalPromotesAccountTasks() {
        UUID bottleneckAccountId = UUID.randomUUID();
        UUID healthyAccountId = UUID.randomUUID();

        when(tocSentinelService.getCurrentConstraintName()).thenReturn("NONE");
        when(bottleneckDetectionService.detect()).thenReturn(List.of(
                new BottleneckDto("ACCOUNT_LIMIT", bottleneckAccountId, 15L, "Account starved")
        ));

        assertEquals(100, service.computePriority(bottleneckAccountId.toString()),
                "Falsification: starved account task did not receive priority boost!");
        assertEquals(0, service.computePriority(healthyAccountId.toString()),
                "Falsification: unconstrained account received unearned priority boost!");
    }

    // =========================================================================
    // 2. FRENK_RAMSEY_01_DECISION_EXPECTED_LOSS [D005, Ramsey]
    // =========================================================================

    @Test
    @DisplayName("Ramsey D005: Priority reorders queue but strictly NEVER stops, drops, denies or cancels tasks")
    void falsifyRamseyDecisionExpectedLoss_priorityNeverStopsDropsOrDeniesTasks() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("ORCHESTRATE_PROCESSING");
        when(bottleneckDetectionService.detect()).thenReturn(List.of(
                new BottleneckDto("TAG", "HOT_FEATURE", 5L, 30L, "Hot path")
        ));

        // When priority refresh is triggered
        service.refreshQueuedTasksPriority();

        // Then ClaimService is called to refresh priorities with high positive value (100)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(claimService, times(1)).refreshQueuedTasksPriority(captor.capture(), eq(100));

        Set<String> refs = captor.getValue();
        assertTrue(refs.contains("ORCHESTRATE_PROCESSING"));
        assertTrue(refs.contains("HOT_FEATURE"));

        // And strictly NO negative decisions, no dropping or cancellation: only refreshQueuedTasksPriority is invoked
        verifyNoMoreInteractions(claimService);
    }

    @Test
    @DisplayName("Ramsey D005: Non-bottleneck tasks receive default priority 0, never negative or starvation priority")
    void falsifyRamseyDecisionExpectedLoss_defaultPriorityIsZeroNeverNegative() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("PR_REVIEW");
        when(bottleneckDetectionService.detect()).thenReturn(Collections.emptyList());

        int defaultPriority = service.computePriority("UNCONSTRAINED_TASK");
        assertEquals(0, defaultPriority,
                "Falsification: unconstrained task received non-zero or negative priority!");
        assertTrue(defaultPriority >= 0,
                "Falsification: unconstrained task received negative priority leading to starvation!");
    }

    // =========================================================================
    // 3. AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP [D004, Varzi]
    // =========================================================================

    @Test
    @DisplayName("Varzi D004: Pure coordinator delegates queue updates to ClaimService with zero direct TaskRepository writes")
    void falsifyVarziPartWholeOwnership_pureCoordinatorDoesNotDirectlyMutateTaskRepository() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("AUTOMERGE");
        when(bottleneckDetectionService.detect()).thenReturn(List.of(
                new BottleneckDto("TAG", "REVIEW_QUEUE", 2L, 15L, "Review bottleneck")
        ));

        service.refreshQueuedTasksPriority();

        // Assert that TaskRepository is NEVER mutated directly by BottleneckAwarePriorityService
        verify(taskRepository, never()).save(any());
        verify(taskRepository, never()).saveAll(any());
        verify(taskRepository, never()).delete(any());
        verify(taskRepository, never()).deleteAll();
        verify(taskRepository, never()).deleteAll(any());

        // All updates are funneled cleanly through ClaimService
        verify(claimService, times(1)).refreshQueuedTasksPriority(anySet(), eq(100));
    }

    @Test
    @DisplayName("Varzi D004: Absence of parts (empty bottlenecks and null sentinel) handled safely without exception")
    void falsifyVarziPartWholeOwnership_nullOrEmptyBottlenecksSafelyHandledWithoutException() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn(null);
        when(bottleneckDetectionService.detect()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> service.refreshQueuedTasksPriority(),
                "Falsification: null or empty components threw exception in aggregate calculation!");

        verify(claimService, times(1)).refreshQueuedTasksPriority(Collections.emptySet(), 100);
    }
}
