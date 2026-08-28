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

    private DeliveryRealityProducerService service() {
        return new DeliveryRealityProducerService(
                mock(ProjectRepository.class),
                mock(TaskRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(OperationalRealityFindingRepository.class),
                mock(EvidenceNodeRepository.class),
                wishlistRepository);
    }

    private TaskEntity task(ProjectEntity project) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setTitle("Runtime Contract 9b58412d");
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
}
