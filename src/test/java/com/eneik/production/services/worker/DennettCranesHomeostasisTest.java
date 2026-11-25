package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.agy.LocalAgyWorktreeExecutionEngine;
import com.eneik.production.services.worker.deepseek.DeepSeekQuotaExhaustedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DennettCranesHomeostasisTest {

    @Test
    @DisplayName("Cranes, not Skyhooks (WO #13): on 402/429 DeepSeek error, marks account blocked and fails over strictly to sovereign LOCAL_AGY (never Jules)")
    void testCranesHomeostasisFailover() {
        DeepSeekWorktreeExecutionEngine mockDeepSeek = mock(DeepSeekWorktreeExecutionEngine.class);
        JulesExecutionEngine mockJules = mock(JulesExecutionEngine.class);
        LocalAgyWorktreeExecutionEngine mockLocalAgy = mock(LocalAgyWorktreeExecutionEngine.class);
        SystemSettingsService mockSettings = mock(SystemSettingsService.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        JulesSessionRepository mockSessionRepo = mock(JulesSessionRepository.class);

        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("HYBRID");

        AccountEntity deepSeekAccount = new AccountEntity();
        deepSeekAccount.setName("deepseek-primary");
        deepSeekAccount.setProvider("DEEPSEEK");
        deepSeekAccount.setStatus(AccountStatus.idle);
        deepSeekAccount.setEnabled(true);
        deepSeekAccount.setApiKey("sk-quota-exhausted-key");

        when(mockAccountRepo.findAll()).thenReturn(List.of(deepSeekAccount));

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        // DeepSeek throws 402 Insufficient Balance
        when(mockDeepSeek.dispatch(any(), any(), any()))
                .thenThrow(new DeepSeekQuotaExhaustedException("HTTP 402 Insufficient Balance", 402));

        // Sovereign LOCAL_AGY engine succeeds
        WorkerDispatchResult agySuccess = WorkerDispatchResult.ok(UUID.randomUUID(), "local-agy-session-1", WorkerEngineType.LOCAL_AGY, null);
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

        // Verify account marked api_blocked
        assertEquals(AccountStatus.api_blocked, deepSeekAccount.getStatus());
        verify(mockAccountRepo).save(deepSeekAccount);
        // Verify LOCAL_AGY called and Google Jules NEVER called
        verify(mockLocalAgy).dispatch(task, deepSeekAccount, "IMPLEMENTER");
        verifyNoInteractions(mockJules);
    }

    @Test
    @DisplayName("Cranes, not Skyhooks (WO #13): when both DeepSeek and LOCAL_AGY fail/unavailable, returns honest failure without calling Jules")
    void testNoFallbackToJulesWhenBothSovereignEnginesUnavailable() {
        DeepSeekWorktreeExecutionEngine mockDeepSeek = mock(DeepSeekWorktreeExecutionEngine.class);
        JulesExecutionEngine mockJules = mock(JulesExecutionEngine.class);
        SystemSettingsService mockSettings = mock(SystemSettingsService.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        JulesSessionRepository mockSessionRepo = mock(JulesSessionRepository.class);

        when(mockSettings.effectiveValue("worker_engine_mode")).thenReturn("HYBRID");
        when(mockAccountRepo.findAll()).thenReturn(List.of());

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        WorkerExecutionRouter router = new WorkerExecutionRouter(
                mockDeepSeek,
                mockJules,
                null,
                mockSettings,
                mockAccountRepo,
                mockSessionRepo
        );

        WorkerDispatchResult result = router.dispatch(task, null, "IMPLEMENTER");

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("NO_SOVEREIGN_WORKER_AVAILABLE"));
        verifyNoInteractions(mockJules);
    }
}
