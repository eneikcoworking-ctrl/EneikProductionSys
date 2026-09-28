package com.eneik.production.services.operational;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Фальсифицирующий замер таблицы истинностных статусов ProjectStatus (Ступень 2, предписание 57).
 *
 * Паттерны:
 * - BARCAN-TAG-06_DEONTIC-CONSISTENCY:03:nuel-belnap / NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]:
 *   таблица истинностных статусов жизненного цикла проекта.
 * - BARCAN-TAG-00_CODE-GUARDIAN:01:lyudvig-vitgenshteyn / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]:
 *   эмпирическая сверка статусов с рантаймом.
 */
class ProjectStatusTruthTableTest {

    @Test
    @DisplayName("Belnap 7-valued truth status: ProjectStatus содержит ровно 7 первоклассных состояний")
    void projectStatusEnumHasSevenExplicitStates() {
        assertThat(ProjectStatus.values()).containsExactlyInAnyOrder(
                ProjectStatus.active,
                ProjectStatus.analyzing,
                ProjectStatus.waiting,
                ProjectStatus.frozen,
                ProjectStatus.accepted,
                ProjectStatus.archived,
                ProjectStatus.stalled
        );
    }

    @Test
    @DisplayName("NUEL_BELNAP_03: stalled статус строго обособлен от active и терминальных состояний")
    void stalledStatusPredicatesAreMutuallyExclusive() {
        ProjectStatus stalled = ProjectStatus.stalled;
        assertThat(stalled.isStalled()).isTrue();
        assertThat(stalled.isActive()).isFalse();
        assertThat(stalled.isTerminal()).isFalse();

        ProjectStatus active = ProjectStatus.active;
        assertThat(active.isActive()).isTrue();
        assertThat(active.isStalled()).isFalse();
        assertThat(active.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Терминальность: только accepted и archived являются терминальными состояниями проекта")
    void terminalPredicatesAreExact() {
        assertThat(ProjectStatus.accepted.isTerminal()).isTrue();
        assertThat(ProjectStatus.archived.isTerminal()).isTrue();

        assertThat(ProjectStatus.active.isTerminal()).isFalse();
        assertThat(ProjectStatus.analyzing.isTerminal()).isFalse();
        assertThat(ProjectStatus.waiting.isTerminal()).isFalse();
        assertThat(ProjectStatus.frozen.isTerminal()).isFalse();
        assertThat(ProjectStatus.stalled.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("ProjectEntity предикаты синхронно отражают статус проекта")
    void projectEntityDelegatesStatusPredicatesAccurately() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());

        project.setStatus(ProjectStatus.stalled);
        assertThat(project.isStalled()).isTrue();
        assertThat(project.isActive()).isFalse();

        project.setStatus(ProjectStatus.active);
        assertThat(project.isActive()).isTrue();
        assertThat(project.isStalled()).isFalse();

        project.setStatus(ProjectStatus.frozen);
        assertThat(project.isActive()).isFalse();
        assertThat(project.isStalled()).isFalse();
    }

    @Test
    @DisplayName("Anti-Mirror Telemetry: ProjectStatus.stalled транслируется в SYSTEM_STALLED и блокирует диспетчеризацию")
    void stalledProjectProducesBlockingSystemStalledStateInFlowSpine() {
        FlowSpineService.StateInputs input = input(ProjectStatus.stalled, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true, "ok", false);

        String state = FlowSpineService.decideState(input);
        assertEquals("SYSTEM_STALLED", state);
        assertTrue(FlowSpineService.isBlockingState(state));
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"frozen", "archived", "accepted", "waiting", "analyzing"})
    @DisplayName("Специфичные статусы проекта не маскируются под активный поток")
    void nonActiveProjectStatusesProduceDistinctLifecycleStates(ProjectStatus status) {
        FlowSpineService.StateInputs input = input(status, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true, "ok", false);

        String state = FlowSpineService.decideState(input);
        switch (status) {
            case frozen -> assertEquals("FROZEN", state);
            case archived -> assertEquals("ARCHIVED", state);
            case accepted -> assertEquals("ACCEPTED", state);
            case waiting, analyzing -> assertEquals("PROJECT_NOT_ACTIVE", state);
            default -> {}
        }
    }

    private FlowSpineService.StateInputs input(ProjectStatus projectStatus,
                                               long queuedTasks,
                                               long activeTasks,
                                               long reviewTasks,
                                               long doneTasks,
                                               long failedTasks,
                                               long blockedTasks,
                                               long pendingWishlist,
                                               long compilingWishlist,
                                               long openSessions,
                                               int mergedReviews,
                                               int openReviews,
                                               int failingReviews,
                                               int qualityGatePassed,
                                               int qualityGateFailed,
                                               int totalFeatures,
                                               int completeFeatures,
                                               int totalDeliverables,
                                               int mergedDeliverables,
                                               boolean decompositionComplete,
                                               String systemStatus,
                                               boolean duplicateContentDetected) {
        return new FlowSpineService.StateInputs(projectStatus, queuedTasks, activeTasks, reviewTasks, doneTasks,
                failedTasks, blockedTasks, pendingWishlist, compilingWishlist, openSessions, mergedReviews,
                openReviews, 0L, "", failingReviews, qualityGatePassed, qualityGateFailed, totalFeatures, completeFeatures,
                totalDeliverables, mergedDeliverables, decompositionComplete, systemStatus, duplicateContentDetected);
    }
}
