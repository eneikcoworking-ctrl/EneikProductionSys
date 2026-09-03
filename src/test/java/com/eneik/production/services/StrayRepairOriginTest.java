package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Model rule 8.18.1: a repair belongs to the epic of the requirement it repairs and may not found one of
 * its own.
 *
 * <p>Measured on the live circuit 2026-09-03: of 135 repairs, 125 carry an epic outside the product set and
 * none of them can be re-homed - the work runs, merges and moves nothing value counts. The rule asks that
 * the ORIGIN of those stray epics be established before any code is changed, because the two possible
 * answers name two different sites: an epic inherited from the task being repaired means the stray was
 * already there and the chain is what has to be cut, while an epic that is not the repaired task's was made
 * at the filing site. A count that cannot tell them apart cannot choose between the two repairs.
 */
class StrayRepairOriginTest {

    private final DeliveryRealityProducerService service = new DeliveryRealityProducerService(
            mock(ProjectRepository.class), mock(TaskRepository.class),
            mock(ClientDeliverableReadinessService.class), mock(OperationalRealityFindingRepository.class),
            mock(EvidenceNodeRepository.class), mock(WishlistRepository.class),
            mock(PlannedWorkRecoveryService.class));

    @Test
    void anEpicCarriedDownFromTheRepairedTaskIsInherited() {
        UUID epic = UUID.randomUUID();
        TaskEntity repaired = task(epic);
        WishlistEntity repair = repairOf(repaired, epic);

        assertEquals("inherited", service.strayEpicOrigin(repair, Map.of(repaired.getId(), repaired)));
    }

    @Test
    void anEpicThatIsNotTheRepairedTasksWasFoundedHere() {
        // The case that must remain distinguishable: without it every stray reads as inherited and the
        // filing site is never named.
        TaskEntity repaired = task(UUID.randomUUID());
        WishlistEntity repair = repairOf(repaired, UUID.randomUUID());

        assertEquals("founded", service.strayEpicOrigin(repair, Map.of(repaired.getId(), repaired)));
    }

    @Test
    void aRepairCarryingNoEpicBelongsToNothing() {
        TaskEntity repaired = task(UUID.randomUUID());
        WishlistEntity repair = repairOf(repaired, null);

        assertEquals("none", service.strayEpicOrigin(repair, Map.of(repaired.getId(), repaired)));
    }

    @Test
    void aRepairWhoseTaskIsGoneAnswersNeither() {
        // Counting an unanswerable row as either origin would put weight behind a repair chosen on it.
        TaskEntity repaired = task(UUID.randomUUID());
        WishlistEntity repair = repairOf(repaired, UUID.randomUUID());

        assertEquals("repaired-task-gone", service.strayEpicOrigin(repair, Map.of()));
    }

    private TaskEntity task(UUID epic) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setFeatureId(epic);
        return task;
    }

    private WishlistEntity repairOf(TaskEntity repaired, UUID epic) {
        WishlistEntity repair = new WishlistEntity();
        repair.setId(UUID.randomUUID());
        repair.setSourceTaskId(repaired.getId());
        repair.setFeatureId(epic);
        return repair;
    }
}
