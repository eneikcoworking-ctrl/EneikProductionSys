package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService.ReviewAdmissionDecision;
import com.eneik.production.services.gate.GateOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test suite enforcing Law 16 (Review Item) from ENGINEERING_PHILOSOPHY_ACTION_PLAN.md:
 * review(τ) ⟹ ∃ r: review artifact belonging to τ.
 *
 * Grounded in Nuel Belnap's four-valued logic (NUEL_BELNAP_03_TRUTH_STATUS_TABLE,
 * NUEL_BELNAP_05_LAMBDA_CORE_REDUCTION, NUEL_BELNAP_01_FALSIFICATION_HARNESS).
 */
public class ReviewAdmissionLaw16Test {

    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final GateOrchestrator gateOrchestrator = mock(GateOrchestrator.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);

    private final ClaimService claimService = new ClaimService(
            claimRepository,
            taskRepository,
            accountRepository,
            julesSessionRepository,
            gateOrchestrator,
            readinessService,
            prReviewRepository
    );

    // =========================================================================
    // 1. Pure Decision Function Tests (NUEL_BELNAP_05_LAMBDA_CORE_REDUCTION)
    // =========================================================================

    @Test
    @DisplayName("Pure evaluator: told-true (artifact present) admits into review")
    void pureDecision_toldTrue_admits() {
        assertEquals(ReviewAdmissionDecision.ADMIT,
                ClaimService.evaluateReviewAdmission(true, true, false));
        assertEquals(ReviewAdmissionDecision.ADMIT,
                ClaimService.evaluateReviewAdmission(true, true, true));
    }

    @Test
    @DisplayName("Pure evaluator: told-neither (session active, no artifact yet) defers admission to protect live work")
    void pureDecision_toldNeither_defers() {
        assertEquals(ReviewAdmissionDecision.DEFER,
                ClaimService.evaluateReviewAdmission(true, false, true));
    }

    @Test
    @DisplayName("Pure evaluator: told-false (session terminal/absent, no artifact) rejects admission")
    void pureDecision_toldFalse_rejects() {
        assertEquals(ReviewAdmissionDecision.REJECT,
                ClaimService.evaluateReviewAdmission(true, false, false));
    }

    @Test
    @DisplayName("Pure evaluator: roles not requiring code are unconditionally admitted")
    void pureDecision_specRole_admits() {
        assertEquals(ReviewAdmissionDecision.ADMIT,
                ClaimService.evaluateReviewAdmission(false, false, false));
        assertEquals(ReviewAdmissionDecision.ADMIT,
                ClaimService.evaluateReviewAdmission(false, false, true));
    }

    // =========================================================================
    // 2. Falsification Harness on ClaimService (NUEL_BELNAP_01_FALSIFICATION_HARNESS)
    // =========================================================================

