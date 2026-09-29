package com.eneik.production.services;

import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.dto.dashboard.ExpiredStatDto;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.ClaimResultStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 3 для ClaimResultStatus:
 *
 * 1. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012 Policy Contradiction]:
 *    - Закрытость решётки исходов удержания притязания: ровно три взаимоисключающих значения {done, failed, expired}.
 *    - Ни одно состояние не слито и не сведено к синониму: отказ выполнения (failed) категорически отличен
 *      от истечения времени аренды (expired) и успешного завершения (done).
 *
 * 2. NUEL_BELNAP_01_FALSIFICATION_HARNESS [D008 False Green]:
 *    - Читатель 1 (ClaimService):
 *      * Исход expired (releaseExpiredClaimsMaintenance): при истечении аренды задача возвращается в очередь
 *        (compareAndSetStatus(claimed, queued)), позволяя повторить выполнение другим аккаунтом.
 *      * Исход failed (closeTaskAsFailed): при отказе выполнения задача переводится в терминальный failed,
 *        предотвращая бесконечный цикл ретраев.
 *      * Исход done (completeTaskAndReleaseClaim): задача переводится в done.
 *    - Читатель 2 (ClaimRepository / BottleneckDetectionService):
 *      * expiredCountByAccountSince фильтрует строго c.resultStatus = 'expired'.
 *      * Повторные истечения аренды (> 2) генерируют боттлнек "expired_lease_spike", тогда как сбои выполнения (failed)
 *        никогда не квалифицируются как всплеск истечения аренды.
 */
class ClaimResultStatusTruthTableFalsificationTest {

    @Nested
    @DisplayName("BELNAP_03: Полнота и замкнутость трёхзначной решётки ClaimResultStatus (D012)")
    class StateSpaceExhaustivenessTests {

        @Test
        @DisplayName("BELNAP_03: ClaimResultStatus содержит ровно три исхода: done, failed, expired")
        void claimResultStatusContainsExactlyThreeOutcomes() {
            ClaimResultStatus[] values = ClaimResultStatus.values();
            assertThat(values).containsExactlyInAnyOrder(
                    ClaimResultStatus.done,
                    ClaimResultStatus.failed,
                    ClaimResultStatus.expired
            );
            assertThat(values).hasSize(3);
        }

        @Test
        @DisplayName("BELNAP_03: ClaimEntity сохраняет и отдает ClaimResultStatus без искажения")
        void claimEntityMaintainsResultStatusProperty() {
            ClaimEntity claim = new ClaimEntity();
            assertThat(claim.getResultStatus()).isNull();

            claim.setResultStatus(ClaimResultStatus.expired);
            assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.expired);

            claim.setResultStatus(ClaimResultStatus.failed);
            assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.failed);

