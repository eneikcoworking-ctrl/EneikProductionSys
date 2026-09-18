package com.eneik.production.services.advice;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class IdleProjectAdviceServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private TaskRepository taskRepository;

    private IdleProjectAdviceService idleProjectAdviceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idleProjectAdviceService = new IdleProjectAdviceService(projectRepository, wishlistRepository, taskRepository);
    }

    @Test
    void checkIdleGuard_whenProjectIsNull_returnsActiveWorkPresent() {
        IdleProjectAdviceService.IdleGuardVerdict verdict = idleProjectAdviceService.checkIdleGuard(null);
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.ACTIVE_WORK_PRESENT, verdict);

        ProjectEntity noIdProject = new ProjectEntity();
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.ACTIVE_WORK_PRESENT,
                idleProjectAdviceService.checkIdleGuard(noIdProject));
        verifyNoInteractions(wishlistRepository, taskRepository);
    }

    @Test
    void checkIdleGuard_whenProjectHasPendingWishlist_returnsActiveWorkPresent() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("active-with-wishlist");

        WishlistEntity pending = new WishlistEntity();
        pending.setStatus(WishlistStatus.pending);
        when(wishlistRepository.findByProjectIdAndStatus(eq(project.getId()), eq(WishlistStatus.pending)))
                .thenReturn(List.of(pending));

        IdleProjectAdviceService.IdleGuardVerdict verdict = idleProjectAdviceService.checkIdleGuard(project);
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.ACTIVE_WORK_PRESENT, verdict);
        assertFalse(idleProjectAdviceService.isIdle(project));
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void checkIdleGuard_whenProjectHasQueuedTasks_returnsActiveWorkPresent() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("active-with-queue");

        when(wishlistRepository.findByProjectIdAndStatus(eq(project.getId()), eq(WishlistStatus.pending)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.countByProjectIdAndStatus(eq(project.getId()), eq(TaskStatus.queued)))
                .thenReturn(3L);

        IdleProjectAdviceService.IdleGuardVerdict verdict = idleProjectAdviceService.checkIdleGuard(project);
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.ACTIVE_WORK_PRESENT, verdict);
        assertFalse(idleProjectAdviceService.isIdle(project));
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void checkIdleGuard_whenProjectHasActiveTasks_returnsActiveWorkPresent() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("active-with-in-progress");

        when(wishlistRepository.findByProjectIdAndStatus(eq(project.getId()), eq(WishlistStatus.pending)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.countByProjectIdAndStatus(eq(project.getId()), eq(TaskStatus.queued)))
                .thenReturn(0L);
        when(taskRepository.findActiveTasksByProject(eq(project.getId())))
                .thenReturn(List.of(new TaskEntity()));

        IdleProjectAdviceService.IdleGuardVerdict verdict = idleProjectAdviceService.checkIdleGuard(project);
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.ACTIVE_WORK_PRESENT, verdict);
        assertFalse(idleProjectAdviceService.isIdle(project));
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void checkIdleGuard_whenProjectIsGenuinelyIdle_suppressesSpeculativeWorkAndReturnsIdlePrevented() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("genuine-idle-project");

        when(wishlistRepository.findByProjectIdAndStatus(eq(project.getId()), eq(WishlistStatus.pending)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.countByProjectIdAndStatus(eq(project.getId()), eq(TaskStatus.queued)))
                .thenReturn(0L);
        when(taskRepository.findActiveTasksByProject(eq(project.getId())))
                .thenReturn(Collections.emptyList());

        assertTrue(idleProjectAdviceService.isIdle(project));

        IdleProjectAdviceService.IdleGuardVerdict verdict = idleProjectAdviceService.checkIdleGuard(project);
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.IDLE_SPECULATIVE_WORK_PREVENTED, verdict);

        // Teleosemantic proof (FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK D011):
        // Guard prevents erroneous speculative generation; zero wishlist entities are saved.
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void generateIdleProjectAdvice_evaluatesAllActiveProjectsAndCollectsVerdicts() {
        ProjectEntity idleProject = new ProjectEntity();
        idleProject.setId(UUID.randomUUID());
        idleProject.setName("idle-project");

        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(UUID.randomUUID());
        activeProject.setName("active-project");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(idleProject, activeProject));

        // Idle project setup
        when(wishlistRepository.findByProjectIdAndStatus(eq(idleProject.getId()), eq(WishlistStatus.pending)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.countByProjectIdAndStatus(eq(idleProject.getId()), eq(TaskStatus.queued)))
                .thenReturn(0L);
        when(taskRepository.findActiveTasksByProject(eq(idleProject.getId())))
                .thenReturn(Collections.emptyList());

        // Active project setup (has queued task)
        when(wishlistRepository.findByProjectIdAndStatus(eq(activeProject.getId()), eq(WishlistStatus.pending)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.countByProjectIdAndStatus(eq(activeProject.getId()), eq(TaskStatus.queued)))
                .thenReturn(1L);

        Map<UUID, IdleProjectAdviceService.IdleGuardVerdict> verdicts =
                idleProjectAdviceService.generateIdleProjectAdvice();

        assertEquals(2, verdicts.size());
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.IDLE_SPECULATIVE_WORK_PREVENTED,
                verdicts.get(idleProject.getId()));
        assertEquals(IdleProjectAdviceService.IdleGuardVerdict.ACTIVE_WORK_PRESENT,
                verdicts.get(activeProject.getId()));

        verify(wishlistRepository, never()).save(any());
    }
}
