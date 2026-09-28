package com.eneik.production.services;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.logging.LogScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 2 для StrandedFinalizingSweepService:
 * динамический расчет таймаута финализации (AYZEK_LEVI_01) и эмпирическая проверка рантайма (LYUDVIG_VITGENSHTEYN_14).
 *
 * Grounding:
 * - BARCAN-TAG-04_MODAL-QUANTIFIER:03:ayzek-levi / AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER [D007, Levi]:
 *   динамическая ревизия доксастического состояния (срока аренды finalizing) на основе эмпирических
 *   свидетельств FINALIZING_DURATION вместо захардкоженной константы.
 * - BARCAN-TAG-00_CODE-GUARDIAN:01:lyudvig-vitgenshteyn / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]:
 *   эмпирическая сверка рантайма: CAS-атомарность, идемпотентность, фильтрация статусов проекта, гигиена MDC-скоупа.
 */
class StrandedFinalizingDynamicTimeoutFalsificationTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private DefectJournalRepository defectJournalRepository;
    private StrandedFinalizingSweepService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        service = new StrandedFinalizingSweepService(projectRepository, wishlistRepository, null);
        service.setDefectJournalRepository(defectJournalRepository);
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "maxAgeMinutes", 3L);
        service.setMinSamplesForDataDriven(5);
        service.setSafetyMultiplier(10.0);
    }

    @Test
    @DisplayName("AYZEK_LEVI_01: При отсутствии наблюдений действует консервативный fallback (maxAgeMinutes)")
    void fallbackToDefaultLeaseWhenZeroOrInsufficientEvidence() {
        // Случай 0 наблюдений: репозиторий возвращает пустой список
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(List.of());
        assertEquals(Duration.ofMinutes(3), service.calculateEffectiveLeaseDuration());

        // Случай 4 наблюдений (< minSamples=5): вера не пересматривается преждевременно
        List<DefectJournalEntity> fourSamples = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            DefectJournalEntity entity = new DefectJournalEntity();
            entity.setMetricValue(2500.0 * i);
            fourSamples.add(entity);
        }
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(fourSamples);
        assertEquals(Duration.ofMinutes(3), service.calculateEffectiveLeaseDuration());
    }

    @Test
    @DisplayName("AYZEK_LEVI_01: Робастная медиана защищает от единичных экстремальных выбросов")
    void medianIsRobustAgainstExtremeOutliers() {
        // 4 замера по 2 секунды (2000 мс) и 1 катастрофический выброс (600 секунд = 600,000 мс)
        // Среднее арифметическое взлетело бы до (8000 + 600000)/5 = 121,600 мс (121 сек * 10 = 1216 сек = 20 мин!),
        // блокируя своевременное освобождение зависших строк.
        // Медиана остается ровно 2000 мс! 2000 * 10 = 20,000 мс -> срез на полу 30,000 мс.
        List<DefectJournalEntity> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            DefectJournalEntity e = new DefectJournalEntity();
            e.setMetricValue(2000.0);
            entries.add(e);
        }
        DefectJournalEntity outlier = new DefectJournalEntity();
        outlier.setMetricValue(600_000.0);
        entries.add(outlier);

        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(entries);

        Duration effective = service.calculateEffectiveLeaseDuration();
        assertEquals(Duration.ofMillis(30_000L), effective, "Медиана отсекает выброс и зажимается защитным полом 30с");
    }

    @Test
    @DisplayName("AYZEK_LEVI_01: Четное число наблюдений корректно интерполирует медиану")
    void evenSampleCountComputesAverageOfTwoMiddleValues() {
        // Замеры: 2000, 3000, 5000, 7000, 9000, 11000 мс (всего 6)
        // Два средних: 5000 и 7000 -> медиана = 6000 мс
        // 6000 * 10 = 60,000 мс (60 секунд)
        List<DefectJournalEntity> entries = new ArrayList<>();
        double[] values = {2000.0, 3000.0, 5000.0, 7000.0, 9000.0, 11000.0};
        for (double v : values) {
            DefectJournalEntity e = new DefectJournalEntity();
            e.setMetricValue(v);
            entries.add(e);
        }

        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(entries);

        Duration effective = service.calculateEffectiveLeaseDuration();
        assertEquals(Duration.ofMillis(60_000L), effective);
    }

    @Test
    @DisplayName("ANTI_MIRROR_TELEMETRY: CAS защищает живого исполнителя от потери аренды")
    void casGuaranteesLiveWorkerNeverRobbedOfLease() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID wishlistId = UUID.randomUUID();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        wishlist.setStatus(WishlistStatus.finalizing);
        wishlist.setFinalizingSince(Instant.now().minus(10, ChronoUnit.MINUTES));

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(wishlist));
        // CAS возвращает 0 (живой компилятор завершил работу и сам перевел статус в completed)
        when(wishlistRepository.compareAndSetStatus(wishlistId, WishlistStatus.finalizing, WishlistStatus.pending))
                .thenReturn(0);

        service.sweepProject(project);

        verify(wishlistRepository).compareAndSetStatus(wishlistId, WishlistStatus.finalizing, WishlistStatus.pending);
    }

    @Test
    @DisplayName("ANTI_MIRROR_TELEMETRY: Гигиена MDC-скоупа гарантированно очищается в finally")
    void mdcScopeCleanedUpInFinallyEvenOnException() {
        ProjectEntity project = new ProjectEntity();
        UUID projectId = UUID.randomUUID();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(project));
        // Имитация исключения при обработке проекта
        StrandedFinalizingSweepService mockSelf = mock(StrandedFinalizingSweepService.class);
        doThrow(new RuntimeException("Simulated database timeout")).when(mockSelf).sweepProject(any());
        ReflectionTestUtils.setField(service, "self", mockSelf);

        service.sweep();

        // Проверяем, что MDC-скоуп очищен и не засоряет контекст других потоков
        assertThat(MDC.get(LogScope.MDC_KEY)).isNull();
    }
}
