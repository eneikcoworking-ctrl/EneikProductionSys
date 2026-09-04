package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * P5 of §9: the two jobs `marker` used to do at once are now separate, and BOTH halves are asserted.
 *
 * <p>What happened on 2026-08-28. DeliveryRealityProducerService built one string,
 * {@code "task " + task.getId()}, and used it as the deduplication key AND as text inside a brief handed
 * to an agent working in the client's codebase. The agent has no entity called "task &lt;uuid&gt;", so it
 * did the only thing that string allowed: found a client table with a subject_id column and wrote
 * {@code UPDATE ... SET status='RESOLVED' WHERE subject_id = '<that uuid>'}. Seven Jules sessions were
 * then spent reviewing the pull request that came out of it.
 *
 * <p>That is §2's first invariant - {@code Code(t) ∩ ℒ_factory = ∅} - failing at the BRIEF boundary while
 * the poka-yoke that guards it stands at the MERGE boundary. Which is exactly why the client's main branch
 * stayed clean and the loop filled with nonsense: the guard held where it was, and there was no guard
 * where the leak was.
 *
 * <p>Why both halves. Removing the id from the text alone would have silently broken deduplication, which
 * matched that same substring - and the factory would have filed the same brief again every tick. Fixing
 * only the dedup would have left the leak. Half a fix on a boundary is the defect this session has already
 * paid for twice: a compile failure and two rounds of dead test stubs, each from changing one side.
 */
class DeliveryBriefZoneBoundaryTest {

