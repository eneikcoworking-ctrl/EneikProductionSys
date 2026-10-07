package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskGateLogEntity;
import com.eneik.production.repositories.TaskGateLogRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stage 4 empirical falsification test suite for {@link GateOrchestrator}.
 * <p>
 * Grounded in:
 * <ul>
 *   <li>KARL_POPPER_01_FALSIFICATION_HARNESS [D008, Popper]: Denominator falsification (applicableChecksByStage);
 *       prevents false green 0/0 empty-set pass from certifying delivery.</li>
 *   <li>KARL_POPPER_08_INSTITUTIONAL_FACT_REGISTER [D007, Popper]: Institutional gate evidence persisted to
 *       {@link TaskGateLogEntity} with verified report and stages metadata.</li>
 *   <li>DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX [D006, Raz]: Deontic filtering of stages and build-phase exemption.</li>
 * </ul>
 */
public class GateOrchestratorFalsificationTest {

    private TaskRepository taskRepository;
    private TaskGateLogRepository taskGateLogRepository;
    private ObjectMapper objectMapper;
    private ClientDeliverableReadinessService readinessService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        taskGateLogRepository = mock(TaskGateLogRepository.class);
        objectMapper = new ObjectMapper();
        readinessService = mock(ClientDeliverableReadinessService.class);
    }

    @Test
    @DisplayName("Falsify Popper: Empty gate set reports 0 applicable checks per stage and does not fake verification")
    void falsifyPopperFalsificationHarness_emptyGateSetCarriesZeroDenominatorPerStage() {
        // Zero gates applicable
        GateOrchestrator orchestrator = new GateOrchestrator(
                Collections.emptyList(), taskRepository, taskGateLogRepository, objectMapper, readinessService);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        orchestrator.runQualityGate(task);

        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(taskCaptor.capture());
        TaskEntity savedTask = taskCaptor.getValue();

        // Deliberately, allPassed is true (to avoid failing 8 roles without gates), but denominator is strictly 0
        assertTrue(savedTask.isQualityGatePassed());
        JsonNode report = savedTask.getQualityGateReport();
        assertNotNull(report);
        assertTrue(report.path("passed").asBoolean());

        JsonNode applicableByStage = report.path("applicableChecksByStage");
        for (GateStage stage : GateStage.values()) {
            assertEquals(0, applicableByStage.path(stage.name()).asLong(),
                    "Stage " + stage.name() + " must have 0 applicable checks");
        }

        // Must NOT qualify as verified for delivery when delivery check count is 0
        assertFalse(savedTask.isVerifiedForDelivery(),
                "Task with 0 applicable checks must NOT be verified for delivery");
        assertTrue(savedTask.isDeliveryVerificationAbsent(),
                "Task with 0 applicable checks must report delivery verification absent");
    }

    @Test
    @DisplayName("Falsify Popper: Applicable checks increment stage denominator and failure reasons are preserved")
    void falsifyPopperFalsificationHarness_applicableGateFailureRecordsExactDenominatorAndReasons() {
        GateCheck specPassCheck = mock(GateCheck.class);
        when(specPassCheck.stage()).thenReturn(GateStage.TASK_SPEC);
        when(specPassCheck.supports(any())).thenReturn(true);
        when(specPassCheck.isBuildPhaseExempt()).thenReturn(false);
        when(specPassCheck.check(any())).thenReturn(new GateResult(true, "Spec Check", List.of()));

        GateCheck implFailCheck = mock(GateCheck.class);
        when(implFailCheck.stage()).thenReturn(GateStage.IMPLEMENTATION_RESULT);
        when(implFailCheck.supports(any())).thenReturn(true);
        when(implFailCheck.isBuildPhaseExempt()).thenReturn(false);
        when(implFailCheck.check(any())).thenReturn(new GateResult(false, "Contract Check", List.of("Missing endpoint", "Bad DTO")));

        GateOrchestrator orchestrator = new GateOrchestrator(
                List.of(specPassCheck, implFailCheck), taskRepository, taskGateLogRepository, objectMapper, readinessService);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        orchestrator.runQualityGate(task);

        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(taskCaptor.capture());
        TaskEntity savedTask = taskCaptor.getValue();

        assertFalse(savedTask.isQualityGatePassed(), "Overall gate must fail when one check fails");
        JsonNode report = savedTask.getQualityGateReport();
        assertFalse(report.path("passed").asBoolean());

        assertEquals(1, report.path("applicableChecksByStage").path("TASK_SPEC").asLong());
        assertEquals(1, report.path("applicableChecksByStage").path("IMPLEMENTATION_RESULT").asLong());
        assertEquals(0, report.path("applicableChecksByStage").path("CLIENT_DELIVERABLE").asLong());

        JsonNode checks = report.path("checks");
        assertEquals(2, checks.size());

        JsonNode failedNode = checks.get(1);
        assertEquals("Contract Check", failedNode.path("name").asText());
        assertFalse(failedNode.path("passed").asBoolean());
        assertThat(failedNode.path("failureReasons").toString()).contains("Missing endpoint", "Bad DTO");
    }

    @Test
    @DisplayName("Falsify Popper Register: TaskGateLogEntity persists institutional fact with timestamp and report copy")
    void falsifyPopperInstitutionalRegister_taskGateLogEntityPersistsHistoricalRecord() {
        GateCheck check = mock(GateCheck.class);
        when(check.stage()).thenReturn(GateStage.TASK_SPEC);
        when(check.supports(any())).thenReturn(true);
        when(check.isBuildPhaseExempt()).thenReturn(false);
        when(check.check(any())).thenReturn(new GateResult(true, "Fast Spec Check", List.of()));

        GateOrchestrator orchestrator = new GateOrchestrator(
                List.of(check), taskRepository, taskGateLogRepository, objectMapper, readinessService);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        orchestrator.runQualityGate(task);

        ArgumentCaptor<TaskGateLogEntity> logCaptor = ArgumentCaptor.forClass(TaskGateLogEntity.class);
        verify(taskGateLogRepository).save(logCaptor.capture());
        TaskGateLogEntity savedLog = logCaptor.getValue();

        assertSame(task, savedLog.getTask());
        assertTrue(savedLog.isPassed());
        assertNotNull(savedLog.getCreatedAt());
        assertNotNull(savedLog.getReport());
        assertTrue(savedLog.getReport().path("passed").asBoolean());
        assertEquals(1, savedLog.getReport().path("applicableChecksByStage").path("TASK_SPEC").asLong());
    }

    @Test
    @DisplayName("Falsify Raz Matrix: runTaskSpecGate restricts execution strictly to TASK_SPEC stage")
    void falsifyRazStageFilter_runTaskSpecGateOnlyExecutesTaskSpecStageChecks() {
        GateCheck specCheck = mock(GateCheck.class);
        when(specCheck.stage()).thenReturn(GateStage.TASK_SPEC);
        when(specCheck.supports(any())).thenReturn(true);
        when(specCheck.check(any())).thenReturn(new GateResult(true, "Spec Check", List.of()));

        GateCheck implCheck = mock(GateCheck.class);
        when(implCheck.stage()).thenReturn(GateStage.IMPLEMENTATION_RESULT);
        when(implCheck.supports(any())).thenReturn(true);
        when(implCheck.check(any())).thenReturn(new GateResult(true, "Impl Check", List.of()));

        GateOrchestrator orchestrator = new GateOrchestrator(
                List.of(specCheck, implCheck), taskRepository, taskGateLogRepository, objectMapper, readinessService);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        orchestrator.runTaskSpecGate(task);

        verify(specCheck, times(1)).check(task);
        verify(implCheck, never()).check(task);

        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(taskCaptor.capture());
        JsonNode stages = taskCaptor.getValue().getQualityGateReport().path("stages");
        assertEquals(1, stages.size());
        assertEquals("TASK_SPEC", stages.get(0).asText());
    }

    @Test
    @DisplayName("Falsify Raz Matrix: Build phase exempt gates are skipped when project is in build phase")
    void falsifyRazBuildPhase_exemptChecksAreSkippedDuringBuildPhase() {
        UUID projectId = UUID.randomUUID();
        when(readinessService.isBuildPhase(projectId)).thenReturn(true);

        com.eneik.production.models.persistence.ProjectEntity project = new com.eneik.production.models.persistence.ProjectEntity();
        project.setId(projectId);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);

        GateCheck exemptCheck = mock(GateCheck.class);
        when(exemptCheck.stage()).thenReturn(GateStage.IMPLEMENTATION_RESULT);
        when(exemptCheck.supports(any())).thenReturn(true);
        when(exemptCheck.isBuildPhaseExempt()).thenReturn(true);

        GateCheck nonExemptCheck = mock(GateCheck.class);
        when(nonExemptCheck.stage()).thenReturn(GateStage.IMPLEMENTATION_RESULT);
        when(nonExemptCheck.supports(any())).thenReturn(true);
        when(nonExemptCheck.isBuildPhaseExempt()).thenReturn(false);
        when(nonExemptCheck.check(any())).thenReturn(new GateResult(true, "Non-exempt Check", List.of()));

        GateOrchestrator orchestrator = new GateOrchestrator(
                List.of(exemptCheck, nonExemptCheck), taskRepository, taskGateLogRepository, objectMapper, readinessService);

        orchestrator.runQualityGate(task);

        verify(exemptCheck, never()).check(task);
        verify(nonExemptCheck, times(1)).check(task);
    }

    @Test
    @DisplayName("Falsify Raz Matrix: Unsupported gates for specific task role are never executed")
    void falsifyRazSupportsPredicate_unsupportedGatesAreSkipped() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        GateCheck supportedCheck = mock(GateCheck.class);
        when(supportedCheck.stage()).thenReturn(GateStage.TASK_SPEC);
        when(supportedCheck.supports(task)).thenReturn(true);
        when(supportedCheck.check(task)).thenReturn(new GateResult(true, "Supported", List.of()));

        GateCheck unsupportedCheck = mock(GateCheck.class);
        when(unsupportedCheck.stage()).thenReturn(GateStage.TASK_SPEC);
        when(unsupportedCheck.supports(task)).thenReturn(false);

        GateOrchestrator orchestrator = new GateOrchestrator(
                List.of(supportedCheck, unsupportedCheck), taskRepository, taskGateLogRepository, objectMapper, readinessService);

        orchestrator.runQualityGate(task);

        verify(supportedCheck, times(1)).check(task);
        verify(unsupportedCheck, never()).check(task);
    }
}
