package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Model rule 8.19 with Charter invariant 10: "did it reach main" and "was the requirement delivered" are one
 * question and must be answered by one predicate.
 *
 * <p>Measured on the live circuit 2026-08-30: 400 tasks done, 13 of 19 client requirements delivered, and
 * 16 done tasks that reached main carrying no code. Those 16 satisfied the department's selection
 * (reachedMain) and failed the value count (hasRequiredMergeEvidence), so nothing saw them: the requirement
 * was not delivered, and no place in the factory said so. That is muda in its exact sense - work executed,
 * merged, counted done, delivering nothing.
 */
class DeliveryPredicateAgreementTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final OperationalRealityFindingRepository findingRepository = mock(OperationalRealityFindingRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final PlannedWorkRecoveryService plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);

    private final DeliveryRealityProducerService service = new DeliveryRealityProducerService(
            projectRepository, taskRepository, readinessService, findingRepository,
            mock(EvidenceNodeRepository.class), wishlistRepository, plannedWorkRecoveryService);

    private ProjectEntity activeProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);
        project.setName("test-project");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        return project;
    }

    private TaskEntity doneTask(ProjectEntity project) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setTitle("API Slice");
        task.setStatus(TaskStatus.done);
        // Real product work belongs to an epic. Without one, filing refuses before it ever reaches the
        // question this class asks - model rule 8.18.1 forbids a repair from founding an epic of its own,
        // so a task with no epic and no source wishlist is declined for a reason that has nothing to do
        // with whether its merge carried code. The fixture was written before that rule existed and had
        // been failing in main ever since, asserting nothing.
        task.setFeatureId(UUID.randomUUID());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        return task;
    }

    @Test
    void aReissuedOrderCarriesTheGroundOfTheDenialItAnswers() {
        // Model rule 8.22. A repair is a turn of a cycle and rule 8.4 requires every cycle to carry
        // something that strictly decreases. Reissuing the failed order unchanged decreases nothing - the
        // next agent gets the same task, in the same words, knowing what the last one knew. The ground was
        // recorded next to the verdict and nothing outside the judgment service could read it.
        ProjectEntity project = activeProject();
        TaskEntity task = doneTask(project);
        task.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put(TaskEntity.ACCEPTANCE_VERDICT_KEY, TaskEntity.VERDICT_REFUTED)
                .put(TaskEntity.ACCEPTANCE_VERDICT_REASON_KEY,
                        "the merged diff records a blocker and carries none of the change"));
        when(readinessService.reachedMain(task)).thenReturn(true);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        // The steady state after the first sweep: the finding is already on record, which is the path the
        // filing actually runs on.
        com.eneik.production.models.persistence.OperationalRealityFindingEntity known =
                new com.eneik.production.models.persistence.OperationalRealityFindingEntity();
        known.setTaskId(task.getId());
        when(findingRepository.findByTaskId(task.getId())).thenReturn(List.of(known));
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(any(), any(), any())).thenReturn(false);
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());

        service.produce();

        org.mockito.ArgumentCaptor<com.eneik.production.models.persistence.WishlistEntity> filed =
                org.mockito.ArgumentCaptor.forClass(com.eneik.production.models.persistence.WishlistEntity.class);
        verify(wishlistRepository).save(filed.capture());
        assertTrue(filed.getValue().getContent()
                        .contains("the merged diff records a blocker and carries none of the change"),
                "the reissued order does not carry the ground of the denial it answers");
    }

    @Test
    void anUnrecordedGroundIsNotInvented() {
        // Without this the rule turns into "make one up", which is the defect 8.3.1 exists to prevent,
        // committed by the repairer instead of the denier.
        ProjectEntity project = activeProject();
        TaskEntity task = doneTask(project);
        when(readinessService.reachedMain(task)).thenReturn(true);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        // The steady state after the first sweep: the finding is already on record, which is the path the
        // filing actually runs on.
        com.eneik.production.models.persistence.OperationalRealityFindingEntity known =
                new com.eneik.production.models.persistence.OperationalRealityFindingEntity();
        known.setTaskId(task.getId());
        when(findingRepository.findByTaskId(task.getId())).thenReturn(List.of(known));
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(any(), any(), any())).thenReturn(false);
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());

        service.produce();

        org.mockito.ArgumentCaptor<com.eneik.production.models.persistence.WishlistEntity> filed =
                org.mockito.ArgumentCaptor.forClass(com.eneik.production.models.persistence.WishlistEntity.class);
        verify(wishlistRepository).save(filed.capture());
        assertFalse(filed.getValue().getContent().contains("already judged"),
                "no verdict was recorded, so the brief must not claim one");
    }

    @Test
    void oneSweepReadsEachTableOnce() {
        // Charter invariant 10, one point of application. The sweep used to ask for the project's whole
        // task table four times and its whole wishlist table twice, each caller building its own maps over
        // the same rows. Measured 2026-09-03 while the flow stood still: the backend sat at 863 MiB of its
        // 1 GiB limit on two cores, Hikari reported thread starvation with an 84-second housekeeper delta,
        // and a read path that had answered in 4.6 seconds took over 200. Same rows, same answers - asking
        // once is not a cache.
        ProjectEntity project = activeProject();
        TaskEntity task = doneTask(project);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(true);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());

        service.produce();

        verify(taskRepository, times(1)).findByProjectIdOrderByCreatedAtDesc(project.getId());
        verify(wishlistRepository, times(1)).findByProjectId(project.getId());
    }

    @Test
    void workThatMergedWithoutCarryingCodeIsOrderedAgain() {
        ProjectEntity project = activeProject();
        TaskEntity task = doneTask(project);
        // The exact live situation: the pull request DID merge to main - reachedMain is true - but it
        // carried a blocker record instead of the work, so the requirement is not delivered. Selecting on
        // reachedMain skips this task; selecting on the delivery predicate does not.
        when(readinessService.reachedMain(task)).thenReturn(true);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        // A finding is already on record, which is the steady state after the first sweep - the question
        // this test asks is whether the WORK gets ordered again, and that must not depend on the finding
        // being new (the 2026-08-23 lesson: a repair on the path the defect does not take is no repair).
        com.eneik.production.models.persistence.OperationalRealityFindingEntity known =
                new com.eneik.production.models.persistence.OperationalRealityFindingEntity();
        known.setTaskId(task.getId());
        when(findingRepository.findByTaskId(task.getId())).thenReturn(List.of(known));
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(any(), any(), any())).thenReturn(false);

        service.produce();

        verify(wishlistRepository, times(1)).save(any());
    }

    @Test
    void workWhoseCodeIsOnMainIsLeftAlone() {
        // Without this the rule degenerates into re-ordering everything the factory ever delivered.
        ProjectEntity project = activeProject();
        TaskEntity task = doneTask(project);
        when(readinessService.reachedMain(task)).thenReturn(true);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(true);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);

        service.produce();

        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void auxiliaryWorkIsStillNeverOrderedAgain() {
        // A DECISION-stage or complex-Cynefin task is not expected to reach main on its own.
        ProjectEntity project = activeProject();
        TaskEntity task = doneTask(project);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(true);

        service.produce();

        verify(wishlistRepository, never()).save(any());
    }

    /**
     * Model rule 8.11 O8: a record that cannot be read correctly is not a record. The sweep reported a task
     * count beside a sum of refreshed evidence nodes, which invited exactly the subtraction that produced a
     * false finding on 2026-09-02 - "50 parked, 16 recorded, therefore 34 unspoken for" - when the two
     * numbers answer different questions. The line now names how many TASKS the sweep saw.
     */
    @Test
    void theSweepSaysHowManyTasksItSawNotHowManyNodesItTouched() {
        ProjectEntity project = activeProject();
        TaskEntity task = doneTask(project);
        when(readinessService.reachedMain(task)).thenReturn(true);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        com.eneik.production.models.persistence.OperationalRealityFindingEntity known =
                new com.eneik.production.models.persistence.OperationalRealityFindingEntity();
        known.setId(UUID.randomUUID());
        known.setTaskId(task.getId());
        when(findingRepository.findByTaskId(task.getId())).thenReturn(List.of(known));
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(any(), any(), any())).thenReturn(true);

        Logs logs = Logs.capture(DeliveryRealityProducerService.class);
        try {
            service.produce();
        } finally {
            logs.stop();
        }

        assertTrue(logs.contains("1 task(s) with nothing delivered"),
                "the sweep must name the number of tasks it saw");
    }

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
            return appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(fragment));
        }

        void stop() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
