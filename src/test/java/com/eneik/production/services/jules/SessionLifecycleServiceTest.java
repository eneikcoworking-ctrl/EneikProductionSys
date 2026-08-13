package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Confirmed live against Jules's real API (2026-08-01) that DELETE /v1alpha/sessions/{session} genuinely
 * removes a session server-side - this tests the service that makes that the single, consistent choke
 * point for "this session is done", replacing what used to be purely local bookkeeping.
 */
class SessionLifecycleServiceTest {

    private JulesSessionRepository julesSessionRepository;
    private AccountRepository accountRepository;
    private TaskRepository taskRepository;
    private JulesApiClient julesApiClient;
    private SessionLifecycleService service;

    @BeforeEach
    void setUp() {
        julesSessionRepository = mock(JulesSessionRepository.class);
        accountRepository = mock(AccountRepository.class);
        taskRepository = mock(TaskRepository.class);
        julesApiClient = mock(JulesApiClient.class);
        service = new SessionLifecycleService(julesSessionRepository, accountRepository, taskRepository, julesApiClient, null);
        // 2026-08-14 (bug-hunt sweep): applyLocalCancelStatus/prepareRemoteDeleteContext/recordRemoteDeleted
        // are called via self (REQUIRES_NEW proxy dispatch) - wired to the instance itself here, same as
        // JulesDispatchServiceTest.self, since there's no real Spring proxy in a plain unit test.
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "cleanupBatchSize", 30);
        when(julesSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private JulesSessionEntity session(String status, String externalId, UUID accountId) {
        JulesSessionEntity s = new JulesSessionEntity();
        s.setId(UUID.randomUUID());
        s.setStatus(status);
        s.setExternalSessionId(externalId);
        s.setAccountId(accountId);
        return s;
    }

    private AccountEntity account(UUID id, String apiKey) {
        AccountEntity a = new AccountEntity();
        a.setId(id);
        a.setApiKey(apiKey);
        return a;
    }

    @Test
    void retireSessionOnlyMarksLocalCancelledAndDeletesRemotely() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity s = session("pr_opened", "sessions/real-123", accountId);
        when(julesSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, "real-key")));
        when(julesApiClient.deleteSession("sessions/real-123", "real-key"))
                .thenReturn(new JulesApiClient.DeleteSessionResult(true, 200, "{}"));

        service.retireSessionOnly(s.getId(), "test reason");

        assertEquals("cancelled", s.getStatus());
        assertNotNull(s.getClosedAt());
        assertNotNull(s.getRemoteDeletedAt());
    }

    @Test
    void retireSessionOnlyTreats404AsConfirmedDeletion() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity s = session("stuck", "sessions/already-gone", accountId);
        when(julesSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, "real-key")));
        when(julesApiClient.deleteSession("sessions/already-gone", "real-key"))
                .thenReturn(new JulesApiClient.DeleteSessionResult(false, 404, "not found"));

        service.retireSessionOnly(s.getId(), "test reason");

        assertNotNull(s.getRemoteDeletedAt(), "a 404 is equally good evidence of 'really gone' as a fresh 200");
    }

    @Test
    void retireSessionOnlyLeavesRemoteDeletedAtNullOnRealFailure() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity s = session("stuck", "sessions/wont-delete", accountId);
        when(julesSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, "real-key")));
        when(julesApiClient.deleteSession("sessions/wont-delete", "real-key"))
                .thenReturn(new JulesApiClient.DeleteSessionResult(false, 500, "server error"));

        service.retireSessionOnly(s.getId(), "test reason");

        assertEquals("cancelled", s.getStatus(), "local retirement still happens even if the remote call fails");
        assertNull(s.getRemoteDeletedAt(), "must not claim a deletion that didn't actually happen");
    }

    @Test
    void retireSessionOnlyDoesNotReDeleteWhenAlreadyConfirmedGone() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity s = session("cancelled", "sessions/already-deleted", accountId);
        s.setRemoteDeletedAt(java.time.Instant.now().minusSeconds(3600));
        when(julesSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));

        service.retireSessionOnly(s.getId(), "test reason");

        verify(julesApiClient, never()).deleteSession(any(), any());
    }

    private TaskEntity task(UUID id, TaskStatus status, ProjectEntity project) {
        TaskEntity t = new TaskEntity();
        t.setId(id);
        t.setStatus(status);
        t.setProject(project);
        return t;
    }

    private ProjectEntity project(ProjectStatus status) {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setStatus(status);
        return p;
    }

    @Test
    void cleanupProcessesSessionWhoseTaskIsTerminal() {
        UUID accountId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        JulesSessionEntity s = session("closed_terminal_task", "sessions/done-task", accountId);
        s.setTaskId(taskId);
        when(julesSessionRepository.findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull()).thenReturn(List.of(s));
        // 2026-08-14 (bug-hunt sweep): deleteRemote now re-fetches the session by id inside its own
        // short REQUIRES_NEW spans (see SessionLifecycleService's javadoc on retireSessionOnly), instead of
        // reusing the entity instance this cleanup loop already holds - real DB, real find; this mock must
        // answer the same lookup.
        when(julesSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task(taskId, TaskStatus.done, project(ProjectStatus.active))));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, "k")));
        when(julesApiClient.deleteSession(any(), any())).thenReturn(new JulesApiClient.DeleteSessionResult(true, 200, "{}"));

        int processed = service.cleanupEligibleSessions();

        assertEquals(1, processed);
        verify(julesApiClient, times(1)).deleteSession(eq("sessions/done-task"), eq("k"));
    }

    @Test
    void cleanupProcessesSessionWhoseProjectIsClosedEvenIfTaskIsNotTerminal() {
        UUID accountId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        JulesSessionEntity s = session("cancelled", "sessions/closed-project", accountId);
        s.setTaskId(taskId);
        when(julesSessionRepository.findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull()).thenReturn(List.of(s));
        when(julesSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task(taskId, TaskStatus.queued, project(ProjectStatus.frozen))));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, "k")));
        when(julesApiClient.deleteSession(any(), any())).thenReturn(new JulesApiClient.DeleteSessionResult(true, 200, "{}"));

        int processed = service.cleanupEligibleSessions();

        assertEquals(1, processed);
        verify(julesApiClient, times(1)).deleteSession(eq("sessions/closed-project"), eq("k"));
    }

    @Test
    void cleanupSkipsSessionWhenNeitherTriggerApplies() {
        UUID accountId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        JulesSessionEntity s = session("running", "sessions/still-live", accountId);
        s.setTaskId(taskId);
        when(julesSessionRepository.findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull()).thenReturn(List.of(s));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task(taskId, TaskStatus.claimed, project(ProjectStatus.active))));

        int processed = service.cleanupEligibleSessions();

        assertEquals(0, processed);
        verify(julesApiClient, never()).deleteSession(any(), any());
    }

    @Test
    void cleanupRespectsBatchLimit() {
        java.util.List<JulesSessionEntity> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID accountId = UUID.randomUUID();
            UUID taskId = UUID.randomUUID();
            JulesSessionEntity s = session("closed_terminal_task", "sessions/batch-" + i, accountId);
            s.setTaskId(taskId);
            candidates.add(s);
            when(julesSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
            when(taskRepository.findById(taskId)).thenReturn(Optional.of(task(taskId, TaskStatus.done, project(ProjectStatus.active))));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, "k")));
        }
        when(julesSessionRepository.findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull()).thenReturn(candidates);
        when(julesApiClient.deleteSession(any(), any())).thenReturn(new JulesApiClient.DeleteSessionResult(true, 200, "{}"));
        ReflectionTestUtils.setField(service, "cleanupBatchSize", 2);

        int processed = service.cleanupEligibleSessions();

        assertEquals(2, processed);
    }
}
