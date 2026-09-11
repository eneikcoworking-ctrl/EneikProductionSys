package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.EmsDashboardMetricsDto;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmsMetricsServiceTest {

    private final EmsMetricsService service = new EmsMetricsService(
            mock(com.eneik.production.repositories.JulesSessionRepository.class),
            mock(com.eneik.production.repositories.PrReviewRepository.class));

    @Test
    void buildShowsAllDoctrineRolesEvenWithoutExecutionTasks() {
        EmsDashboardMetricsDto metrics = service.build(List.of(), List.of());

        assertThat(metrics.roleDoctrineReadiness().roles()).hasSize(13);
        assertThat(metrics.roleDoctrineReadiness().unknown()).isEqualTo(13);
        assertThat(metrics.roleDoctrineReadiness().statusLabel()).isEqualTo("incomplete");
        assertThat(metrics.roleKpis()).hasSize(13);
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

    @Test
    void satisfactionScoreCalculationWithImpactWeights() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        
        RoleEntity r1 = new RoleEntity();
        r1.setTag("BARCAN-TAG-01");
        r1.setDescription("name");
        
        TaskEntity t1 = new TaskEntity();
        t1.setRole(r1);
        t1.setStatus(com.eneik.production.models.persistence.TaskStatus.done);
        t1.setQualityGatePassed(true);
        com.fasterxml.jackson.databind.node.ObjectNode p1 = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode m1 = mapper.createObjectNode();
        m1.put("BARCAN-TAG-01", 0.9);
        m1.put("BARCAN-TAG-02", 0.1);
        p1.set("impact_coefficients", m1);
        t1.setPayload(p1);

        RoleEntity r2 = new RoleEntity();
        r2.setTag("BARCAN-TAG-02");
        r2.setDescription("name");

        TaskEntity t2 = new TaskEntity();
        t2.setRole(r2);
        t2.setStatus(com.eneik.production.models.persistence.TaskStatus.queued);
        com.fasterxml.jackson.databind.node.ObjectNode p2 = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode m2 = mapper.createObjectNode();
        m2.put("BARCAN-TAG-01", 0.1);
        m2.put("BARCAN-TAG-02", 0.9);
        p2.set("impact_coefficients", m2);
        t2.setPayload(p2);

        EmsDashboardMetricsDto metrics = service.build(List.of(t1, t2), List.of());
        
        assertThat(metrics.roleDoctrineReadiness().roles())
                .filteredOn(role -> role.roleTag().equals("BARCAN-TAG-02"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.stance()).isEqualTo("almost_satisfied");
                    assertThat(role.satisfactionScore()).isEqualTo(73.8);
                });
    }

    // D012 / D002 / Prescription 9: Falsification of absorbing failure states in doctrine readiness

    @Test
    void unrecoveredFailedTaskRefusesDoctrineReadinessWithActionableObjection() {
        RoleEntity r = new RoleEntity();
        r.setTag("BARCAN-TAG-00");
        r.setDescription("code guardian");

        TaskEntity failedTask = new TaskEntity();
        failedTask.setRole(r);
        failedTask.setStatus(com.eneik.production.models.persistence.TaskStatus.failed);
        failedTask.setContentKey("key-auth-1");

        EmsDashboardMetricsDto metrics = service.build(List.of(failedTask), List.of());

        assertThat(metrics.roleDoctrineReadiness().roles())
                .filteredOn(role -> role.roleTag().equals("BARCAN-TAG-00"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.stance()).isEqualTo("refuses");
                    assertThat(role.topObjection()).contains("unrecovered failed work");
                });
    }

    @Test
    void recoveredFailedTaskDoesNotRefuseDoctrineReadiness() {
        RoleEntity r = new RoleEntity();
        r.setTag("BARCAN-TAG-00");
        r.setDescription("code guardian");

        TaskEntity failedTask = new TaskEntity();
        failedTask.setRole(r);
        failedTask.setStatus(com.eneik.production.models.persistence.TaskStatus.failed);
        failedTask.setContentKey("key-auth-1");

        // Successor task for the same content key succeeds
        TaskEntity recoveredTask = new TaskEntity();
        recoveredTask.setRole(r);
        recoveredTask.setStatus(com.eneik.production.models.persistence.TaskStatus.done);
        recoveredTask.setContentKey("key-auth-1");
        recoveredTask.setQualityGatePassed(true);

        EmsDashboardMetricsDto metrics = service.build(List.of(failedTask, recoveredTask), List.of());

        assertThat(metrics.roleDoctrineReadiness().roles())
                .filteredOn(role -> role.roleTag().equals("BARCAN-TAG-00"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.stance())
                            .as("Historical failed task recovered by succeeding work must NOT cause 'refuses'")
                            .isNotEqualTo("refuses");
                    assertThat(role.stance()).isEqualTo("satisfied");
                });
    }

    @Test
    void closedDefectWorkDoesNotTriggerDefectWorkObjection() {
        RoleEntity r = new RoleEntity();
        r.setTag("BARCAN-TAG-07");
        r.setDescription("security / second-order knowledge");

        // Task with retryCount > 0 that successfully finished (closed defect recovery)
        TaskEntity defectTaskDone = new TaskEntity();
        defectTaskDone.setRole(r);
        defectTaskDone.setStatus(com.eneik.production.models.persistence.TaskStatus.done);
        defectTaskDone.setRetryCount(2);
        defectTaskDone.setQualityGatePassed(true);

        EmsDashboardMetricsDto metrics = service.build(List.of(defectTaskDone), List.of());

        assertThat(metrics.roleDoctrineReadiness().roles())
                .filteredOn(role -> role.roleTag().equals("BARCAN-TAG-07"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.stance()).isEqualTo("satisfied");
                    assertThat(role.topObjection()).doesNotContain("Defect-work evidence remains attached");
                });
    }
}