    private static final Pattern ANY_UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final PlannedWorkRecoveryService plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);

    private DeliveryRealityProducerService service() {
        return new DeliveryRealityProducerService(
                mock(ProjectRepository.class),
                taskRepository,
                readinessService,
                mock(OperationalRealityFindingRepository.class),
                mock(EvidenceNodeRepository.class),
                wishlistRepository,
                plannedWorkRecoveryService);
    }

    /**
     * Model rule 8.12: every department's finding maps to a wishlist. This department owns "the work never
     * reached main", and its predicate read the status word rather than the deliverable, so abandoned
     * `failed` work belonged to no department at all. Measured on the live circuit 2026-08-30: 382 done, 38
     * failed and nothing else, six planned client deliverables unmerged, seven accounts free, and the
     * project standing in VERIFYING_DELIVERY for 3632 minutes - rule 8.11 L5 failing outright.
     */
    /**
     * The brief is the only thing the agent working in the client's codebase reads, so it must not assert
     * something about the task that is untrue. Widening this sweep to abandoned `failed` work while leaving
     * the sentence at "has status done" would hand that agent a false premise - the same boundary this test
     * class was written for, one field further along.
     */
    @Test
    void theBriefForAbandonedFailedWorkDoesNotClaimTheTaskSaidDone() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = task(project);
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.failed);

        service().fileTheMissingWorkAsScope(project, task);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        String content = captor.getValue().getContent();
        assertFalse(content.contains("has status done"), "the task did not say done - it failed");
        assertTrue(content.contains("has status failed"), "the brief must state what the record actually says");
    }

    @Test
    void theBriefForDoneWithoutMergeStillSaysTheStatusAssertedDelivery() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = task(project);
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.done);

        service().fileTheMissingWorkAsScope(project, task);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("has status done"));
    }

    @Test
    void abandonedFailedWorkIsWorkThatNeverLanded() {
        TaskEntity task = task(new ProjectEntity());
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.failed);
        when(plannedWorkRecoveryService.mayStillBeResumed(task)).thenReturn(false);

        assertTrue(service().isWorkThatNeverLanded(task));
    }

    /**
     * The complement, and it is not optional: work the recovery service can still resume is owned by that
     * service, which reuses the task identity. Ordering it again here would run the same requirement twice.
     */
    @Test
    void failedWorkTheRecoveryStillOwnsIsNotOrderedAgainHere() {
        TaskEntity task = task(new ProjectEntity());
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.failed);
        when(plannedWorkRecoveryService.mayStillBeResumed(task)).thenReturn(true);

        assertFalse(service().isWorkThatNeverLanded(task));
    }

    @Test
    void doneWorkIsStillWhatThisDepartmentPrimarilySpeaksAbout() {
        TaskEntity task = task(new ProjectEntity());
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.done);

        assertTrue(service().isWorkThatNeverLanded(task));
    }

    @Test
    void workThatIsStillRunningIsNotDeclaredLost() {
        TaskEntity task = task(new ProjectEntity());
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.in_progress);

        assertFalse(service().isWorkThatNeverLanded(task));
    }

    private TaskEntity task(ProjectEntity project) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setTitle("Runtime Contract 9b58412d");
        // The ordinary case: the task belongs to an epic, so the repair inherits it (model rule 8.18.1).
        // The case where nothing in the chain carries one has its own test below.
        UUID epic = UUID.randomUUID();
        task.setFeatureId(epic);
        if (project != null && project.getId() != null) {
            when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of(
                    new ClientDeliverableReadinessService.EpicDiagnostic(
                            epic, "default", null, java.time.Instant.now(), true, false, 0, 0)));
        }
        return task;
    }

    @Test
    void theBriefHandedToTheClientZoneCarriesNoFactoryIdentifier() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = task(project);
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(any(), any(), any()))
                .thenReturn(false);

        service().fileTheMissingWorkAsScope(project, task);

        ArgumentCaptor<WishlistEntity> saved = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(saved.capture());
        WishlistEntity brief = saved.getValue();

        assertFalse(brief.getContent().contains(task.getId().toString()),
                "the task's own id must not travel into the client's zone");
        assertFalse(ANY_UUID.matcher(brief.getContent()).find(),
                "no factory identifier of any kind belongs in text an agent in the client's zone reads");
        assertTrue(brief.getContent().contains("Runtime Contract 9b58412d"),
                "the task is still named - by its title, which is meaningful on both sides of the boundary");
    }

    @Test
    void theLinkSurvivesAsAColumnSoDeduplicationStillHasItsKey() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = task(project);
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(any(), any(), any()))
                .thenReturn(false);

        service().fileTheMissingWorkAsScope(project, task);

        ArgumentCaptor<WishlistEntity> saved = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(saved.capture());
        assertEquals(task.getId(), saved.getValue().getSourceTaskId(),
                "removing the id from the prose is only safe because it is kept here");
    }

    @Test
    void aBriefAlreadyFiledForThisTaskIsNotFiledAgain() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = task(project);
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(
                project.getId(), WishlistSource.delivery_never_reached_main, task.getId()))
                .thenReturn(true);

        service().fileTheMissingWorkAsScope(project, task);

        verify(wishlistRepository, never()).save(any());
    }

    /**
     * Model rule 8.18.1: a repair belongs to the epic of the requirement it repairs and never founds one.
     * When the repaired task has no epic of its own, the chain is walked back through the wishlist it came
     * from. Measured 2026-09-02: 434 of 444 repairs carried an epic outside the product set, spread over 30
     * such epics against nine real ones, because a repair with no epic minted one rooted in itself.
     */
    @Test
    void aRepairTakesTheEpicOfTheRequirementItRepairs() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        UUID requirementEpic = UUID.randomUUID();

        WishlistEntity requirement = new WishlistEntity();
        requirement.setId(UUID.randomUUID());
        requirement.setFeatureId(requirementEpic);

        TaskEntity attempt = task(project);
        attempt.setFeatureId(null);
        attempt.setSourceWishlistId(requirement.getId());
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of(
                new ClientDeliverableReadinessService.EpicDiagnostic(
                        requirementEpic, "requirement", null, java.time.Instant.now(), true, false, 0, 0)));
        // no featureId on the task itself - this is the case that used to mint a fresh epic
        when(wishlistRepository.findById(requirement.getId())).thenReturn(java.util.Optional.of(requirement));

        service().fileTheMissingWorkAsScope(project, attempt);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        assertEquals(requirementEpic, captor.getValue().getFeatureId());
    }

    /**
     * The complement, and it is not optional: when nothing in the chain carries an epic, the repair must
     * file NOTHING rather than found one. An epic founded on a repair brief is outside the product set by
     * construction and can never come back into it.
     */
    @Test
    void aRepairWithNoReachableEpicFilesNothingRatherThanFoundingOne() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity orphan = task(project);
        orphan.setFeatureId(null);
        orphan.setSourceWishlistId(null);

        service().fileTheMissingWorkAsScope(project, orphan);

        verify(wishlistRepository, never()).save(any());
    }
}
