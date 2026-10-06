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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
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
 * Stage 4 Popperian falsification harness for SessionLifecycleService.
 *
 * Grounded in:
 * 1. MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK [D004, Gilbert]: shared contract over remote session life;
 *    local cancel status is not a fiction that fakes remote deletion; confirmed remote deletion locks out redundant calls.
 * 2. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]: normative prohibition as code; missing API key or skipped external ID
 *    strictly prohibits external network I/O; DB transaction is isolated from external HTTP call.
 * 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]: reliabilism of epistemic processes; remoteDeletedAt is recorded
 *    only upon verified causal evidence (HTTP 200 or 404); active tasks in active projects are protected from premature sweep.
 */
class SessionLifecycleServiceFalsificationTest {

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

        // Self-injected proxy wired to instance for unit falsification test
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "cleanupBatchSize", 30);
        when(julesSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private JulesSessionEntity createSession(String status, String externalId, UUID accountId) {
        JulesSessionEntity s = new JulesSessionEntity();
        s.setId(UUID.randomUUID());
        s.setStatus(status);
        s.setExternalSessionId(externalId);
        s.setAccountId(accountId);
        return s;
    }

    private AccountEntity createAccount(UUID id, String apiKey) {
        AccountEntity a = new AccountEntity();
        a.setId(id);
        a.setApiKey(apiKey);
        return a;
    }

    private TaskEntity createTask(UUID id, TaskStatus status, ProjectEntity project) {
        TaskEntity t = new TaskEntity();
        t.setId(id);
        t.setStatus(status);
        t.setProject(project);
        return t;
    }

    private ProjectEntity createProject(ProjectStatus status) {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setStatus(status);
        return p;
    }

    // --- MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK (D004) ---

    @Test
    @DisplayName("Gilbert: Local cancellation does not forge remote deletion timestamp when Jules API call fails")
    void falsifyGilbertJointCommitment_localCancellationDoesNotForgeRemoteDeletionOnTransportFailure() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity session = createSession("running", "sessions/remote-fails", accountId);
        when(julesSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(createAccount(accountId, "valid-key")));
        // Remote Jules call returns HTTP 500 error
        when(julesApiClient.deleteSession("sessions/remote-fails", "valid-key"))
                .thenReturn(new JulesApiClient.DeleteSessionResult(false, 500, "Internal Server Error"));

        service.retireSessionOnly(session.getId(), "falsification test failure");

        // Local status becomes cancelled, but remoteDeletedAt MUST NOT be claimed
        assertEquals("cancelled", session.getStatus());
        assertNotNull(session.getClosedAt());
        assertEquals("falsification test failure", session.getClosureReason());
        assertNull(session.getRemoteDeletedAt(), "Joint commitment violated if system claims remote deletion on 500 failure");
    }

    @Test
    @DisplayName("Gilbert: Already confirmed remote deletion locks out redundant network calls to Jules API")
    void falsifyGilbertJointCommitment_alreadyConfirmedDeletionLocksOutRedundantCalls() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity session = createSession("cancelled", "sessions/already-gone", accountId);
        session.setRemoteDeletedAt(Instant.now().minusSeconds(120));
        when(julesSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        service.retireSessionOnly(session.getId(), "duplicate retirement invocation");

        // Remote deletion must never be called again if remoteDeletedAt is already verified
        verify(julesApiClient, never()).deleteSession(any(), any());
    }

    // --- DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006) ---

    @Test
    @DisplayName("Raz: Missing API key or skipped external session ID strictly prohibits network transport call")
    void falsifyRazProhibition_missingApiKeyOrSkippedSessionProhibitsNetworkTransport() {
        UUID accountId1 = UUID.randomUUID();
        JulesSessionEntity skippedSession = createSession("running", "skipped", accountId1);
        when(julesSessionRepository.findById(skippedSession.getId())).thenReturn(Optional.of(skippedSession));
        when(accountRepository.findById(accountId1)).thenReturn(Optional.of(createAccount(accountId1, "valid-key")));

        service.retireSessionOnly(skippedSession.getId(), "skipped session retirement");
        verify(julesApiClient, never()).deleteSession(any(), any());

        UUID accountId2 = UUID.randomUUID();
        JulesSessionEntity blankKeySession = createSession("running", "sessions/real-session", accountId2);
        when(julesSessionRepository.findById(blankKeySession.getId())).thenReturn(Optional.of(blankKeySession));
        when(accountRepository.findById(accountId2)).thenReturn(Optional.of(createAccount(accountId2, "   ")));

        service.retireSessionOnly(blankKeySession.getId(), "blank api key retirement");
        verify(julesApiClient, never()).deleteSession(any(), any());
    }

    @Test
    @DisplayName("Raz: Null external session ID or non-existent account entity strictly prohibits network transport call")
    void falsifyRazProhibition_nullExternalSessionOrNonExistentAccountProhibitsNetworkTransport() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity nullExtSession = createSession("running", null, accountId);
        when(julesSessionRepository.findById(nullExtSession.getId())).thenReturn(Optional.of(nullExtSession));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(createAccount(accountId, "valid-key")));

        service.retireSessionOnly(nullExtSession.getId(), "null external session");
        verify(julesApiClient, never()).deleteSession(any(), any());

        UUID missingAccountId = UUID.randomUUID();
        JulesSessionEntity missingAccountSession = createSession("running", "sessions/valid-session", missingAccountId);
        when(julesSessionRepository.findById(missingAccountSession.getId())).thenReturn(Optional.of(missingAccountSession));
        when(accountRepository.findById(missingAccountId)).thenReturn(Optional.empty());

        service.retireSessionOnly(missingAccountSession.getId(), "missing account entity");
        verify(julesApiClient, never()).deleteSession(any(), any());
    }

    // --- ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010) ---

    @Test
    @DisplayName("Goldman: HTTP 404 response is audited as valid causal evidence of absence on external server")
    void falsifyGoldmanReliabilityChain_http404RecognizedAsCausalEvidenceOfAbsence() {
        UUID accountId = UUID.randomUUID();
        JulesSessionEntity session = createSession("running", "sessions/already-expired-404", accountId);
        when(julesSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(createAccount(accountId, "key-404")));
        when(julesApiClient.deleteSession("sessions/already-expired-404", "key-404"))
                .thenReturn(new JulesApiClient.DeleteSessionResult(false, 404, "Not Found"));

        service.retireSessionOnly(session.getId(), "audit 404 absence");

        // Verified absence is reliable evidence of deletion
        assertEquals("cancelled", session.getStatus());
        assertNotNull(session.getRemoteDeletedAt(), "Reliable process audit: HTTP 404 is valid evidence of remote deletion");
    }

    @Test
    @DisplayName("Goldman: Active tasks in active projects are protected from premature periodic sweep; terminal statuses trigger sweep")
    void falsifyGoldmanReliabilityChain_activeTaskInActiveProjectNeverSweptPrematurely() {
        UUID accountId = UUID.randomUUID();
        UUID liveTaskId = UUID.randomUUID();
        UUID spikeTaskId = UUID.randomUUID();

        // 1. Live session: task claimed in active project -> MUST NOT BE SWEPT
        JulesSessionEntity liveSession = createSession("running", "sessions/live-work", accountId);
        liveSession.setTaskId(liveTaskId);
        TaskEntity liveTask = createTask(liveTaskId, TaskStatus.claimed, createProject(ProjectStatus.active));

        // 2. Terminal spike session: task spike_completed in active project -> MUST BE SWEPT
        JulesSessionEntity spikeSession = createSession("running", "sessions/spike-work", accountId);
        spikeSession.setTaskId(spikeTaskId);
        TaskEntity spikeTask = createTask(spikeTaskId, TaskStatus.spike_completed, createProject(ProjectStatus.active));

        when(julesSessionRepository.findByRemoteDeletedAtIsNullAndExternalSessionIdIsNotNull())
                .thenReturn(List.of(liveSession, spikeSession));
        when(julesSessionRepository.findById(liveSession.getId())).thenReturn(Optional.of(liveSession));
        when(julesSessionRepository.findById(spikeSession.getId())).thenReturn(Optional.of(spikeSession));
        when(taskRepository.findById(liveTaskId)).thenReturn(Optional.of(liveTask));
        when(taskRepository.findById(spikeTaskId)).thenReturn(Optional.of(spikeTask));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(createAccount(accountId, "valid-key")));
        when(julesApiClient.deleteSession(eq("sessions/spike-work"), any()))
                .thenReturn(new JulesApiClient.DeleteSessionResult(true, 200, "{}"));

        int processed = service.cleanupEligibleSessions();

        assertEquals(1, processed, "Only terminal task must be processed");
        verify(julesApiClient, never()).deleteSession(eq("sessions/live-work"), any());
        verify(julesApiClient, times(1)).deleteSession(eq("sessions/spike-work"), eq("valid-key"));
    }
}
