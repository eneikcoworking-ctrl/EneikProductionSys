package com.eneik.production.controllers;

import com.eneik.production.models.persistence.OnboardingAuditFindingEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityMetricsControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getDefectSummary_NeverCallsTaskFindAll_UsesFindByQualityGateReportIsNotNull() {
        PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
        TaskConflictRepository taskConflictRepository = mock(TaskConflictRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        OnboardingAuditFindingRepository onboardingRepo = mock(OnboardingAuditFindingRepository.class);

        QualityMetricsController controller = new QualityMetricsController(
                prReviewRepository, taskConflictRepository, julesSessionRepository,
                taskRepository, projectRepository, onboardingRepo);

        // 1 conflict
        TaskConflictEntity conflict = new TaskConflictEntity();
        conflict.setId(UUID.randomUUID());
        conflict.setConflictType("merge_conflict");
        conflict.setResolutionStatus("open");
        when(taskConflictRepository.findAll()).thenReturn(List.of(conflict));

        // 2 tasks with reports: task1 has 1 failed check, task2 has 1 passed check
        TaskEntity task1 = new TaskEntity();
        task1.setId(UUID.randomUUID());
        ObjectNode report1 = objectMapper.createObjectNode();
        ArrayNode checks1 = report1.putArray("checks");
        ObjectNode check1Fail = checks1.addObject();
        check1Fail.put("name", "UnitTestsCheck");
        check1Fail.put("passed", false);
        check1Fail.put("description", "Failed unit tests");
        ObjectNode check1Pass = checks1.addObject();
        check1Pass.put("name", "LintCheck");
        check1Pass.put("passed", true);
        task1.setQualityGateReport(report1);

        TaskEntity task2 = new TaskEntity();
        task2.setId(UUID.randomUUID());
        ObjectNode report2 = objectMapper.createObjectNode();
        ArrayNode checks2 = report2.putArray("checks");
        ObjectNode check2Pass = checks2.addObject();
        check2Pass.put("name", "CheckstyleCheck");
        check2Pass.put("passed", true);
        task2.setQualityGateReport(report2);

        when(taskRepository.findByQualityGateReportIsNotNull()).thenReturn(List.of(task1, task2));

        // 1 onboarding finding
        OnboardingAuditFindingEntity finding = new OnboardingAuditFindingEntity();
        finding.setId(UUID.randomUUID());
        finding.setRoleTag("TECH_LEAD");
        finding.setSeverity("HIGH");
        finding.setFindingText("Missing build tool");
        when(onboardingRepo.findAll()).thenReturn(List.of(finding));

        Map<String, Object> summary = controller.getDefectSummary();

        // Architectural invariant: must NOT call taskRepository.findAll()
        verify(taskRepository, never()).findAll();
        verify(taskRepository).findByQualityGateReportIsNotNull();

        assertThat(summary.get("totalDefects")).isEqualTo(3L); // 1 conflict + 1 failed check + 1 onboarding

        @SuppressWarnings("unchecked")
        Map<String, Object> qualityGate = (Map<String, Object>) summary.get("qualityGate");
        assertThat(qualityGate.get("total")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) qualityGate.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("checkName")).isEqualTo("UnitTestsCheck");
        assertThat(items.get(0).get("taskId")).isEqualTo(task1.getId());
    }

    @Test
    void getDefectSummary_ZeroFailedChecksProducesZeroQualityGateDefects() {
        // NUEL_BELNAP_03_TRUTH_STATUS_TABLE & DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT:
        // Tasks where no checks failed (including 0 applied checks) must produce 0 quality gate defects.
        PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
        TaskConflictRepository taskConflictRepository = mock(TaskConflictRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        OnboardingAuditFindingRepository onboardingRepo = mock(OnboardingAuditFindingRepository.class);

        QualityMetricsController controller = new QualityMetricsController(
                prReviewRepository, taskConflictRepository, julesSessionRepository,
                taskRepository, projectRepository, onboardingRepo);

        when(taskConflictRepository.findAll()).thenReturn(List.of());
        when(onboardingRepo.findAll()).thenReturn(List.of());

        // Task with report but empty checks (0 delivery checks applied)
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        ObjectNode report = objectMapper.createObjectNode();
        report.putArray("checks");
        task.setQualityGateReport(report);

        when(taskRepository.findByQualityGateReportIsNotNull()).thenReturn(List.of(task));

        Map<String, Object> summary = controller.getDefectSummary();

        assertThat(summary.get("totalDefects")).isEqualTo(0L);

        @SuppressWarnings("unchecked")
        Map<String, Object> qualityGate = (Map<String, Object>) summary.get("qualityGate");
        assertThat(qualityGate.get("total")).isEqualTo(0L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) qualityGate.get("items");
        assertThat(items).isEmpty();
    }

    @Test
    void getConflictDpmo_UsesRepositoryCountsInsteadOfFindAll() {
        PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
        TaskConflictRepository taskConflictRepository = mock(TaskConflictRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        OnboardingAuditFindingRepository onboardingRepo = mock(OnboardingAuditFindingRepository.class);

        QualityMetricsController controller = new QualityMetricsController(
                prReviewRepository, taskConflictRepository, julesSessionRepository,
                taskRepository, projectRepository, onboardingRepo);

        when(prReviewRepository.countByMergedTrue()).thenReturn(90L);
        when(taskConflictRepository.count()).thenReturn(10L);
        when(prReviewRepository.countByMergedTrueAndCreatedAtAfter(any(Instant.class))).thenReturn(45L);
        when(taskConflictRepository.countByDetectedAtAfter(any(Instant.class))).thenReturn(5L);
        when(projectRepository.findAll()).thenReturn(List.of());

        Map<String, Object> result = controller.getConflictDpmo();

        // Verify count methods were called and findAll was NEVER called on reviews
        verify(prReviewRepository).countByMergedTrue();
        verify(taskConflictRepository).count();
        verify(prReviewRepository).countByMergedTrueAndCreatedAtAfter(any(Instant.class));
        verify(taskConflictRepository).countByDetectedAtAfter(any(Instant.class));
        verify(prReviewRepository, never()).findAll();

        assertThat(result.get("totalMergeAttempts")).isEqualTo(100L);
        assertThat(result.get("conflicts")).isEqualTo(10L);
        assertThat(result.get("dpmo")).isEqualTo(100_000.0);

        @SuppressWarnings("unchecked")
        Map<String, Object> last7Days = (Map<String, Object>) result.get("last7Days");
        assertThat(last7Days.get("totalMergeAttempts")).isEqualTo(50L);
        assertThat(last7Days.get("conflicts")).isEqualTo(5L);
        assertThat(last7Days.get("dpmo")).isEqualTo(100_000.0);
    }
}
