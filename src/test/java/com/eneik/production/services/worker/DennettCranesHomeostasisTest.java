package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.settings.SystemSettingsService;
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
    @DisplayName("Cranes, not Skyhooks: on 402/429 DeepSeek error, marks account blocked and fails over to Jules")
    void testCranesHomeostasisFailover() {
        DeepSeekWorktreeExecutionEngine mockDeepSeek = mock(DeepSeekWorktreeExecutionEngine.class);
        JulesExecutionEngine mockJules = mock(JulesExecutionEngine.class);
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

        // Jules engine succeeds
        WorkerDispatchResult julesSuccess = WorkerDispatchResult.ok(UUID.randomUUID(), "jules-session-1", WorkerEngineType.JULES, null);
        when(mockJules.dispatch(any(), any(), any())).thenReturn(julesSuccess);

        WorkerExecutionRouter router = new WorkerExecutionRouter(
                mockDeepSeek,
                mockJules,
                mockSettings,
                mockAccountRepo,
                mockSessionRepo
        );

        WorkerDispatchResult result = router.dispatch(task, deepSeekAccount, "IMPLEMENTER");

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(WorkerEngineType.JULES, result.engineType());

        // Verify account marked api_blocked
        assertEquals(AccountStatus.api_blocked, deepSeekAccount.getStatus());
        verify(mockAccountRepo).save(deepSeekAccount);
        // Verify Jules dispatch called
        verify(mockJules).dispatch(task, null, "IMPLEMENTER");
    }
}
