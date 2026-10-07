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
 * Unit test suite for BottleneckAwarePriorityService.
 */
class BottleneckAwarePriorityServiceTest {

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

    @Test
    @DisplayName("computePriority: returns default priority (0) for null, empty or blank constraint ref")
    void computePriority_returnsDefault_whenRefIsNullEmptyOrBlank() {
        assertEquals(0, service.computePriority(null));
        assertEquals(0, service.computePriority(""));
        assertEquals(0, service.computePriority("   "));
        verifyNoInteractions(bottleneckDetectionService);
    }

    @Test
    @DisplayName("computePriority: returns HIGH_PRIORITY (100) when ref matches TocSentinelService active constraint")
    void computePriority_returnsHighPriority_whenRefMatchesTocSentinelConstraint() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("AUTOMERGE_PROCESSING");

        assertEquals(100, service.computePriority("AUTOMERGE_PROCESSING"));
        assertEquals(100, service.computePriority("automerge_processing")); // case-insensitive
        verifyNoInteractions(bottleneckDetectionService);
    }

    @Test
    @DisplayName("computePriority: returns HIGH_PRIORITY (100) when ref matches bottleneck tag")
    void computePriority_returnsHighPriority_whenRefMatchesBottleneckTag() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("NONE");
        BottleneckDto bottleneck = new BottleneckDto("TAG_BOTTLENECK", "PR_REVIEW", 5L, 120L, "Backlog of PRs");
        when(bottleneckDetectionService.detect()).thenReturn(List.of(bottleneck));

        assertEquals(100, service.computePriority("PR_REVIEW"));
    }

    @Test
    @DisplayName("computePriority: returns HIGH_PRIORITY (100) when ref matches bottleneck accountId string")
    void computePriority_returnsHighPriority_whenRefMatchesBottleneckAccountId() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn(null);
        UUID accountId = UUID.randomUUID();
        BottleneckDto bottleneck = new BottleneckDto("ACCOUNT_STARVATION", accountId, 10L, "Account rate limited");
        when(bottleneckDetectionService.detect()).thenReturn(List.of(bottleneck));

        assertEquals(100, service.computePriority(accountId.toString()));
    }

    @Test
    @DisplayName("computePriority: returns DEFAULT_PRIORITY (0) when ref matches neither constraint nor bottlenecks")
    void computePriority_returnsDefault_whenRefDoesNotMatchAnyConstraintOrBottleneck() {
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("AUTOMERGE_PROCESSING");
        when(bottleneckDetectionService.detect()).thenReturn(List.of(
                new BottleneckDto("TAG", "OTHER_TAG", 1L, 10L, "minor")
        ));

        assertEquals(0, service.computePriority("UNMATCHED_CONSTRAINT"));
    }

    @Test
    @DisplayName("refreshQueuedTasksPriority: aggregates tags, account IDs and TOC constraint, delegating to ClaimService")
    void refreshQueuedTasksPriority_collectsAllRefsAndDelegatesToClaimService() {
        UUID accountId = UUID.randomUUID();
        when(bottleneckDetectionService.detect()).thenReturn(List.of(
                new BottleneckDto("TAG_BOTTLENECK", "TAG_A", 3L, 40L, "tag a bottleneck"),
                new BottleneckDto("ACCOUNT_BOTTLENECK", accountId, 5L, "account bottleneck")
        ));
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("ORCHESTRATE_PROCESSING");

        service.refreshQueuedTasksPriority();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(claimService).refreshQueuedTasksPriority(captor.capture(), eq(100));

        Set<String> capturedRefs = captor.getValue();
        assertEquals(3, capturedRefs.size());
        assertTrue(capturedRefs.contains("TAG_A"));
        assertTrue(capturedRefs.contains(accountId.toString()));
        assertTrue(capturedRefs.contains("ORCHESTRATE_PROCESSING"));
    }

    @Test
    @DisplayName("refreshQueuedTasksPriority: ignores 'NONE' or null TOC constraint name")
    void refreshQueuedTasksPriority_ignoresNoneSentinelConstraint() {
        when(bottleneckDetectionService.detect()).thenReturn(Collections.emptyList());
        when(tocSentinelService.getCurrentConstraintName()).thenReturn("NONE");

        service.refreshQueuedTasksPriority();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(claimService).refreshQueuedTasksPriority(captor.capture(), eq(100));

        Set<String> capturedRefs = captor.getValue();
        assertTrue(capturedRefs.isEmpty());
    }

    @Test
    @DisplayName("scheduledPriorityRefresh: invokes refreshQueuedTasksPriority")
    void scheduledPriorityRefresh_invokesRefreshQueuedTasksPriority() {
        when(bottleneckDetectionService.detect()).thenReturn(Collections.emptyList());
        when(tocSentinelService.getCurrentConstraintName()).thenReturn(null);

        service.scheduledPriorityRefresh();

        verify(claimService).refreshQueuedTasksPriority(anySet(), eq(100));
    }
}
