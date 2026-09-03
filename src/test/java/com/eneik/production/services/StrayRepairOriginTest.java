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

    @Test
    void whatIsBeingRepairedNamesTheRoleAndTheOriginOfTheRepairedTask() {
        // Model rule 8.2: a carrier that did not deliver must not order product scope, while product work
        // that lost its epic has to be returned to its requirement. The two are told apart by the role the
        // repaired task carries and the source of the wishlist it came from, so both must appear.
        TaskEntity repaired = task(UUID.randomUUID());
        repaired.setRole(role("BARCAN-TAG-00"));
        WishlistEntity origin = new WishlistEntity();
        origin.setId(UUID.randomUUID());
        origin.setSource(com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main);
        repaired.setSourceWishlistId(origin.getId());
        WishlistEntity repair = repairOf(repaired, UUID.randomUUID());

        assertEquals("BARCAN-TAG-00/delivery_never_reached_main",
                service.whatIsBeingRepaired(repair, Map.of(origin.getId(), origin),
                        Map.of(repaired.getId(), repaired)));
    }

    @Test
    void aRepairedTaskWithNoRoleAndNoOriginIsStillNamed() {
        // An unnamed row must not silently join a named bucket - that is how a count stops answering.
        TaskEntity repaired = task(UUID.randomUUID());
        WishlistEntity repair = repairOf(repaired, UUID.randomUUID());

        assertEquals("no-role/no-source",
                service.whatIsBeingRepaired(repair, Map.of(), Map.of(repaired.getId(), repaired)));
    }

    private com.eneik.production.models.persistence.RoleEntity role(String tag) {
        com.eneik.production.models.persistence.RoleEntity role =
                new com.eneik.production.models.persistence.RoleEntity();
        role.setTag(tag);
        return role;
    }

    @Test
    void aSliceIsNotCountedAsABriefThatNamesNothing() {
        // The link to the repaired task is what puts a repair inside the requirement's closure (8.18) and
        // what makes chain depth observable (8.21). A brief without it is ordered work no requirement can
        // count, and it must be counted separately from briefs that carry the link - otherwise the two
        // readings that already disagree cannot be told apart.
        WishlistEntity named = repairBrief();
        named.setSourceTaskId(UUID.randomUUID());
        WishlistEntity namingNothing = repairBrief();

        WishlistEntity slice = repairBrief();
        slice.setOriginWishlistId(UUID.randomUUID());

        assertEquals("repair briefs 3, of them slices 1, naming nothing 1",
                service.repairBriefsNamingNothing(java.util.List.of(named, namingNothing, slice)));
    }

    @Test
    void wishlistsThatAreNotRepairBriefsAreNotCountedAsOne() {
        // Without this the count degenerates into "every wishlist without a source task", which is most of
        // the project and answers nothing.
        WishlistEntity plain = new WishlistEntity();
        plain.setId(UUID.randomUUID());
        plain.setSource(com.eneik.production.models.persistence.WishlistSource.client);

        assertEquals("repair briefs 0, of them slices 0, naming nothing 0",
                service.repairBriefsNamingNothing(java.util.List.of(plain)));
    }

    private WishlistEntity repairBrief() {
        WishlistEntity brief = new WishlistEntity();
        brief.setId(UUID.randomUUID());
        brief.setSource(com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main);
        return brief;
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
