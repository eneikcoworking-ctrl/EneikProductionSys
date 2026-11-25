package com.eneik.production.services.worker;

import com.eneik.production.controllers.InternalJulesActivitiesProbeController;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.accounts.AccountHealthService;
import com.eneik.production.services.jules.JulesApiClient;
import com.eneik.production.services.jules.SessionLifecycleService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.agy.LocalAgyWorktreeExecutionEngine;
import com.eneik.production.services.worker.deepseek.DeepSeekQuotaExhaustedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Popperian falsification harness for WO #13 (Mechanism #15):
 * Complete decommissioning of Google Jules from the execution chain.
 *
 * Grounded in:
 * - ELVIN_GOLDMAN_06_CAUSAL_PROCESS_TRACE (D013): zero causal paths reach Google Jules API.
 * - DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006): eneik.jules.enabled=false cannot be bypassed via UI/DB.
 * - KARL_POPPER_01_FALSIFICATION_HARNESS (D008): falsifiable barriers on router, API client, schedulers, V147 pool.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JulesDecommissioningFalsificationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JulesSessionRepository julesSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private JulesApiClient julesApiClient;

    @Autowired
    private SessionLifecycleService sessionLifecycleService;

    @Autowired
    private InternalJulesActivitiesProbeController probeController;

    @Autowired
    private AccountHealthService accountHealthService;

    // --- Заслон 13-1: Router Never Picks Jules & Preserves DeepSeek -> LOCAL_AGY ---

    @Test
    @DisplayName("Barrier 13-1: WorkerExecutionRouter never calls JulesExecutionEngine in any mode and preserves DeepSeek -> LOCAL_AGY order")
    void routerNeverCallsJulesInAnyModeAndPreservesSovereignOrder() {
        DeepSeekWorktreeExecutionEngine mockDeepSeek = mock(DeepSeekWorktreeExecutionEngine.class);
        JulesExecutionEngine mockJules = mock(JulesExecutionEngine.class);
        LocalAgyWorktreeExecutionEngine mockLocalAgy = mock(LocalAgyWorktreeExecutionEngine.class);
        SystemSettingsService mockSettings = mock(SystemSettingsService.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        JulesSessionRepository mockSessionRepo = mock(JulesSessionRepository.class);

        WorkerExecutionRouter router = new WorkerExecutionRouter(
                mockDeepSeek, mockJules, mockLocalAgy, mockSettings, mockAccountRepo, mockSessionRepo);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        // 1. Mode = JULES -> explicit GOOGLE_JULES_DECOMMISSIONED failure, never invokes julesEngine
        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("JULES");
        assertNull(router.resolveEngine(task));
        WorkerDispatchResult julesModeRes = router.dispatch(task, null, "IMPLEMENTER");
        assertFalse(julesModeRes.success());
        assertTrue(julesModeRes.errorMessage().contains("GOOGLE_JULES_DECOMMISSIONED"));

        // 2. Mode = HYBRID, DeepSeek fails with 429, LOCAL_AGY also fails -> honest failure, never invokes julesEngine
        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("HYBRID");
        when(mockSettings.effectiveValue("deepseek_api_key")).thenReturn("sk-test-key");
        when(mockAccountRepo.findAll()).thenReturn(List.of());
        when(mockDeepSeek.dispatch(any(), any(), any()))
                .thenThrow(new DeepSeekQuotaExhaustedException("HTTP 429", 429));
        when(mockLocalAgy.dispatch(any(), any(), any()))
                .thenReturn(WorkerDispatchResult.failed("local agy busy", WorkerEngineType.LOCAL_AGY));

        WorkerDispatchResult bothFailedRes = router.dispatch(task, null, "IMPLEMENTER");
        assertFalse(bothFailedRes.success());
        assertTrue(bothFailedRes.errorMessage().contains("NO_SOVEREIGN_WORKER_AVAILABLE"));

        // 3. Mode = HYBRID, DeepSeek succeeds -> DeepSeek is Tier 1 before LOCAL_AGY
        reset(mockDeepSeek, mockLocalAgy);
        when(mockDeepSeek.dispatch(any(), any(), any()))
                .thenReturn(WorkerDispatchResult.ok(UUID.randomUUID(), "deepseek-1", WorkerEngineType.DEEPSEEK_WORKTREE, null));
        WorkerDispatchResult dsRes = router.dispatch(task, null, "IMPLEMENTER");
        assertTrue(dsRes.success());
        assertEquals(WorkerEngineType.DEEPSEEK_WORKTREE, dsRes.engineType());
        verify(mockLocalAgy, never()).dispatch(any(), any(), any());

        // 4. Mode = LOCAL_AGY with null bean -> fails cleanly, never invokes julesEngine
        WorkerExecutionRouter routerWithoutAgy = new WorkerExecutionRouter(
                mockDeepSeek, mockJules, null, mockSettings, mockAccountRepo, mockSessionRepo);
        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("LOCAL_AGY");
        WorkerDispatchResult missingAgyRes = routerWithoutAgy.dispatch(task, null, "IMPLEMENTER");
        assertFalse(missingAgyRes.success());
        assertTrue(missingAgyRes.errorMessage().contains("NO_SOVEREIGN_WORKER_AVAILABLE"));

        // 5. Poll / cancel for legacy Jules session -> never touches julesEngine
        UUID legacySessionId = UUID.randomUUID();
        JulesSessionEntity legacySession = new JulesSessionEntity();
        legacySession.setId(legacySessionId);
        legacySession.setWorkerEngine("JULES");
        when(mockSessionRepo.findById(legacySessionId)).thenReturn(Optional.of(legacySession));
        WorkerPollResult pollRes = router.poll(legacySessionId);
        assertEquals("GOOGLE_JULES_DECOMMISSIONED", pollRes.errorMessage());
        router.cancel(legacySessionId, "test");

        verifyNoInteractions(mockJules);
    }

    // --- Заслон 13-2: Zero Jules API Calls & No UI Re-enable ---

    @Test
    @DisplayName("Barrier 13-2: SystemSettings blocks UI re-enable of jules_enabled even if DB has true; JulesApiClient, SessionLifecycleService, and ProbeController make zero outbound calls")
    void zeroJulesApiCallsAndNoUiReenable() {
        // Force DB row jules_enabled = 'true' directly via SQL to simulate legacy DB state
        int updated = jdbcTemplate.update("UPDATE system_settings SET \"value\" = 'true' WHERE \"key\" = 'jules_enabled'");
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO system_settings (\"key\", \"value\", updated_at) VALUES ('jules_enabled', 'true', CURRENT_TIMESTAMP)");
        }

        // SystemSettingsService MUST still return false and reject save("jules_enabled", "true")
        assertFalse(systemSettingsService.effectiveBoolean("jules_enabled"),
                "jules_enabled must remain false when eneik.jules.enabled=false even if DB contains 'true'");
        assertThrows(IllegalArgumentException.class,
                () -> systemSettingsService.save("jules_enabled", "true"),
                "API/UI must not be allowed to re-enable jules_enabled");

        // JulesApiClient pointing to unreachable invalid port proves 0 HTTP calls happen
        MockEnvironment env = new MockEnvironment().withProperty("eneik.jules.enabled", "false");
        SystemSettingsService isolatedSettings = new SystemSettingsService(jdbcTemplate, env);
        JulesApiClient isolatedClient = new JulesApiClient(new ObjectMapper(), "http://127.0.0.1:1/v1alpha", isolatedSettings);
        assertFalse(isolatedClient.isEnabled());

        JulesApiClient.CreateSessionResult createRes = isolatedClient.createSessionDetailed(
                "https://github.com/org/repo", "desc", "ctx", "fake-key", "title", "main");
        assertEquals("skipped", createRes.sessionName());
        assertEquals("jules_disabled", createRes.errorBody());
        assertNull(isolatedClient.getSessionStatus("sessions/123", "fake-key"));
        assertNull(isolatedClient.getSessionPrUrl("sessions/123", "fake-key"));
        assertNull(isolatedClient.getSessionActivities("sessions/123", "fake-key", 5, null));
        assertFalse(isolatedClient.sendMessage("sessions/123", "hi", "fake-key"));
        assertEquals(410, isolatedClient.checkSessionRaw("sessions/123", "fake-key").statusCode());
        assertFalse(isolatedClient.deleteSession("sessions/123", "fake-key").success());

        // Spring-wired JulesApiClient is also disabled
        assertFalse(julesApiClient.isEnabled());

        // SessionLifecycleService: cleanupEligibleSessions is a no-op; retireSessionOnly cancels locally with 0 remote calls
        JulesSessionRepository mockSessRepo = mock(JulesSessionRepository.class);
        AccountRepository mockAccRepo = mock(AccountRepository.class);
        TaskRepository mockTaskRepo = mock(TaskRepository.class);
        JulesApiClient mockApiClient = mock(JulesApiClient.class);
        SessionLifecycleService isolatedLifecycle = new SessionLifecycleService(
                mockSessRepo, mockAccRepo, mockTaskRepo, mockApiClient, null);
        ReflectionTestUtils.setField(isolatedLifecycle, "self", isolatedLifecycle);
        ReflectionTestUtils.setField(isolatedLifecycle, "cleanupBatchSize", 30);

        JulesSessionEntity s = new JulesSessionEntity();
        s.setId(UUID.randomUUID());
        s.setStatus("running");
        s.setExternalSessionId("sessions/legacy-999");
        s.setAccountId(UUID.randomUUID());
        when(mockSessRepo.findById(s.getId())).thenReturn(Optional.of(s));
        when(mockSessRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(0, isolatedLifecycle.cleanupEligibleSessions());
        isolatedLifecycle.retireSessionOnly(s.getId(), "local retirement");
        assertEquals("cancelled", s.getStatus());
        verifyNoInteractions(mockApiClient);

        // Spring-wired SessionLifecycleService returns 0 on cleanup
        assertEquals(0, sessionLifecycleService.cleanupEligibleSessions());

        // ProbeController returns 410 Gone
        ResponseEntity<?> probeResp = probeController.probe("sessions/legacy-999", 5, null);
        assertEquals(410, probeResp.getStatusCode().value());
    }

    // --- Заслон 13-3: Account Pool & V147 Integrity (remotecli -> LOCAL_AGY, GOOGLE_JULES excluded, history preserved) ---

    @Test
    @DisplayName("Barrier 13-3: V147 retags remotecli* to LOCAL_AGY, decommissions GOOGLE_JULES without deleting rows, and excludes GOOGLE_JULES from all AccountRepository selectors and AccountHealthService")
    void accountPoolAndV147Integrity() {
        // Seed a simulated pre-V147 remotecli profile and a legacy GOOGLE_JULES cloud account,
        // then re-run the exact V147 SQL statements to verify idempotent behavior on live data shape.
        UUID remoteCliId = UUID.randomUUID();
        UUID cloudJulesId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO accounts (id, name, provider, status, enabled, capabilities, api_key, max_concurrent_sessions, created_at) " +
                "VALUES (?, 'remotecli9', 'GOOGLE_JULES', 'idle', TRUE, '*', 'local-agy-token', 3, CURRENT_TIMESTAMP)",
                remoteCliId);
        jdbcTemplate.update(
                "INSERT INTO accounts (id, name, provider, status, enabled, capabilities, api_key, max_concurrent_sessions, status_changed_at, created_at) " +
                "VALUES (?, 'eneikcoworking-ctrl-legacy', 'GOOGLE_JULES', 'idle', TRUE, '*', 'AIza-legacy-jules-key', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                cloudJulesId);

        // Execute V147 SQL transformation
        jdbcTemplate.update(
                "UPDATE accounts SET provider = 'LOCAL_AGY' " +
                "WHERE LOWER(name) LIKE 'remotecli%' AND UPPER(COALESCE(provider, 'GOOGLE_JULES')) = 'GOOGLE_JULES'");
        jdbcTemplate.update(
                "UPDATE accounts SET status = 'decommissioned', enabled = FALSE, status_changed_at = CURRENT_TIMESTAMP " +
                "WHERE UPPER(COALESCE(provider, 'GOOGLE_JULES')) = 'GOOGLE_JULES'");

        // 1. Both rows still exist (0 rows deleted — history preserved)
        AccountEntity remoteCli = accountRepository.findById(remoteCliId).orElseThrow();
        AccountEntity cloudJules = accountRepository.findById(cloudJulesId).orElseThrow();

        assertEquals("LOCAL_AGY", remoteCli.getProvider(), "remotecli* must be retagged to LOCAL_AGY");
        assertTrue(remoteCli.isEnabled());
        assertEquals(AccountStatus.idle, remoteCli.getStatus());

        assertEquals("GOOGLE_JULES", cloudJules.getProvider());
        assertFalse(cloudJules.isEnabled(), "cloud GOOGLE_JULES account must be disabled");
        assertEquals(AccountStatus.decommissioned, cloudJules.getStatus(), "cloud GOOGLE_JULES account must be decommissioned");

        // 2. Even if someone manually flips a GOOGLE_JULES account back to idle/enabled=true,
        //    AccountRepository selectors MUST NEVER pick it!
        UUID strayJulesId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO accounts (id, name, provider, status, enabled, capabilities, api_key, max_concurrent_sessions, created_at) " +
                "VALUES (?, 'stray-jules-account', 'GOOGLE_JULES', 'idle', TRUE, '*', 'AIza-stray', 3, CURRENT_TIMESTAMP)",
                strayJulesId);

        List<AccountEntity> availableForProject = accountRepository.findAvailableForProjectOrderByNameAsc(projectId);
        assertTrue(availableForProject.stream().noneMatch(a -> "GOOGLE_JULES".equalsIgnoreCase(a.getProvider())),
                "findAvailableForProjectOrderByNameAsc must exclude GOOGLE_JULES");
        assertTrue(availableForProject.stream().anyMatch(a -> remoteCliId.equals(a.getId())),
                "remotecli9 (LOCAL_AGY) must remain selectable");

        // Verify lockNext* selectors never return GOOGLE_JULES
        Optional<AccountEntity> lockedIdle = accountRepository.lockNextIdleAccountForProject(projectId);
        assertTrue(lockedIdle.isPresent());
        assertNotEquals("GOOGLE_JULES", lockedIdle.get().getProvider());

        Optional<AccountEntity> lockedCap = accountRepository.lockNextIdleAccountForProjectAndCapability(projectId, "BARCAN-TAG-02");
        assertTrue(lockedCap.isPresent());
        assertNotEquals("GOOGLE_JULES", lockedCap.get().getProvider());

        Optional<AccountEntity> lockedWithCap = accountRepository.lockNextJulesAccountWithCapacity(
                projectId, "BARCAN-TAG-02", 3, null, 15, null);
        assertTrue(lockedWithCap.isPresent());
        assertNotEquals("GOOGLE_JULES", lockedWithCap.get().getProvider());

        // 3. AccountHealthService ignores GOOGLE_JULES accounts during recovery sweeps
        AccountEntity strayBlockedJules = accountRepository.findById(strayJulesId).orElseThrow();
        strayBlockedJules.setStatus(AccountStatus.api_blocked);
        strayBlockedJules.setStatusChangedAt(Instant.now().minus(10, ChronoUnit.HOURS));
        accountRepository.saveAndFlush(strayBlockedJules);

        accountHealthService.recoverEligibleAccounts(Instant.now());
        AccountEntity afterSweep = accountRepository.findById(strayJulesId).orElseThrow();
        assertEquals(AccountStatus.api_blocked, afterSweep.getStatus(),
                "AccountHealthService must not recover GOOGLE_JULES accounts");
    }
}
