package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.EmsDashboardMetricsDto;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmsMetricsServiceTest {

    private final EmsMetricsService service = new EmsMetricsService();

    @Test
    void buildShowsAllDoctrineRolesEvenWithoutExecutionTasks() {
        EmsDashboardMetricsDto metrics = service.build(List.of(), List.of());

        assertThat(metrics.roleDoctrineReadiness().roles()).hasSize(12);
        assertThat(metrics.roleDoctrineReadiness().unknown()).isEqualTo(12);
        assertThat(metrics.roleDoctrineReadiness().statusLabel()).isEqualTo("incomplete");
        assertThat(metrics.roleKpis()).hasSize(12);
        assertThat(metrics.roleKpis()).allMatch(kpi -> "idle".equals(kpi.statusLabel()));
    }

    @Test
    void pendingSourceRoleRefusalBlocksDoctrineReadiness() {
        WishlistEntity objection = new WishlistEntity();
        objection.setProjectId(UUID.randomUUID());
        objection.setSource(WishlistSource.role);
        objection.setSourceRoleTag("BARCAN-TAG-07");
        objection.setStatus(WishlistStatus.pending);
        objection.setContent("Critical auth validation violation detected by role attack.");

        EmsDashboardMetricsDto metrics = service.build(List.of(), List.of(objection));

        assertThat(metrics.roleDoctrineReadiness().statusLabel()).isEqualTo("blocked");
        assertThat(metrics.roleDoctrineReadiness().refuses()).isEqualTo(1);
        assertThat(metrics.roleDoctrineReadiness().roles())
                .filteredOn(role -> role.roleTag().equals("BARCAN-TAG-07"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.stance()).isEqualTo("refuses");
                    assertThat(role.sourceWishlistPending()).isEqualTo(1);
                    assertThat(role.kanoPressure()).isEqualTo("must_be");
                });
    }
}
