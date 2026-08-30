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
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        return task;
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
}
