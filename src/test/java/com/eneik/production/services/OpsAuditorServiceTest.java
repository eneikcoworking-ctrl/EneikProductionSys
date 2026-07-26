package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testimony-vs-evidence, applied to the embedded auditor itself (2026-07-25): the auditor only gathers
 * INDEPENDENTLY VERIFIED evidence, and re-validates every precondition at execution time before acting -
 * these tests cover exactly that contract, not Gemini's own reasoning (which is out of process/untestable
 * here; the whitelist + precondition re-check is what has to be trustworthy regardless of what Gemini says).
 */
class OpsAuditorServiceTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private SystemSettingsService settingsService;
    private MLPredictionServiceClient mlPredictionServiceClient;
    private GeminiContextService geminiContextService;
    private OpsAuditorService service;

    private void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        settingsService = mock(SystemSettingsService.class);
        mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        geminiContextService = mock(GeminiContextService.class);
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");
        service = new OpsAuditorService(projectRepository, wishlistRepository, taskRepository, settingsService, mlPredictionServiceClient, geminiContextService);
    }

    private ProjectEntity project() {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setName("audited-project");
        p.setStatus(ProjectStatus.active);
        return p;
    }

    @Test
    void doesNothingWhenFeatureFlagIsOff() {
        setUp();
        ProjectEntity project = project();
        when(projectRepository.findAll()).thenReturn(List.of(project));

        service.runAuditCycle();

        verify(wishlistRepository, never()).findByProjectId(any());
        verify(mlPredictionServiceClient, never()).chatCritical(anyString(), anyString());
    }

    @Test
    void skipsGeminiCallWhenNoOrphanedWishlistEvidenceExists() {
        setUp();
        ProjectEntity project = project();
        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());

        service.runAuditCycle();

        verify(mlPredictionServiceClient, never()).chatCritical(anyString(), anyString());
    }

    @Test
    void dismissesOrphanedWishlistWhenGeminiDecidesToAndPreconditionStillHolds() {
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlist.setCompiledByRole("BARCAN-TAG-09");

        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);
        failedTask.setJulesDispatchStatus("Duplicate of another task");

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of(wishlist));
        when(wishlistRepository.findById(wishlistId)).thenReturn(java.util.Optional.of(wishlist));
        when(taskRepository.findBySourceWishlistIdIn(List.of(wishlistId))).thenReturn(List.of(failedTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "dismissOrphanedWishlist", "args": {"wishlistId": "%s", "reasoning": "all derived tasks terminally failed, duplicate work"}}]}
                """.formatted(wishlistId));

        service.runAuditCycle();

        verify(wishlistRepository).save(wishlist);
        assertEquals(WishlistStatus.dismissed, wishlist.getStatus());
    }

    @Test
    void doesNotDismissWhenGeminiFlagsForHumanReviewInstead() {
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlist.setCompiledByRole("BARCAN-TAG-09");

        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of(wishlist));
        when(taskRepository.findBySourceWishlistIdIn(List.of(wishlistId))).thenReturn(List.of(failedTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "flagForHumanReview", "args": {"subjectId": "%s", "reasoning": "not confident this is truly superseded"}}]}
                """.formatted(wishlistId));

        service.runAuditCycle();

        verify(wishlistRepository, never()).save(any(WishlistEntity.class));
        assertEquals(WishlistStatus.converted_to_task, wishlist.getStatus());
    }

    @Test
    void reVerifiesPreconditionAtExecutionTimeAndIgnoresStaleDecision() {
        // Between evidence-gathering and Gemini's response, a fresh task succeeded for the same wishlist -
        // the precondition ("all derived tasks terminally failed") no longer holds. Even though Gemini
        // (working from the now-stale evidence snapshot) said dismiss, execution must re-check and refuse.
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlist.setCompiledByRole("BARCAN-TAG-09");

        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of(wishlist));
        when(wishlistRepository.findById(wishlistId)).thenReturn(java.util.Optional.of(wishlist));
        // First call (evidence gathering) sees only the failed task; second call (precondition re-check at
        // execution time) sees a fresh successful task too - simulates a real race between gathering and acting.
        TaskEntity succeededTask = new TaskEntity();
        succeededTask.setId(UUID.randomUUID());
        succeededTask.setStatus(TaskStatus.done);
        when(taskRepository.findBySourceWishlistIdIn(List.of(wishlistId)))
                .thenReturn(List.of(failedTask))
                .thenReturn(List.of(failedTask, succeededTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "dismissOrphanedWishlist", "args": {"wishlistId": "%s", "reasoning": "looked orphaned at evidence time"}}]}
                """.formatted(wishlistId));

        service.runAuditCycle();

        verify(wishlistRepository, never()).save(any(WishlistEntity.class));
        assertEquals(WishlistStatus.converted_to_task, wishlist.getStatus());
    }

    @Test
    void unknownToolNameIsIgnoredNotExecuted() {
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlist.setCompiledByRole("BARCAN-TAG-09");

        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of(wishlist));
        when(taskRepository.findBySourceWishlistIdIn(List.of(wishlistId))).thenReturn(List.of(failedTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "deleteProject", "args": {"wishlistId": "%s", "reasoning": "trying to escape the whitelist"}}]}
                """.formatted(wishlistId));

        service.runAuditCycle();

        verify(wishlistRepository, never()).save(any(WishlistEntity.class));
    }
}
