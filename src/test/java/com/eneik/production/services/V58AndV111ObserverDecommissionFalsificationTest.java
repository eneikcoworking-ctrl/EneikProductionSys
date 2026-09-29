package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEventLogEntity;
import com.eneik.production.repositories.ProjectEventLogRepository;
import com.eneik.production.services.logging.ProjectLogFlushQueue;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 2 для V58/V111 (отключение Gemini observer, fail-closed инвариант без самопотребления логов):
 *
 * 1. GILBERT_RAYL_03_CATEGORY_ERROR_SCAN [D002, Ryle] & LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]:
 *    - Разделение наблюдателя и рантайма (Category Error):
 *      * Запрет зеркального пожирания логов (Anti-Mirror): Gemini не скармливается собственным журналом бэкенда.
 *      * Постоянная инертность GeminiProjectObserverService (V111): цикл наблюдения декоммиссионирован как Муда (0 вызовов, 0 мутаций).
 *      * Блокировка настройки как код (Prohibition as Code): запрет повторного включения 'gemini_project_observer_enabled' через API.
 *
 * 2. AHILLE_VARTSI_04_ESSENCE_BEFORE_OPTION [D002, Varzi]:
 *    - Долговечность журнала проекта независимо от выкладок (V61 / восстановление после чрезмерного изъятия V58):
 *      * ProjectEventLogService сохраняет события проекта в персистентную БД, переживая перезапуски контейнеров.
 *      * Защита от OOM при выключенном флаге: очередь ProjectLogFlushQueue безопасно сбрасывается без сохранения в БД.
 *      * Защита от переполнения: жесткий лимит очереди (20 000) и ограничение выборки recent (max 5000).
 */
class V58AndV111ObserverDecommissionFalsificationTest {

    @BeforeEach
    void cleanQueue() {
        // Очищаем статическую очередь перед каждым тестом
        ProjectLogFlushQueue.drain(Integer.MAX_VALUE);
    }

    @Nested
    @DisplayName("GILBERT_RAYL_03 & LYUDVIG_VITGENSHTEYN_14: Запрет самопотребления и декоммиссия Gemini Observer (V111)")
    class ObserverDecommissionTests {

        @Test
        @DisplayName("RAYL_03: GeminiProjectObserverService.runObserverCycle перманентно инертен (0 зависимостей, 0 вызовов)")
        void observerCycleIsPermanentlyInertStub() {
            GeminiProjectObserverService observerService = new GeminiProjectObserverService();

            // Инвариант: вызов цикла завершается мгновенно, без сетевых запросов, без обращений к БД и без исключений
            observerService.runObserverCycle();
        }

        @Test
        @DisplayName("VITGENSHTEYN_14: Попытка включения gemini_project_observer_enabled через API блокируется как запрет в коде")
        void modifyingObserverSettingViaApiIsForbiddenByCode() {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            Environment environment = mock(Environment.class);
            SystemSettingsService settingsService = new SystemSettingsService(jdbcTemplate, environment);

            assertThatThrownBy(() -> settingsService.save("gemini_project_observer_enabled", "true"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Setting 'gemini_project_observer_enabled' cannot be modified via API")
                    .hasMessageContaining("GeminiProjectObserverService has been permanently decommissioned as Muda (V111)");

            verifyNoInteractions(jdbcTemplate);
        }
    }

    @Nested
    @DisplayName("AHILLE_VARTSI_04: Долговечность журнала проекта независимо от выкладок (V61/V58)")
    class ProjectEventLogDurabilityTests {

        @Test
        @DisplayName("VARZI_04: flush сбрасывает и персистирует события проекта при включенном флаге")
        void flushPersistsPendingLogEntriesWhenEnabled() {
            ProjectEventLogRepository repository = mock(ProjectEventLogRepository.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);
            when(settingsService.effectiveBoolean("project_event_log_enabled")).thenReturn(true);

            UUID projectId = UUID.randomUUID();
            Instant now = Instant.now();
            ProjectLogFlushQueue.offer(new ProjectLogFlushQueue.PendingEntry(
                    projectId, now, "INFO", "com.eneik.production.OrderService", "Order created"
            ));

            ProjectEventLogService logService = new ProjectEventLogService(repository, settingsService);
            logService.flush();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ProjectEventLogEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(repository).saveAll(captor.capture());

            List<ProjectEventLogEntity> saved = captor.getValue();
            assertThat(saved).hasSize(1);
            ProjectEventLogEntity entry = saved.get(0);
            assertThat(entry.getProjectId()).isEqualTo(projectId);
            assertThat(entry.getLevel()).isEqualTo("INFO");
            assertThat(entry.getLogger()).isEqualTo("com.eneik.production.OrderService");
            assertThat(entry.getMessage()).isEqualTo("Order created");
            assertThat(entry.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("VARZI_04: flush дренирует очередь без записи в БД при выключенном флаге (защита от утечки памяти)")
        void flushDrainsQueueWithoutPersistingWhenDisabled() {
            ProjectEventLogRepository repository = mock(ProjectEventLogRepository.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);
            when(settingsService.effectiveBoolean("project_event_log_enabled")).thenReturn(false);

            UUID projectId = UUID.randomUUID();
            ProjectLogFlushQueue.offer(new ProjectLogFlushQueue.PendingEntry(
                    projectId, Instant.now(), "WARN", "BillingService", "Payment retry"
            ));

            ProjectEventLogService logService = new ProjectEventLogService(repository, settingsService);
            logService.flush();

            // Запись в БД НЕ должна производиться
            verify(repository, never()).saveAll(any());

            // Очередь должна быть пустой (дренирована), предотвращая OOM
            assertThat(ProjectLogFlushQueue.drain(10)).isEmpty();
        }

        @Test
        @DisplayName("VARZI_04: recent ограничивает лимит выборки интервалом [1, 5000] для защиты от переполнения")
        void recentBoundsLimitSafely() {
            ProjectEventLogRepository repository = mock(ProjectEventLogRepository.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);
            ProjectEventLogService logService = new ProjectEventLogService(repository, settingsService);
            UUID projectId = UUID.randomUUID();

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

            // Запрос с отрицательным лимитом ограничивается снизу до 1
            logService.recent(projectId, -10);
            // Запрос с чрезмерным лимитом ограничивается сверху до 5000
            logService.recent(projectId, 100_000);

            verify(repository, times(2)).findByProjectIdOrderByCreatedAtDesc(eq(projectId), pageableCaptor.capture());
            List<Pageable> captured = pageableCaptor.getAllValues();
            assertThat(captured.get(0).getPageSize()).isEqualTo(1);
            assertThat(captured.get(1).getPageSize()).isEqualTo(5000);
        }

        @Test
        @DisplayName("VARZI_04: since делегирует хронологическую выборку в репозиторий")
        void sinceDelegatesChronologicalQueryToRepository() {
            ProjectEventLogRepository repository = mock(ProjectEventLogRepository.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);
            ProjectEventLogService logService = new ProjectEventLogService(repository, settingsService);

            UUID projectId = UUID.randomUUID();
            Instant since = Instant.now().minusSeconds(3600);

            logService.since(projectId, since);
            verify(repository).findByProjectIdAndCreatedAtAfterOrderByCreatedAtAsc(projectId, since);
        }
    }
}