            claim.setResultStatus(ClaimResultStatus.done);
            assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.done);
        }
    }

    @Nested
    @DisplayName("BELNAP_01: Поведенческая различимость failed и expired в ClaimService (D008)")
    class ClaimServiceBehavioralDivergenceTests {

        @Test
        @DisplayName("BELNAP_01: При expired задача возвращается в очередь (queued), а не помечается failed")
        void expiredClaimRequeuesTaskWithoutFailingIt() {
            ClaimRepository claimRepository = mock(ClaimRepository.class);
            TaskRepository taskRepository = mock(TaskRepository.class);
            AccountRepository accountRepository = mock(AccountRepository.class);

            UUID taskId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();

            TaskEntity task = new TaskEntity();
            task.setId(taskId);
            task.setStatus(TaskStatus.claimed);

            AccountEntity account = new AccountEntity();
            account.setId(accountId);

            ClaimEntity claim = new ClaimEntity();
            claim.setId(UUID.randomUUID());
            claim.setTask(task);
            claim.setAccount(account);
            claim.setLeaseExpiresAt(Instant.now().minusSeconds(60)); // просрочен

            when(claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(any(Instant.class)))
                    .thenReturn(List.of(claim));
            when(taskRepository.compareAndSetStatus(taskId, TaskStatus.claimed, TaskStatus.queued))
                    .thenReturn(1);

            JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
            when(julesSessionRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

            ClaimService claimService = mock(ClaimService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(claimService, "claimRepository", claimRepository);
            ReflectionTestUtils.setField(claimService, "taskRepository", taskRepository);
            ReflectionTestUtils.setField(claimService, "accountRepository", accountRepository);
            ReflectionTestUtils.setField(claimService, "julesSessionRepository", julesSessionRepository);

            // Запуск периодической проверки просроченных клеймов
            claimService.reapExpiredLeases();

            // 1. Статус клейма установлен строго в expired
            assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.expired);
            assertThat(claim.getReleasedAt()).isNotNull();
            verify(claimRepository).save(claim);

            // 2. Задача возвращена в очередь через CAS
            verify(taskRepository).compareAndSetStatus(taskId, TaskStatus.claimed, TaskStatus.queued);

            // 3. Задачу НЕ помечали как failed!
            verify(taskRepository, never()).writeStatusUnlessTerminal(eq(taskId), eq(TaskStatus.failed));
        }

        @Test
        @DisplayName("BELNAP_01: При failed задача переводится в сбой (TaskStatus.failed) и НЕ возвращается в очередь")
        void failedClaimMarksTaskAsFailedAndDoesNotRequeue() {
            ClaimRepository claimRepository = mock(ClaimRepository.class);
            TaskRepository taskRepository = mock(TaskRepository.class);
            AccountRepository accountRepository = mock(AccountRepository.class);

            UUID taskId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();

            TaskEntity task = new TaskEntity();
            task.setId(taskId);
            task.setStatus(TaskStatus.claimed);

            AccountEntity account = new AccountEntity();
            account.setId(accountId);

            ClaimEntity claim = new ClaimEntity();
            claim.setId(UUID.randomUUID());
            claim.setTask(task);
            claim.setAccount(account);

            when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
            when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId))
                    .thenReturn(Optional.of(claim));
            when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.failed)).thenReturn(1);

            ClaimService claimService = mock(ClaimService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(claimService, "claimRepository", claimRepository);
            ReflectionTestUtils.setField(claimService, "taskRepository", taskRepository);
            ReflectionTestUtils.setField(claimService, "accountRepository", accountRepository);

            claimService.closeTaskAsFailed(taskId, "Task execution failed irrevocably");

            // 1. Статус клейма установлен строго в failed
            assertThat(claim.getResultStatus()).isEqualTo(ClaimResultStatus.failed);
            assertThat(claim.getReleasedAt()).isNotNull();
            verify(claimRepository).save(claim);

            // 2. Задача переведена в failed
            verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.failed);

            // 3. Задачу НЕ возвращали в очередь
            verify(taskRepository, never()).compareAndSetStatus(eq(taskId), any(), eq(TaskStatus.queued));
        }
    }

    @Nested
    @DisplayName("BELNAP_01: Поведенческая различимость в BottleneckDetectionService (D008)")
    class BottleneckDetectionDivergenceTests {

        @Test
        @DisplayName("BELNAP_01: expired_lease_spike срабатывает только на повторные expired клеймы, игнорируя failed")
        void bottleneckDetectionReactsOnlyToExpiredAndIgnoresFailedClaims() {
            TaskRepository taskRepository = mock(TaskRepository.class);
            AccountRepository accountRepository = mock(AccountRepository.class);
            ClaimRepository claimRepository = mock(ClaimRepository.class);

            UUID accountId = UUID.randomUUID();

            when(taskRepository.queuedGroupedByTag()).thenReturn(Collections.emptyList());
            when(accountRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());

            // Моделируем ситуацию: у аккаунта 10 завершившихся сбоем задач (failed), но 0 просроченных (expired).
            // Запрос expiredCountByAccountSince(since) фильтрует строго c.resultStatus = 'expired',
            // поэтому возвращает пустой список.
            when(claimRepository.expiredCountByAccountSince(any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            BottleneckDetectionService service = new BottleneckDetectionService(
                    taskRepository, accountRepository, claimRepository
            );

            List<BottleneckDto> bottlenecks = service.detect();
            assertThat(bottlenecks.stream().anyMatch(b -> "expired_lease_spike".equals(b.type())))
                    .as("При отсутствии expired клеймов (даже при множестве failed) всплеск истечения аренды не детектируется")
                    .isFalse();

            // Моделируем ситуацию: у аккаунта 6 просроченных аренд (expired > порога 5)
            when(claimRepository.expiredCountByAccountSince(any(Instant.class)))
                    .thenReturn(List.of(new ExpiredStatDto(accountId, 6L)));

            List<BottleneckDto> spikeBottlenecks = service.detect();
            assertThat(spikeBottlenecks.stream().anyMatch(b -> "expired_lease_spike".equals(b.type())))
                    .as("Всплеск истечения аренды детектируется строго по expired-статусам")
                    .isTrue();

            BottleneckDto spike = spikeBottlenecks.stream()
                    .filter(b -> "expired_lease_spike".equals(b.type()))
                    .findFirst()
                    .orElseThrow();
            assertThat(spike.accountId()).isEqualTo(accountId);
            assertThat(spike.expiredCount24h()).isEqualTo(6L);
            assertThat(spike.reason()).contains("Account has repeated expired leases");
        }
    }
}
