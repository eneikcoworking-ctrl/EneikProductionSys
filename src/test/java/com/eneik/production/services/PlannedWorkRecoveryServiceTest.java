package com.eneik.production.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PlannedWorkRecoveryServiceTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final JulesSessionRepository sessionRepository = mock(JulesSessionRepository.class);
    private final ClaimService claimService = mock(ClaimService.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final PlannedWorkRecoveryService service = new PlannedWorkRecoveryService(
            taskRepository, wishlistRepository, sessionRepository, claimService, readinessService, new ObjectMapper());

    @Test
    void resumesSameRootTaskOnlyOnceWithoutCreatingWork() {
        ReflectionTestUtils.setField(service, "frontierResumeLimit", 3);
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity task = retiredTask(project, source.getId());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(sessionRepository.findByTaskId(task.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(task.getId())).thenReturn(false);
        when(taskRepository.compareAndSetStatus(task.getId(), TaskStatus.failed, TaskStatus.queued)).thenReturn(1);

        assertEquals(1, service.resumeNextFrontier(project));
        assertEquals(TaskStatus.queued, task.getStatus());
        assertNull(task.getJulesSessionName());
        assertEquals(1, task.getPayload().path("ems_bounded_plan_resume_count").asInt());
        verify(taskRepository, times(1)).save(task);

        task.setStatus(TaskStatus.failed);
        assertEquals(0, service.resumeNextFrontier(project));
        verify(taskRepository, times(1)).save(task);
    }

    @Test
    void waitsForDependencyFrontierInsteadOfRequeueingWholeGraph() {
        ReflectionTestUtils.setField(service, "frontierResumeLimit", 3);
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity dependency = retiredTask(project, UUID.randomUUID());
        TaskEntity child = retiredTask(project, source.getId());
        child.setDependsOn(dependency);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(child));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(readinessService.isDependencySatisfied(dependency)).thenReturn(false);

        assertEquals(0, service.resumeNextFrontier(project));
        verify(taskRepository, never()).save(any());

        when(readinessService.isDependencySatisfied(dependency)).thenReturn(true);
        when(sessionRepository.findByTaskId(child.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(child.getId())).thenReturn(false);
        when(taskRepository.compareAndSetStatus(child.getId(), TaskStatus.failed, TaskStatus.queued)).thenReturn(1);
        assertEquals(1, service.resumeNextFrontier(project));
    }

    /**
     * Plan §4.33, corrected 2026-08-30 by measurement. The gate was widened so a dependent could be resumed
     * once its dependency was permanently dead. On the client's own epic that produced a one-second loop:
     * ProjectFlowService blocks such a dependent as a dead end (19:01:38), the admission sweep retires it
     * (19:02:45), this service resumes it (19:03:49), and it is blocked again one second later (19:03:50) -
     * spending the single automatic resume without any work being done. A dependent whose dependency is
     * unsatisfied is held here, and the dead-end case terminates through `blocked` instead.
     */
    @Test
    void aDependentIsNotResumedWhileItsDependencyIsUnsatisfiedEvenIfThatDependencyIsDeadForGood() {
        ReflectionTestUtils.setField(service, "frontierResumeLimit", 3);
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity deadDependency = retiredTask(project, UUID.randomUUID());
        ((ObjectNode) deadDependency.getPayload()).put("ems_bounded_plan_resume_count", 1);
        TaskEntity child = retiredTask(project, source.getId());
        child.setDependsOn(deadDependency);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(child));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(readinessService.isDependencySatisfied(deadDependency)).thenReturn(false);

        assertEquals(0, service.resumeNextFrontier(project));
        verify(taskRepository, never()).compareAndSetStatus(child.getId(), TaskStatus.failed, TaskStatus.queued);
    }

    @Test
    void cleanoutDoesNotCompleteCompilerTaskBeforeRealFeatureDecomposition() {
        ProjectEntity project = project();
        TaskEntity bootstrap = new TaskEntity();
        bootstrap.setId(UUID.randomUUID());
        bootstrap.setProject(project);
        bootstrap.setTitle("Runtime Contract");
        bootstrap.setStatus(TaskStatus.done);

        TaskEntity compiler = new TaskEntity();
        compiler.setId(UUID.randomUUID());
        compiler.setProject(project);
        compiler.setTitle("Compile 1 wishlist(s) into task graph");
        compiler.setStatus(TaskStatus.queued);

        when(readinessService.computeForProject(project.getId()))
                .thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(compiler, bootstrap));

        assertEquals(0, service.cleanoutOrphanedMetaTasksWhenProductComplete(project));
        assertEquals(TaskStatus.queued, compiler.getStatus());
        verify(taskRepository, never()).save(compiler);
        verify(claimService, never()).releaseTerminalClaim(compiler.getId());
    }

    @Test
    void cleanoutCompletesMetaTaskOnlyAfterRealFeatureDecompositionExists() {
        ProjectEntity project = project();
        TaskEntity productTask = new TaskEntity();
        productTask.setId(UUID.randomUUID());
        productTask.setProject(project);
        productTask.setTitle("Product feature task");
        productTask.setStatus(TaskStatus.done);

        TaskEntity compiler = new TaskEntity();
        compiler.setId(UUID.randomUUID());
        compiler.setProject(project);
        compiler.setTitle("Compile 1 wishlist(s) into task graph");
        compiler.setStatus(TaskStatus.queued);

        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(compiler, productTask));

        assertEquals(1, service.cleanoutOrphanedMetaTasksWhenProductComplete(project));
        assertEquals(TaskStatus.done, compiler.getStatus());
        verify(taskRepository).save(compiler);
        verify(claimService).releaseTerminalClaim(compiler.getId());
    }

    @Test
    void stuckCompilingWishlistDoesNotConvertBecauseAnotherWishlistConverted() {
        ProjectEntity project = project();
        WishlistEntity stuck = source(project.getId());
        stuck.setStatus(WishlistStatus.compiling);
        stuck.setCompiledByRole(null);
        stuck.setFeatureId(null);

        WishlistEntity unrelatedConverted = source(project.getId());
        unrelatedConverted.setStatus(WishlistStatus.converted_to_task);
        unrelatedConverted.setSource(WishlistSource.role);
        unrelatedConverted.setSourceRoleTag("BARCAN-TAG-01");

        when(wishlistRepository.findByProjectIdAndStatus(project.getId(), WishlistStatus.compiling))
                .thenReturn(List.of(stuck));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of());
        when(wishlistRepository.findByProjectId(project.getId()))
                .thenReturn(List.of(stuck, unrelatedConverted));
        when(readinessService.computeForProject(project.getId(), stuck.getId()))
                .thenReturn(ClientDeliverableReadinessService.Readiness.none());

        assertEquals(1, service.recoverStuckCompilingWishlists(project));
        assertEquals(WishlistStatus.pending, stuck.getStatus());
        verify(wishlistRepository).save(stuck);
    }

    private ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        return project;
    }

    private WishlistEntity source(UUID projectId) {
        WishlistEntity source = new WishlistEntity();
        source.setId(UUID.randomUUID());
        source.setProjectId(projectId);
        source.setSource(WishlistSource.client);
        source.setCompiledByRole("BARCAN-TAG-08");
        source.setFeatureId(UUID.randomUUID());
        source.setContent("planned slice");
        return source;
    }

    @Test
    void resumeTaskRevivesAGeneralGithubTruthReconciliationFailureNotJustTheTwoHistoricalStrings() {
        // 2026-08-01 regression test: tasks d9f35f4b/529e5252 (test-fortieth) both died via
        // reconcileClosedUnmergedPullRequest's generic reason text and had to be revived by hand via a raw
        // status PATCH, bypassing this service's atomic CAS/resume-count safety entirely - this is the
        // single-task entry point GeminiObserverActionService.reviveFailedTask now calls instead.
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity task = retiredTask(project, source.getId());
        task.setJulesDispatchStatus("PR#22 closed without merge on GitHub; task had no active claim/session "
                + "left to complete it normally (periodic GitHub-truth reconciliation, testimony-vs-evidence Phase 2)");
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(sessionRepository.findByTaskId(task.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(task.getId())).thenReturn(false);
        when(taskRepository.compareAndSetStatus(task.getId(), TaskStatus.failed, TaskStatus.queued)).thenReturn(1);

        assertEquals(true, service.resumeTask(task.getId()));
        assertEquals(TaskStatus.queued, task.getStatus());
        verify(taskRepository, times(1)).save(task);
    }

    @Test
    void resumeTaskRevivesAGeminiObserverSourcedTaskNotJustClientBriefWork() {
        // 2026-08-01 regression test: confirmed live on test-fortieth (task 0cb354e9) - Gemini's own
        // reviveFailedTask calls were silently rejected three separate cycles in a row because
        // PRODUCT_SOURCES excluded WishlistSource.gemini_observer, even though the failure reason matched
        // and the task was otherwise perfectly eligible. Safe now that platform-scope findings never reach
        // this pipeline at all (see GeminiProjectObserverService's Kaizen routing).
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        source.setSource(WishlistSource.gemini_observer);
        TaskEntity task = retiredTask(project, source.getId());
        task.setJulesDispatchStatus("PR#27 closed without merge on GitHub; task had no active claim/session "
                + "left to complete it normally (periodic GitHub-truth reconciliation, testimony-vs-evidence Phase 2)");
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(sessionRepository.findByTaskId(task.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(task.getId())).thenReturn(false);
        when(taskRepository.compareAndSetStatus(task.getId(), TaskStatus.failed, TaskStatus.queued)).thenReturn(1);

        assertEquals(true, service.resumeTask(task.getId()));
        assertEquals(TaskStatus.queued, task.getStatus());
    }

    @Test
    void resumeTaskRefusesATaskThatFailedForAnUnrelatedReason() {
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity task = retiredTask(project, source.getId());
        task.setJulesDispatchStatus("Review rejected: security vulnerability found in submitted code");
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));

        assertEquals(false, service.resumeTask(task.getId()));
        verify(taskRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    /**
     * Model rule 8.6: an element leaves the decision set exactly once, in the data, and only when nothing
     * can carry it to done. BLOCKED_BY_FAILED_FRONTIER denies ORCHESTRATE and both dispatch actions for the
     * whole project and names this service as its resolver, so which elements this resolver refuses, and on
     * what condition, has to be readable. Before this it refused with a bare `false` in every branch.
     */
    @Test
    void aRefusalInTheGatesOwnDenominatorNamesTheConditionThatHeldIt() {
        ReflectionTestUtils.setField(service, "frontierResumeLimit", 3);
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity dependency = retiredTask(project, source.getId());
        TaskEntity dependent = retiredTask(project, source.getId());
        dependent.setDependsOn(dependency);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(dependent));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(sessionRepository.findByTaskId(dependent.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(dependent.getId())).thenReturn(false);
        when(readinessService.isDependencySatisfied(dependency)).thenReturn(false);

        Logs logs = Logs.capture(PlannedWorkRecoveryService.class);
        try {
            assertEquals(0, service.resumeNextFrontier(project));
        } finally {
            logs.stop();
        }

        assertTrue(logs.contains("is held by"), "the frontier's refusal must be readable, not a bare false");
        assertTrue(logs.contains("is not satisfied"), "and must name the condition that held it");
    }

    /**
     * The complement. The fact is stable and the tick is not; the same sentence written every tick is what
     * produced the 868-repetition report this factory already had to remove.
     */
    @Test
    void anUnchangedRefusalIsNotRepeatedOnTheNextTick() {
        ReflectionTestUtils.setField(service, "frontierResumeLimit", 3);
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity dependency = retiredTask(project, source.getId());
        TaskEntity dependent = retiredTask(project, source.getId());
        dependent.setDependsOn(dependency);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(dependent));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(sessionRepository.findByTaskId(dependent.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(dependent.getId())).thenReturn(false);
        when(readinessService.isDependencySatisfied(dependency)).thenReturn(false);
        service.resumeNextFrontier(project);

        Logs logs = Logs.capture(PlannedWorkRecoveryService.class);
        try {
            service.resumeNextFrontier(project);
        } finally {
            logs.stop();
        }

        assertFalse(logs.contains("is held by"), "an unchanged fact carries no information on a second tick");
    }

    /** Minimal in-memory appender - the assertion is about what a reader can retrieve, so it reads the log. */
    private static final class Logs {
        private final ch.qos.logback.classic.Logger logger;
        private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();

        private Logs(Class<?> type) {
            logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(type);
            appender.start();
            logger.addAppender(appender);
        }

        static Logs capture(Class<?> type) {
            return new Logs(type);
        }

        boolean contains(String fragment) {
            return appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(fragment));
        }

        void stop() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private TaskEntity retiredTask(ProjectEntity project, UUID sourceWishlistId) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setSourceWishlistId(sourceWishlistId);
        task.setFeatureId(UUID.randomUUID());
        task.setStatus(TaskStatus.failed);
        task.setJulesSessionName("sessions/old");
        task.setJulesDispatchStatus(
                "Blocked task retired; auto-recovery follow-up disabled during task-expansion incident");
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("ems_semantic_key", "ems:test");
        task.setPayload(payload);
        return task;
    }
}
