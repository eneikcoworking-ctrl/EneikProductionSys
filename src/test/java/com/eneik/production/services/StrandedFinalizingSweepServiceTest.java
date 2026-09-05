package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StrandedFinalizingSweepServiceTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private StrandedFinalizingSweepService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        service = new StrandedFinalizingSweepService(projectRepository, wishlistRepository, null);
        // Wire self-reference
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "maxAgeMinutes", 3L);
    }

    @Test
    void sweepsStrandedFinalizingWishlistsOlderThanMaxAgeToPending() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID strandedWishlistId = UUID.randomUUID();
        WishlistEntity stranded = new WishlistEntity();
        stranded.setId(strandedWishlistId);
        stranded.setProjectId(projectId);
        stranded.setStatus(WishlistStatus.finalizing);
        stranded.setLastCompileDispatchedAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(stranded));
        when(wishlistRepository.compareAndSetStatus(strandedWishlistId, WishlistStatus.finalizing, WishlistStatus.pending))
                .thenReturn(1);

        service.sweepProject(project);

        verify(wishlistRepository).compareAndSetStatus(
                strandedWishlistId, WishlistStatus.finalizing, WishlistStatus.pending);
    }

    @Test
    void doesNotSweepFinalizingWishlistsWithinLeaseWindow() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID activeWishlistId = UUID.randomUUID();
        WishlistEntity active = new WishlistEntity();
        active.setId(activeWishlistId);
        active.setProjectId(projectId);
        active.setStatus(WishlistStatus.finalizing);
        active.setLastCompileDispatchedAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(active));

        service.sweepProject(project);

        verify(wishlistRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    @Test
    void sweepIteratesAllActiveProjects() {
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(UUID.randomUUID());
        activeProject.setStatus(ProjectStatus.active);

        ProjectEntity nonActiveProject = new ProjectEntity();
        nonActiveProject.setId(UUID.randomUUID());
        nonActiveProject.setStatus(ProjectStatus.archived);

        when(projectRepository.findAll()).thenReturn(List.of(activeProject, nonActiveProject));
        when(wishlistRepository.findByProjectIdAndStatus(activeProject.getId(), WishlistStatus.finalizing))
                .thenReturn(List.of());

        service.sweep();

        verify(wishlistRepository).findByProjectIdAndStatus(activeProject.getId(), WishlistStatus.finalizing);
        verify(wishlistRepository, never()).findByProjectIdAndStatus(nonActiveProject.getId(), WishlistStatus.finalizing);
    }
}
