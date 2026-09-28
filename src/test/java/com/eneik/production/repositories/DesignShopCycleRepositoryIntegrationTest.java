package com.eneik.production.repositories;

import com.eneik.production.models.persistence.DesignShopCycleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест-заслон для DesignShopCycleRepository (Ступень 2, XX.1).
 *
 * Проверяемые инварианты:
 * 1. claimStartCycle CAS: атомарный захват аренды (только один победитель).
 * 2. readiness gate: lastWasReady = true в базе блокирует повторный старт.
 * 3. BOUNDARY_TOPOLOGY / TTL: истёкшая аренда (startCycleClaimedAt < expiryCutoff) автоматически перезанимается.
 * 4. CAS-release: compareAndReleaseStrandedClaim сбрасывает аренду только если timestamp совпадает с ожидаемым.
 * 5. Sweeping query: findByStartCycleClaimedAtIsNotNullAndStartCycleClaimedAtBefore находит зависшие циклы.
 *
 * Паттерны:
 * - BARCAN-TAG-06_DEONTIC-CONSISTENCY:01:karl-popper / KARL_POPPER_01_FALSIFICATION_HARNESS [D008, Popper]
 * - BARCAN-TAG-00_CODE-GUARDIAN:01:lyudvig-vitgenshteyn / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]
 */
@DataJpaTest
@ActiveProfiles("test")
class DesignShopCycleRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DesignShopCycleRepository repository;

    private DesignShopCycleEntity createCycle(UUID projectId, boolean lastWasReady, Instant claimedAt) {
        DesignShopCycleEntity entity = new DesignShopCycleEntity();
        entity.setProjectId(projectId);
        entity.setLastWasReady(lastWasReady);
        entity.setStartCycleClaimedAt(claimedAt);
        entity.setStage(DesignShopCycleEntity.STAGE_IDLE);
        entity.setEditIterationCount(0);
        return entityManager.persistAndFlush(entity);
    }

    @Test
    @DisplayName("CAS Claim: свободный цикл успешно захватывается первым претендентом")
    void claimStartCycleSucceedsForUnclaimedAndNotReadyCycle() {
        UUID projectId = UUID.randomUUID();
        createCycle(projectId, false, null);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int rowsUpdated = repository.claimStartCycle(projectId, now);

        assertThat(rowsUpdated).isEqualTo(1);

        entityManager.clear();
        DesignShopCycleEntity updated = repository.findByProjectId(projectId).orElseThrow();
        assertThat(updated.getStartCycleClaimedAt()).isNotNull();
    }

    @Test
    @DisplayName("Mutual Exclusion: повторная попытка захватить активную аренду отклоняется базой (0 rows)")
    void claimStartCycleRejectsConcurrentActiveClaim() {
        UUID projectId = UUID.randomUUID();
        Instant activeClaim = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        createCycle(projectId, false, activeClaim);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int rowsUpdated = repository.claimStartCycle(projectId, now);

        assertThat(rowsUpdated).isEqualTo(0);

        entityManager.clear();
        DesignShopCycleEntity unchanged = repository.findByProjectId(projectId).orElseThrow();
        assertThat(unchanged.getStartCycleClaimedAt()).isEqualTo(activeClaim);
    }

    @Test
    @DisplayName("Readiness Invariant: если lastWasReady=true, захват блокируется даже при startCycleClaimedAt=null")
    void claimStartCycleRefusesWhenLastWasReadyIsTrue() {
        UUID projectId = UUID.randomUUID();
        createCycle(projectId, true, null);

        Instant now = Instant.now();
        int rowsUpdated = repository.claimStartCycle(projectId, now);

        assertThat(rowsUpdated).isEqualTo(0);
    }

    @Test
    @DisplayName("BOUNDARY_TOPOLOGY / TTL: истёкшая аренда (>15 мин) успешно перезанимается новым тактом")
    void claimStartCycleRecoversExpiredClaimPastTtl() {
        UUID projectId = UUID.randomUUID();
        Instant expiredClaim = Instant.now().minus(Duration.ofMinutes(30));
        createCycle(projectId, false, expiredClaim);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int rowsUpdated = repository.claimStartCycle(projectId, now);

        assertThat(rowsUpdated).isEqualTo(1);

        entityManager.clear();
        DesignShopCycleEntity updated = repository.findByProjectId(projectId).orElseThrow();
        assertThat(updated.getStartCycleClaimedAt()).isAfter(expiredClaim);
    }

    @Test
    @DisplayName("CAS Release: compareAndReleaseStrandedClaim освобождает только при совпадении метки эпохи")
    void compareAndReleaseStrandedClaimGuardsHolderEpoch() {
        UUID projectId = UUID.randomUUID();
        Instant originalClaim = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.MILLIS);
        createCycle(projectId, false, originalClaim);

        // 1. Попытка с чужим (несовпадающим) timestamp отклоняется
        Instant wrongTimestamp = Instant.now().minus(Duration.ofMinutes(10));
        int rejected = repository.compareAndReleaseStrandedClaim(projectId, wrongTimestamp);
        assertThat(rejected).isEqualTo(0);

        // 2. Освобождение с правильным timestamp проходит успешно
        int released = repository.compareAndReleaseStrandedClaim(projectId, originalClaim);
        assertThat(released).isEqualTo(1);

        entityManager.clear();
        DesignShopCycleEntity cleared = repository.findByProjectId(projectId).orElseThrow();
        assertThat(cleared.getStartCycleClaimedAt()).isNull();
    }

    @Test
    @DisplayName("Sweeping Query: находит зависшие циклы строго старше переданного cutoff")
    void findByStartCycleClaimedAtIsNotNullAndStartCycleClaimedAtBeforeFiltersAccurately() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(15));

        UUID p1 = UUID.randomUUID(); // зависший (30 мин назад)
        createCycle(p1, false, now.minus(Duration.ofMinutes(30)));

        UUID p2 = UUID.randomUUID(); // свежий (5 мин назад)
        createCycle(p2, false, now.minus(Duration.ofMinutes(5)));

        UUID p3 = UUID.randomUUID(); // не занятый (null)
        createCycle(p3, false, null);

        List<DesignShopCycleEntity> stranded =
                repository.findByStartCycleClaimedAtIsNotNullAndStartCycleClaimedAtBefore(cutoff);

        assertThat(stranded).hasSize(1);
        assertThat(stranded.get(0).getProjectId()).isEqualTo(p1);
    }
}
