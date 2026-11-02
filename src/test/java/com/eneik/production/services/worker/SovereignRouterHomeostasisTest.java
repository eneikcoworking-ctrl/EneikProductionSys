package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.agy.LocalAgyWorktreeExecutionEngine;
import com.eneik.production.services.worker.deepseek.DeepSeekQuotaExhaustedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SovereignRouterHomeostasisTest {

    private DeepSeekWorktreeExecutionEngine mockDeepSeek;
    private JulesExecutionEngine mockJules;
    private LocalAgyWorktreeExecutionEngine mockLocalAgy;
    private SystemSettingsService mockSettings;
    private AccountRepository mockAccountRepo;
    private JulesSessionRepository mockSessionRepo;

    private TaskEntity task;

    @BeforeEach
    void setUp() {
        mockDeepSeek = mock(DeepSeekWorktreeExecutionEngine.class);
        mockJules = mock(JulesExecutionEngine.class);
        mockLocalAgy = mock(LocalAgyWorktreeExecutionEngine.class);
        mockSettings = mock(SystemSettingsService.class);
        mockAccountRepo = mock(AccountRepository.class);
        mockSessionRepo = mock(JulesSessionRepository.class);

        task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Sovereign Homeostasis Task");
    }

    @Test
    @DisplayName("Cranes Homeostasis: on DeepSeek 402/429 error, marks account blocked and fails over to LOCAL_AGY")
    void testDeepSeekFailsOverToLocalAgy() {
        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("HYBRID");

        AccountEntity deepSeekAccount = new AccountEntity();
        deepSeekAccount.setName("deepseek-primary");
        deepSeekAccount.setProvider("DEEPSEEK");
        deepSeekAccount.setStatus(AccountStatus.idle);
        deepSeekAccount.setEnabled(true);
        deepSeekAccount.setApiKey("sk-quota-exhausted-key");
        when(mockAccountRepo.findAll()).thenReturn(List.of(deepSeekAccount));

        // DeepSeek throws 402 Insufficient Balance
        when(mockDeepSeek.dispatch(any(), any(), any()))
                .thenThrow(new DeepSeekQuotaExhaustedException("HTTP 402 Insufficient Balance", 402));

        // LOCAL_AGY succeeds
        WorkerDispatchResult agySuccess = WorkerDispatchResult.ok(UUID.randomUUID(), "agy-session-1", WorkerEngineType.LOCAL_AGY, "https://github.com/org/repo/pull/201");
        when(mockLocalAgy.dispatch(any(), any(), any())).thenReturn(agySuccess);

        WorkerExecutionRouter router = new WorkerExecutionRouter(
                mockDeepSeek,
                mockJules,
                mockLocalAgy,
                mockSettings,
                mockAccountRepo,
                mockSessionRepo
        );

        WorkerDispatchResult result = router.dispatch(task, deepSeekAccount, "IMPLEMENTER");

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(WorkerEngineType.LOCAL_AGY, result.engineType());

        // Account is marked api_blocked
        assertEquals(AccountStatus.api_blocked, deepSeekAccount.getStatus());
        verify(mockAccountRepo).save(deepSeekAccount);

        // Verify LOCAL_AGY dispatch called
        verify(mockLocalAgy).dispatch(task, deepSeekAccount, "IMPLEMENTER");
        // Verify Jules was NOT invoked since LOCAL_AGY succeeded
        verifyNoInteractions(mockJules);
    }

    @Test
    @DisplayName("Cranes Homeostasis: if both DeepSeek and LOCAL_AGY fail, seamlessly cascades to Jules reserve")
    void testCascadeToJulesReserve() {
        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("HYBRID");

        AccountEntity deepSeekAccount = new AccountEntity();
        deepSeekAccount.setName("deepseek-primary");
        deepSeekAccount.setProvider("DEEPSEEK");
        deepSeekAccount.setStatus(AccountStatus.idle);
        deepSeekAccount.setEnabled(true);
        deepSeekAccount.setApiKey("sk-quota-exhausted-key");
        when(mockAccountRepo.findAll()).thenReturn(List.of(deepSeekAccount));

        // DeepSeek throws 429 Rate Limit
        when(mockDeepSeek.dispatch(any(), any(), any()))
                .thenThrow(new DeepSeekQuotaExhaustedException("HTTP 429 Rate Limit", 429));

        // LOCAL_AGY also encounters an issue
        when(mockLocalAgy.dispatch(any(), any(), any())).thenThrow(new RuntimeException("Local process error"));

        // Jules reserve succeeds
        WorkerDispatchResult julesSuccess = WorkerDispatchResult.ok(UUID.randomUUID(), "jules-session-99", WorkerEngineType.JULES, null);
        when(mockJules.dispatch(any(), any(), any())).thenReturn(julesSuccess);

        WorkerExecutionRouter router = new WorkerExecutionRouter(
                mockDeepSeek,
                mockJules,
                mockLocalAgy,
                mockSettings,
                mockAccountRepo,
                mockSessionRepo
        );

        WorkerDispatchResult result = router.dispatch(task, deepSeekAccount, "IMPLEMENTER");

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(WorkerEngineType.JULES, result.engineType());

        verify(mockDeepSeek).dispatch(any(), any(), any());
        verify(mockLocalAgy).dispatch(any(), any(), any());
        verify(mockJules).dispatch(task, null, "IMPLEMENTER");
    }

    @Test
    @DisplayName("Zero-Cost Sovereign First: when no DeepSeek keys exist, router immediately engages LOCAL_AGY")
    void testZeroCostDirectToLocalAgy() {
        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("HYBRID");
        when(mockAccountRepo.findAll()).thenReturn(List.of()); // No DeepSeek accounts

        WorkerDispatchResult agySuccess = WorkerDispatchResult.ok(UUID.randomUUID(), "agy-session-1", WorkerEngineType.LOCAL_AGY, "https://github.com/org/repo/pull/202");
        when(mockLocalAgy.dispatch(any(), any(), any())).thenReturn(agySuccess);

        WorkerExecutionRouter router = new WorkerExecutionRouter(
                mockDeepSeek,
                mockJules,
                mockLocalAgy,
                mockSettings,
                mockAccountRepo,
                mockSessionRepo
        );

        WorkerDispatchResult result = router.dispatch(task, null, "IMPLEMENTER");

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(WorkerEngineType.LOCAL_AGY, result.engineType());

        verifyNoInteractions(mockDeepSeek);
        verify(mockLocalAgy).dispatch(task, null, "IMPLEMENTER");
    }

    @Test
    @DisplayName("Explicit LOCAL_AGY mode routes strictly to LocalAgyWorktreeExecutionEngine")
    void testExplicitLocalAgyMode() {
        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("LOCAL_AGY");

        WorkerDispatchResult agySuccess = WorkerDispatchResult.ok(UUID.randomUUID(), "agy-session-2", WorkerEngineType.LOCAL_AGY, null);
        when(mockLocalAgy.dispatch(any(), any(), any())).thenReturn(agySuccess);

        WorkerExecutionRouter router = new WorkerExecutionRouter(
                mockDeepSeek,
                mockJules,
                mockLocalAgy,
                mockSettings,
                mockAccountRepo,
                mockSessionRepo
        );

        assertEquals(mockLocalAgy, router.resolveEngine(task));

        WorkerDispatchResult result = router.dispatch(task, null, "IMPLEMENTER");
        assertTrue(result.success());
        assertEquals(WorkerEngineType.LOCAL_AGY, result.engineType());
        verify(mockLocalAgy).dispatch(task, null, "IMPLEMENTER");
    }
}
