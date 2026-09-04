package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dependent of a dependency nothing will ever resume is a dead end, not a task still waiting.
 *
 * <p>Measured 04.09 on test-fiftieth: the whole project sat in BLOCKED_BY_FAILED_FRONTIER - a globally
 * blocking state - because of exactly one failed task, whose dependency this same service had already
 * refused as work it may never resume. The routing to `blocked` existed on the queued path only: a rule
 * with one consumer enumerated instead of all of them, and the failed path was the other one.
 *
 * <p>The distinction the dependent's fate turns on is asked with the same durable predicate the
 * BLOCKED_BY_FAILED_FRONTIER gate uses, so the gate and the resolver cannot disagree.
 */
class DeadDependencyEndsTheWaitTest {

    @Test
    void aFailedDependencyFromAnOutOfCycleBriefIsDeadForGood() {
        TaskEntity dependency = failedTask();
        dependency.setJulesDispatchStatus("auto-recovery is disabled; dependent task retired");
        WishlistEntity outOfCycle = new WishlistEntity();
        outOfCycle.setSource(WishlistSource.role);

        assertFalse(PlannedWorkRecoveryService.isProductWorkThisMayResume(dependency, outOfCycle),
                "an out-of-cycle brief is not work this service resumes");
    }

    @Test
    void aFailedDependencyThisServiceWillReviveIsNotDeadYet() {
        // The mandatory reverse case. Without it every unsatisfied dependency would look dead and its
        // dependents would be blocked while the recovery was still able to revive them. Resumability turns
        // on the recorded failure reason, not on the status alone.
        TaskEntity dependency = failedTask();
        dependency.setJulesDispatchStatus("auto-recovery is disabled; dependent task retired");
        WishlistEntity clientBrief = new WishlistEntity();
        clientBrief.setSource(WishlistSource.client);

        assertTrue(PlannedWorkRecoveryService.isProductWorkThisMayResume(dependency, clientBrief),
                "a task retired with nothing left working it is exactly what this service revives");
    }

    @Test
    void aFailedDependencyWithAnotherReasonIsNotRevivedHere() {
        // And this is why the dependent cannot wait: the dependency will not leave `failed` through this
        // service, and a repair for it arrives as new work, not as its revival.
        TaskEntity dependency = failedTask();
        dependency.setJulesDispatchStatus("the agent reported a merge conflict it could not resolve");
        WishlistEntity clientBrief = new WishlistEntity();
        clientBrief.setSource(WishlistSource.client);

        assertFalse(PlannedWorkRecoveryService.isProductWorkThisMayResume(dependency, clientBrief));
    }

    @Test
    void aDependencyThatHasNotFailedIsNeverDeadForGood() {
        TaskEntity queued = failedTask();
        queued.setStatus(TaskStatus.queued);
        WishlistEntity clientBrief = new WishlistEntity();
        clientBrief.setSource(WishlistSource.client);

        assertFalse(PlannedWorkRecoveryService.isProductWorkThisMayResume(queued, clientBrief));
    }

    @Test
    void aResumableDependencyWhoseOnlyResumeIsSpentIsDeadForGood() {
        // The case that held the live circuit. It IS work this service resumes, so the durable predicate
        // says yes - and its single automatic resume is already gone, so nothing will ever act on that yes.
        // Measured 04.09: the dependency holding the whole project appeared in no refusal bucket at all,
        // which by this class's own filter means exactly that its budget was spent.
        TaskEntity dependency = resumableDependency();
        dependency.setPayload(new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode().put("ems_bounded_plan_resume_count", 1));

        assertTrue(service().isDeadForGood(dependency),
                "a resumable dependency past its only resume still ends the wait");
    }

    @Test
    void aDependencyWithItsResumeStillUnspentIsWorthWaitingFor() {
        // The mandatory reverse case: a dependency that can still be revived must not end its dependent.
        assertFalse(service().isDeadForGood(resumableDependency()));
    }

    private TaskEntity resumableDependency() {
        TaskEntity dependency = failedTask();
        dependency.setSourceWishlistId(java.util.UUID.randomUUID());
        dependency.setJulesDispatchStatus("auto-recovery is disabled; dependent task retired");
        return dependency;
    }

    private PlannedWorkRecoveryService service() {
        WishlistRepository wishlistRepository = org.mockito.Mockito.mock(WishlistRepository.class);
        WishlistEntity clientBrief = new WishlistEntity();
        clientBrief.setSource(WishlistSource.client);
        org.mockito.Mockito.when(wishlistRepository.findById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(clientBrief));
        return new PlannedWorkRecoveryService(
                org.mockito.Mockito.mock(com.eneik.production.repositories.TaskRepository.class),
                wishlistRepository,
                org.mockito.Mockito.mock(com.eneik.production.repositories.JulesSessionRepository.class),
                org.mockito.Mockito.mock(ClaimService.class),
                org.mockito.Mockito.mock(ClientDeliverableReadinessService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private TaskEntity failedTask() {
        TaskEntity task = new TaskEntity();
        task.setId(java.util.UUID.randomUUID());
        task.setStatus(TaskStatus.failed);
        task.setFeatureId(java.util.UUID.randomUUID());
        return task;
    }
}
