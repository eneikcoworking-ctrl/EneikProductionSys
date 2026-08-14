package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectEventLogEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.ProjectEventLogRepository;
import com.eneik.production.repositories.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This policy deletes data, so its mistakes are unrecoverable - a log wrongly dropped cannot be
 * reconstructed. These tests pin the boundaries that matter rather than the mechanics: what must survive
 * is more important here than what gets removed.
 */
class ProjectEventLogRetentionServiceTest {

    private ProjectEventLogRepository repository;
    private ProjectRepository projectRepository;
    private ProjectEventLogRetentionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProjectEventLogRepository.class);
        projectRepository = mock(ProjectRepository.class);
        service = new ProjectEventLogRetentionService(repository, projectRepository, null);
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "retainAfterAcceptedDays", 30);
        ReflectionTestUtils.setField(service, "maxEntriesPerProject", 20000);
    }

    private ProjectEntity project(ProjectStatus status, Instant acceptedAt) {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setStatus(status);
        p.setAcceptedAt(acceptedAt);
        return p;
    }

    @Test
    void neverTouchesTheLogOfAProjectThatIsStillRunning() {
        ProjectEntity active = project(ProjectStatus.active, null);
        when(projectRepository.findAll()).thenReturn(List.of(active));
        when(repository.countByProjectId(active.getId())).thenReturn(500L);

        service.enforceRetention();

        verify(repository, never()).deleteByProjectIdAndCreatedAtBefore(any(), any());
    }

    /**
     * The operator's requirement was a full log "from project start to its acceptance". Age alone must
     * therefore never trigger deletion - an old entry on an unaccepted project is exactly what that
     * sentence asks to keep.
     */
    @Test
    void keepsOldEntriesWhileTheProjectHasNotBeenAccepted() {
        ProjectEntity waiting = project(ProjectStatus.waiting, null);
        when(projectRepository.findAll()).thenReturn(List.of(waiting));
        when(repository.countByProjectId(waiting.getId())).thenReturn(19_999L);

        service.enforceRetention();

        verify(repository, never()).deleteByProjectIdAndCreatedAtBefore(any(), any());
    }

    @Test
    void keepsAFrozenProjectsLogBecauseFrozenIsPausedNotFinished() {
        ProjectEntity frozen = project(ProjectStatus.frozen, null);
        when(projectRepository.findAll()).thenReturn(List.of(frozen));
        when(repository.countByProjectId(frozen.getId())).thenReturn(100L);

        service.enforceRetention();

        verify(repository, never()).deleteByProjectIdAndCreatedAtBefore(any(), any());
    }

    @Test
    void keepsAnAcceptedProjectsLogThroughTheGracePeriod() {
        ProjectEntity justAccepted = project(ProjectStatus.accepted, Instant.now().minus(3, ChronoUnit.DAYS));
        when(projectRepository.findAll()).thenReturn(List.of(justAccepted));
        when(repository.countByProjectId(justAccepted.getId())).thenReturn(100L);

        service.enforceRetention();

        verify(repository, never()).deleteByProjectIdAndCreatedAtBefore(any(), any());
    }

    @Test
    void dropsAnAcceptedProjectsLogOnceTheGracePeriodHasPassed() {
        ProjectEntity longAccepted = project(ProjectStatus.accepted, Instant.now().minus(90, ChronoUnit.DAYS));
        when(projectRepository.findAll()).thenReturn(List.of(longAccepted));
        when(repository.deleteByProjectIdAndCreatedAtBefore(eq(longAccepted.getId()), any())).thenReturn(42);

        service.enforceRetention();

        verify(repository).deleteByProjectIdAndCreatedAtBefore(eq(longAccepted.getId()), any());
    }

    /**
     * The ceiling is the bound that stops one unaccepted project filling the database on its own. It must
     * keep the NEWEST entries: recent history is what anyone diagnosing a stalled project reads.
     */
    @Test
    void trimsOnlyTheExcessAndKeepsTheNewestEntries() {
        UUID projectId = UUID.randomUUID();
        Instant cutoff = Instant.now().minus(10, ChronoUnit.DAYS);
        ProjectEventLogEntity oldestKeptBoundary = new ProjectEventLogEntity();
        oldestKeptBoundary.setCreatedAt(cutoff);

        when(repository.countByProjectId(projectId)).thenReturn(20_005L);
        when(repository.findByProjectIdOrderByCreatedAtAsc(eq(projectId), any(Pageable.class)))
                .thenReturn(List.of(oldestKeptBoundary));
        when(repository.deleteByProjectIdAndCreatedAtBefore(eq(projectId), eq(cutoff))).thenReturn(5);

        int removed = service.trimToCeiling(projectId, 20_000);

        org.assertj.core.api.Assertions.assertThat(removed).isEqualTo(5);
        verify(repository).deleteByProjectIdAndCreatedAtBefore(eq(projectId), eq(cutoff));
    }

    @Test
    void oneProjectFailingDoesNotStopTheSweep() {
        ProjectEntity broken = project(ProjectStatus.accepted, Instant.now().minus(90, ChronoUnit.DAYS));
        ProjectEntity healthy = project(ProjectStatus.accepted, Instant.now().minus(90, ChronoUnit.DAYS));
        when(projectRepository.findAll()).thenReturn(List.of(broken, healthy));
        when(repository.deleteByProjectIdAndCreatedAtBefore(eq(broken.getId()), any()))
                .thenThrow(new RuntimeException("simulated DB failure"));
        when(repository.deleteByProjectIdAndCreatedAtBefore(eq(healthy.getId()), any())).thenReturn(7);

        service.enforceRetention();

        verify(repository).deleteByProjectIdAndCreatedAtBefore(eq(healthy.getId()), any());
    }
}
