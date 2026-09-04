package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Model rules 8.18 and 8.21: the requirement's closure is recursive - a repair of a repair is still in it.
 *
 * <p>The index the walk consults was built in ONE pass over the initial attempts, so the tasks of a
 * first-order repair were never asked about and their own repairs could not be found. The closure therefore
 * ended at depth one by construction, not by data: measured for weeks as `repair chain depths {1=145}` on a
 * project where every outstanding requirement already had a repair filed for its repair, while 14 of 19
 * client requirements stood undelivered.
 */
class RepairClosureReachesDeeperChainsTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);

    private final ClientDeliverableReadinessService service = new ClientDeliverableReadinessService(
            mock(WishlistRepository.class), mock(FeatureRepository.class), taskRepository,
            mock(JulesSessionRepository.class), mock(PrReviewRepository.class),
            mock(com.eneik.production.repositories.FeatureThreadRepository.class),
            mock(com.eneik.production.repositories.ProjectRepository.class),
            mock(com.eneik.production.services.operational.OperationalPolicyService.class));

    @Test
    void aRepairOfARepairIsInsideTheClosure() {
        TaskEntity firstAttempt = task();
        WishlistEntity firstRepair = repairOf(firstAttempt);
        WishlistEntity firstSlice = sliceOf(firstRepair);
        TaskEntity secondAttempt = taskFrom(firstSlice);

        WishlistEntity secondRepair = repairOf(secondAttempt);
        WishlistEntity secondSlice = sliceOf(secondRepair);
        TaskEntity thirdAttempt = taskFrom(secondSlice);

        when(taskRepository.findBySourceWishlistIdIn(any()))
                .thenReturn(List.of(secondAttempt, thirdAttempt));

        ClientDeliverableReadinessService.RepairIndex index = service.buildRepairIndex(
                List.of(firstRepair, firstSlice, secondRepair, secondSlice),
                Set.of(firstAttempt.getId()));
        List<TaskEntity> closure = service.repairClosure(List.of(firstAttempt),
                index.repairsByRepairedTask(), index.tasksByRepair());

        assertTrue(closure.contains(thirdAttempt),
                "the second-order repair's task never entered the closure");
        assertEquals(3, closure.size());
    }

    @Test
    void aRepairFiledForSomeoneElseIsNotPulledIn() {
        // The mandatory reverse case: growing the index to a fixpoint must not widen it into the project's
        // whole repair history, which is what the scoping in this method exists to prevent.
        TaskEntity attempt = task();
        TaskEntity strangersTask = task();
        WishlistEntity strangersRepair = repairOf(strangersTask);

        ClientDeliverableReadinessService.RepairIndex index = service.buildRepairIndex(
                List.of(strangersRepair), Set.of(attempt.getId()));
        List<TaskEntity> closure = service.repairClosure(List.of(attempt),
                index.repairsByRepairedTask(), index.tasksByRepair());

        assertEquals(List.of(attempt), closure);
    }

    private TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        return task;
    }

    private TaskEntity taskFrom(WishlistEntity slice) {
        TaskEntity task = task();
        task.setSourceWishlistId(slice.getId());
        return task;
    }

    private WishlistEntity repairOf(TaskEntity repaired) {
        WishlistEntity repair = new WishlistEntity();
        repair.setId(UUID.randomUUID());
        repair.setSourceTaskId(repaired.getId());
        return repair;
    }

    private WishlistEntity sliceOf(WishlistEntity repair) {
        WishlistEntity slice = new WishlistEntity();
        slice.setId(UUID.randomUUID());
        slice.setOriginWishlistId(repair.getId());
        return slice;
    }
}