    private TaskEntity createTask(String roleTag) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.claimed);
        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        task.setRole(role);
        return task;
    }

    private ClaimEntity createActiveClaim(TaskEntity task) {
        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setTask(task);
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        claim.setAccount(account);
        claim.setClaimedAt(Instant.now().minusSeconds(60));
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(task.getId()))
                .thenReturn(Optional.of(claim));
        return claim;
    }

    @Test
    @DisplayName("ClaimService.complete: code-owing task with PR artifact is admitted to review (told-true)")
    void complete_admittedWhenArtifactPresent() {
        TaskEntity task = createTask("BARCAN-TAG-02");
        ClaimEntity claim = createActiveClaim(task);

        when(readinessService.requiresCodeForDelivery(task)).thenReturn(true);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setStatus("pr_opened");
        session.setPrUrl("https://github.com/org/repo/pull/101");
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        doAnswer(inv -> {
            TaskEntity t = inv.getArgument(0);
            t.setQualityGatePassed(true);
            return null;
        }).when(gateOrchestrator).runQualityGate(task);

        claimService.complete(task.getId());

        assertEquals(TaskStatus.review, task.getStatus(), "Task with PR must be admitted to review");
        assertEquals(ClaimResultStatus.done, claim.getResultStatus(), "Implementer claim must be marked done");
        assertNotNull(claim.getReleasedAt(), "Claim lease must be released");
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("ClaimService.complete: code-owing task with PrReviewEntity is admitted to review (told-true)")
    void complete_admittedWhenPrReviewEntityExists() {
        TaskEntity task = createTask("BARCAN-TAG-02");
        ClaimEntity claim = createActiveClaim(task);

        when(readinessService.requiresCodeForDelivery(task)).thenReturn(true);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setStatus("completed");
        session.setPrUrl(null); // URL not set on session itself
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());
        review.setJulesSessionId(session.getId());
        when(prReviewRepository.findByJulesSessionIdIn(List.of(session.getId()))).thenReturn(List.of(review));

        doAnswer(inv -> {
            TaskEntity t = inv.getArgument(0);
            t.setQualityGatePassed(true);
            return null;
        }).when(gateOrchestrator).runQualityGate(task);

        claimService.complete(task.getId());

        assertEquals(TaskStatus.review, task.getStatus());
        assertEquals(ClaimResultStatus.done, claim.getResultStatus());
        assertNotNull(claim.getReleasedAt());
    }

    @Test
    @DisplayName("ClaimService.complete: code-owing task with terminal session and no artifact is rejected to failed (told-false)")
    void complete_rejectedToFailedWhenTerminalWithoutArtifact() {
        TaskEntity task = createTask("BARCAN-TAG-02");
        ClaimEntity claim = createActiveClaim(task);

        when(readinessService.requiresCodeForDelivery(task)).thenReturn(true);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setStatus("failed"); // terminal session
        session.setPrUrl(null);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdIn(List.of(session.getId()))).thenReturn(List.of());

        claimService.complete(task.getId());

        assertEquals(TaskStatus.failed, task.getStatus(),
                "Task without review artifact and with terminal session must transition to failed (state with exits)");
        assertEquals(ClaimResultStatus.failed, claim.getResultStatus());
        assertNotNull(claim.getReleasedAt(), "Claim must be released so account is not held indefinitely");
        assertNotNull(task.getJulesDispatchStatus());
        assertTrue(task.getJulesDispatchStatus().contains("Law 16"),
                "Dispatch status must record Law 16 reasoning (NUEL_BELNAP_08_INSTITUTIONAL_FACT_REGISTER)");
        verify(taskRepository).save(task);
        verify(gateOrchestrator, never()).runQualityGate(any());
    }

    @Test
    @DisplayName("ClaimService.complete: code-owing task without sessions is rejected to failed (told-false)")
    void complete_rejectedToFailedWhenNoSessionsExist() {
        TaskEntity task = createTask("BARCAN-TAG-02");
        ClaimEntity claim = createActiveClaim(task);

        when(readinessService.requiresCodeForDelivery(task)).thenReturn(true);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of());

        claimService.complete(task.getId());

        assertEquals(TaskStatus.failed, task.getStatus());
        assertEquals(ClaimResultStatus.failed, claim.getResultStatus());
        assertNotNull(claim.getReleasedAt());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("ClaimService.complete: code-owing task with active session is deferred (told-neither) - no race fail and no empty review")
    void complete_deferredWhenLiveSessionInProgressWithoutArtifact() {
        TaskEntity task = createTask("BARCAN-TAG-02");
        ClaimEntity claim = createActiveClaim(task);

        when(readinessService.requiresCodeForDelivery(task)).thenReturn(true);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setStatus("running"); // Active session in progress
        session.setPrUrl(null); // PR has not landed yet
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdIn(List.of(session.getId()))).thenReturn(List.of());

        claimService.complete(task.getId());

        // Under Belnap told-neither: do NOT fail, do NOT move to review
        assertEquals(TaskStatus.claimed, task.getStatus(),
                "Task status must remain claimed (deferred), preventing premature exit or empty review");
        assertNull(claim.getReleasedAt(),
                "Claim lease must NOT be released while the session is still actively working");
        verify(taskRepository, never()).save(any());
        verify(gateOrchestrator, never()).runQualityGate(any());
    }

    @Test
    @DisplayName("ClaimService.complete: spec/doc role owing no code is admitted without artifact")
    void complete_specRoleAdmittedWithoutCodeArtifact() {
        TaskEntity task = createTask("BARCAN-TAG-01"); // Spec/architecture role
        ClaimEntity claim = createActiveClaim(task);

        when(readinessService.requiresCodeForDelivery(task)).thenReturn(false);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of());

        doAnswer(inv -> {
            TaskEntity t = inv.getArgument(0);
            t.setQualityGatePassed(true);
            return null;
        }).when(gateOrchestrator).runQualityGate(task);

        claimService.complete(task.getId());

        assertEquals(TaskStatus.review, task.getStatus(), "Spec roles deliver documents, admitted to review");
        assertEquals(ClaimResultStatus.done, claim.getResultStatus());
        assertNotNull(claim.getReleasedAt());
        verify(taskRepository).save(task);
    }
}
