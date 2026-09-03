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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private final ClientDeliverableReadinessService readinessService =
            mock(ClientDeliverableReadinessService.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);

    private final DeliveryRealityProducerService service = new DeliveryRealityProducerService(
            mock(ProjectRepository.class), taskRepository,
            readinessService, mock(OperationalRealityFindingRepository.class),
            mock(EvidenceNodeRepository.class), wishlistRepository,
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

    @Test
    void aSliceIsFollowedBackToTheBriefItWasCutFrom() {
        // Rule 8.18 defines slices(w) = {v : originWishlist(v) = w}, and the walk that looks for a product
        // epic did not follow that link - it stopped at the slice. Measured on the live circuit: 0 of 133
        // repairs re-homeable while every one inherited its epic from the task it repairs.
        UUID productEpic = UUID.randomUUID();
        WishlistEntity brief = new WishlistEntity();
        brief.setId(UUID.randomUUID());
        brief.setFeatureId(productEpic);
        WishlistEntity slice = new WishlistEntity();
        slice.setId(UUID.randomUUID());
        slice.setOriginWishlistId(brief.getId());
        slice.setFeatureId(UUID.randomUUID());

        assertEquals(brief, service.throughLineage(slice, Map.of(brief.getId(), brief), Set.of(productEpic)));
    }

    @Test
    void aBriefAlreadyInTheProductSetIsNotReplacedByItsAncestor() {
        // The reverse case: walking past a wishlist that already belongs to the product set would re-home
        // work away from the requirement it actually serves.
        UUID productEpic = UUID.randomUUID();
        WishlistEntity ancestor = new WishlistEntity();
        ancestor.setId(UUID.randomUUID());
        WishlistEntity item = new WishlistEntity();
        item.setId(UUID.randomUUID());
        item.setFeatureId(productEpic);
        item.setOriginWishlistId(ancestor.getId());

        assertEquals(item, service.throughLineage(item, Map.of(ancestor.getId(), ancestor), Set.of(productEpic)));
    }

    @Test
    void aWishlistWithNoLineageIsReturnedUnchanged() {
        WishlistEntity lone = new WishlistEntity();
        lone.setId(UUID.randomUUID());

        assertEquals(lone, service.throughLineage(lone, Map.of(), Set.of(UUID.randomUUID())));
    }

    @Test
    void aRepairWhoseRequirementEpicIsReachableIsReturnedToIt() {
        // Rule 8.18.1 is an equality, not a preference: epic(repair) = epic(requirement), always. Measured
        // on the live circuit, 133 of 143 repairs sat outside the product set - work that runs, merges and
        // moves nothing the value count can see.
        UUID productEpic = UUID.randomUUID();
        TaskEntity repaired = task(productEpic);
        WishlistEntity repair = repairOf(repaired, UUID.randomUUID());

        assertEquals(productEpic, service.homeOfRepair(repair, Map.of(),
                Map.of(repaired.getId(), repaired), Set.of(productEpic)));
    }

    @Test
    void aRepairAlreadyInItsRequirementEpicIsNotMoved() {
        // Without this the sweep would rewrite and save every repair on every pass.
        UUID productEpic = UUID.randomUUID();
        TaskEntity repaired = task(productEpic);
        WishlistEntity repair = repairOf(repaired, productEpic);

        assertNull(service.homeOfRepair(repair, Map.of(),
                Map.of(repaired.getId(), repaired), Set.of(productEpic)));
    }

    @Test
    void aRepairWithNoReachableProductEpicIsLeftAlone() {
        // Inventing a home would be the defect 8.18.1 forbids, committed while claiming to fix it.
        TaskEntity repaired = task(UUID.randomUUID());
        WishlistEntity repair = repairOf(repaired, UUID.randomUUID());

        assertNull(service.homeOfRepair(repair, Map.of(),
                Map.of(repaired.getId(), repaired), Set.of(UUID.randomUUID())));
    }

    @Test
    void aRepairIsFiledIntoTheProductEpicOfTheRequirement() {
        UUID productEpic = UUID.randomUUID();
        com.eneik.production.models.persistence.ProjectEntity project = project();
        TaskEntity attempt = task(productEpic);
        attempt.setProject(project);
        stubProductEpics(project, productEpic);

        assertEquals(productEpic, service.epicOfRequirement(attempt));
    }

    @Test
    void anEpicOutsideTheProductSetDoesNotEndTheWalk() {
        // Rule 8.18.1: the repair belongs to the epic of the REQUIREMENT, and a requirement is a planned
        // product item by construction. Returning the first non-null epic is how a stray is born and then
        // inherited - measured, 120 repairs outside the set with no product epic reachable from any.
        UUID productEpic = UUID.randomUUID();
        com.eneik.production.models.persistence.ProjectEntity project = project();
        TaskEntity attempt = task(UUID.randomUUID());
        attempt.setProject(project);
        WishlistEntity requirement = new WishlistEntity();
        requirement.setId(UUID.randomUUID());
        requirement.setFeatureId(productEpic);
        attempt.setSourceWishlistId(requirement.getId());
        stubProductEpics(project, productEpic);
        when(wishlistRepository.findById(requirement.getId())).thenReturn(java.util.Optional.of(requirement));

        assertEquals(productEpic, service.epicOfRequirement(attempt));
    }

    @Test
    void aStrayEpicOnThePathIsWalkedPastRatherThanReturned() {
        // The case that makes the rule bite: the wishlist the attempt came from carries an epic that is NOT
        // the product set's, and the requirement's own epic lies one hop further. Returning the first
        // non-null epic is precisely how a stray is born and then inherited down every later repair.
        UUID productEpic = UUID.randomUUID();
        UUID strayEpic = UUID.randomUUID();
        com.eneik.production.models.persistence.ProjectEntity project = project();
        TaskEntity attempt = task(null);
        attempt.setProject(project);

        WishlistEntity strayBrief = new WishlistEntity();
        strayBrief.setId(UUID.randomUUID());
        strayBrief.setFeatureId(strayEpic);
        TaskEntity earlierAttempt = task(null);
        strayBrief.setSourceTaskId(earlierAttempt.getId());
        attempt.setSourceWishlistId(strayBrief.getId());

        WishlistEntity requirement = new WishlistEntity();
        requirement.setId(UUID.randomUUID());
        requirement.setFeatureId(productEpic);
        earlierAttempt.setSourceWishlistId(requirement.getId());

        stubProductEpics(project, productEpic);
        when(wishlistRepository.findById(strayBrief.getId())).thenReturn(java.util.Optional.of(strayBrief));
        when(taskRepository.findById(earlierAttempt.getId())).thenReturn(java.util.Optional.of(earlierAttempt));
        when(wishlistRepository.findById(requirement.getId())).thenReturn(java.util.Optional.of(requirement));

        assertEquals(productEpic, service.epicOfRequirement(attempt),
                "the walk stopped at an epic that is not the requirement's");
    }

    @Test
    void noProductEpicAnywhereMeansNoEpicAtAll() {
        // The mandatory reverse case: filing must refuse rather than found an epic of its own, which is
        // exactly what 8.18.1 forbids.
        com.eneik.production.models.persistence.ProjectEntity project = project();
        TaskEntity attempt = task(UUID.randomUUID());
        attempt.setProject(project);
        stubProductEpics(project, UUID.randomUUID());

        assertNull(service.epicOfRequirement(attempt));
    }

    private com.eneik.production.models.persistence.ProjectEntity project() {
        com.eneik.production.models.persistence.ProjectEntity project =
                new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(UUID.randomUUID());
        return project;
    }

    private void stubProductEpics(com.eneik.production.models.persistence.ProjectEntity project, UUID... epics) {
        java.util.List<ClientDeliverableReadinessService.EpicDiagnostic> diagnostics =
                new java.util.ArrayList<>();
        for (UUID epic : epics) {
            diagnostics.add(new ClientDeliverableReadinessService.EpicDiagnostic(
                    epic, "epic", null, java.time.Instant.now(), true, false, 0, 0));
        }
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(diagnostics);
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
