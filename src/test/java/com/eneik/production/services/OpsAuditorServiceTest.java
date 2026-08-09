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
    private ClientDeliverableReadinessService readinessService;
    private OpsAuditorService service;

    private void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        settingsService = mock(SystemSettingsService.class);
        mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        geminiContextService = mock(GeminiContextService.class);
        readinessService = mock(ClientDeliverableReadinessService.class);
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");
        service = new OpsAuditorService(projectRepository, wishlistRepository, taskRepository, settingsService,
                mlPredictionServiceClient, geminiContextService, readinessService, null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
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

    private com.eneik.production.models.persistence.RoleEntity role(String tag) {
        com.eneik.production.models.persistence.RoleEntity r = new com.eneik.production.models.persistence.RoleEntity();
        r.setTag(tag);
        return r;
    }

    @Test
    void createsTargetedRecoveryTaskCopyingRoleFeatureAndSemanticKeyWhenGeminiDecidesToAndPreconditionHolds() {
        setUp();
        ProjectEntity project = project();
        UUID featureId = UUID.randomUUID();
        com.eneik.production.models.persistence.RoleEntity failedRole = role("BARCAN-TAG-08");

        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);
        failedTask.setRole(failedRole);
        failedTask.setFeatureId(featureId);
        failedTask.setTitle("Data Schema");
        failedTask.setDescription("Original brief");
        failedTask.setJulesDispatchStatus("Blocked task retired by iteration-admission poka-yoke; no child work created");
        com.fasterxml.jackson.databind.node.ObjectNode originalPayload = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        originalPayload.put("ems_semantic_key", "proj|BARCAN-TAG-08|data schema jtbd");
        failedTask.setPayload(originalPayload);

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(failedTask));
        when(readinessService.isAuxiliaryTask(failedTask)).thenReturn(false);
        when(readinessService.isDependencySatisfied(failedTask)).thenReturn(false);
        when(taskRepository.findById(failedTask.getId())).thenReturn(java.util.Optional.of(failedTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "createTargetedRecoveryTask", "args": {"subjectId": "%s", "reasoning": "transient failure, worth retrying"}}]}
                """.formatted(failedTask.getId()));
        org.mockito.ArgumentCaptor<TaskEntity> savedCaptor = org.mockito.ArgumentCaptor.forClass(TaskEntity.class);
        when(taskRepository.save(savedCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.runAuditCycle();

        TaskEntity saved = savedCaptor.getValue();
        assertEquals("BARCAN-TAG-08", saved.getRole().getTag());
        assertEquals(featureId, saved.getFeatureId());
        assertEquals(TaskStatus.queued, saved.getStatus());
        assertEquals("proj|BARCAN-TAG-08|data schema jtbd", saved.getPayload().path("ems_semantic_key").asText());
        assertEquals(failedTask.getId().toString(), saved.getPayload().path("recoversFailedTaskId").asText());
    }

    @Test
    void skipsRecoveryTaskWhenReplacementAlreadyExistsAtExecutionTime() {
        // Between evidence-gathering and Gemini's response, a real replacement already merged -
        // isDependencySatisfied re-checked at execution time must catch this and refuse, not trust the
        // stale evidence snapshot Gemini reasoned from.
        setUp();
        ProjectEntity project = project();
        com.eneik.production.models.persistence.RoleEntity failedRole = role("BARCAN-TAG-08");
        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);
        failedTask.setRole(failedRole);
        failedTask.setFeatureId(UUID.randomUUID());

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(failedTask));
        when(readinessService.isAuxiliaryTask(failedTask)).thenReturn(false);
        // False during evidence-gathering (so it's surfaced to Gemini at all), true by the time
        // createTargetedRecoveryTask re-checks at execution - simulates a real replacement merging in
        // between the two, which the execution-time re-check must catch rather than trusting the snapshot.
        when(readinessService.isDependencySatisfied(failedTask)).thenReturn(false, true);
        when(taskRepository.findById(failedTask.getId())).thenReturn(java.util.Optional.of(failedTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "createTargetedRecoveryTask", "args": {"subjectId": "%s", "reasoning": "looked stuck at evidence time"}}]}
                """.formatted(failedTask.getId()));

        service.runAuditCycle();

        verify(taskRepository, never()).save(any(TaskEntity.class));
    }

    /**
     * 2026-08-08 (engineering invariant #14, live incident test-forty-third): gatherOrphanedDependencyChainEvidence
     * used to require a CURRENTLY queued/blocked dependent pointing at the failed task before it would ever
     * surface it - a leaf-level failed task nothing else ever depended on was invisible forever, no matter
     * how many audit cycles ran. It now surfaces any failed task with no live replacement, dependent or not.
     */
    @Test
    void surfacesAFailedTaskWithNoDependentsAtAllAsOrphanedEvidence() {
        setUp();
        ProjectEntity project = project();
        com.eneik.production.models.persistence.RoleEntity failedRole = role("BARCAN-TAG-11");
        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);
        failedTask.setRole(failedRole);
        failedTask.setFeatureId(UUID.randomUUID());
        failedTask.setTitle("UI Slice, nobody depends on this");

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        // No dependent anywhere in the task list - just the failed task itself.
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(failedTask));
        when(readinessService.isAuxiliaryTask(failedTask)).thenReturn(false);
        when(readinessService.isDependencySatisfied(failedTask)).thenReturn(false);
        when(taskRepository.findById(failedTask.getId())).thenReturn(java.util.Optional.of(failedTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "createTargetedRecoveryTask", "args": {"subjectId": "%s", "reasoning": "orphaned leaf task, worth retrying"}}]}
                """.formatted(failedTask.getId()));
        org.mockito.ArgumentCaptor<TaskEntity> savedCaptor = org.mockito.ArgumentCaptor.forClass(TaskEntity.class);
        when(taskRepository.save(savedCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.runAuditCycle();

        assertEquals(failedTask.getId().toString(), savedCaptor.getValue().getPayload().path("recoversFailedTaskId").asText());
    }

    /**
     * 2026-08-08 (same incident): a failed task whose dependent already went ahead and finished (e.g. an
     * early-unblock path let it proceed without waiting) used to look "not stuck" and so was never surfaced
     * either - even though the failed task's own real scope was never delivered. Being terminally failed
     * with no live replacement is itself sufficient evidence, independent of any dependent's own status.
     */
    @Test
    void surfacesAFailedTaskEvenWhenItsOnlyDependentAlreadyProceededWithoutIt() {
        setUp();
        ProjectEntity project = project();
        com.eneik.production.models.persistence.RoleEntity failedRole = role("BARCAN-TAG-11");
        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);
        failedTask.setRole(failedRole);
        failedTask.setFeatureId(UUID.randomUUID());
        failedTask.setTitle("UI Slice, dependent moved on without it");

        TaskEntity dependent = new TaskEntity();
        dependent.setId(UUID.randomUUID());
        dependent.setStatus(TaskStatus.done);
        dependent.setDependsOn(failedTask);

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(dependent, failedTask));
        when(readinessService.isAuxiliaryTask(failedTask)).thenReturn(false);
        when(readinessService.isDependencySatisfied(failedTask)).thenReturn(false);
        when(taskRepository.findById(failedTask.getId())).thenReturn(java.util.Optional.of(failedTask));
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("""
                {"decisions": [{"tool": "createTargetedRecoveryTask", "args": {"subjectId": "%s", "reasoning": "dependent proceeded but real scope still missing"}}]}
                """.formatted(failedTask.getId()));
        org.mockito.ArgumentCaptor<TaskEntity> savedCaptor = org.mockito.ArgumentCaptor.forClass(TaskEntity.class);
        when(taskRepository.save(savedCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.runAuditCycle();

        assertEquals(failedTask.getId().toString(), savedCaptor.getValue().getPayload().path("recoversFailedTaskId").asText());
    }

    @Test
    void neverSurfacesAnAuxiliaryFailedTaskAsOrphanedEvidence() {
        // A DECISION-stage/complex-Cynefin failure was never counted toward feature-completion readiness -
        // recovering it buys nothing, and would just be noise Gemini has to reason about for no reason.
        setUp();
        ProjectEntity project = project();
        com.eneik.production.models.persistence.RoleEntity failedRole = role("BARCAN-TAG-09");
        TaskEntity failedTask = new TaskEntity();
        failedTask.setId(UUID.randomUUID());
        failedTask.setStatus(TaskStatus.failed);
        failedTask.setRole(failedRole);
        failedTask.setFeatureId(UUID.randomUUID());

        when(settingsService.effectiveBoolean("ops_auditor_enabled")).thenReturn(true);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(failedTask));
        when(readinessService.isAuxiliaryTask(failedTask)).thenReturn(true);

        service.runAuditCycle();

        verify(mlPredictionServiceClient, never()).chatCritical(anyString(), anyString());
        verify(taskRepository, never()).save(any(TaskEntity.class));
    }
}
