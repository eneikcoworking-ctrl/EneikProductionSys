package com.eneik.production.services;

import com.eneik.production.dto.dashboard.ClientDeliveryDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectFinalReportEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectFinalReportRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 2 для V15 (project_final_reports / сохранение снапшота сдачи):
 *
 * 1. DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT [D010 Data lineage loss]:
 *    - Персистентный итоговый отчет поставки (ProjectFinalReportEntity):
 *      * Точная фиксация количества выполненных задач (totalTasksCompleted) и запрошенных требований (totalWishlistItems).
 *      * Сохранение глубокого неизменяемого JSON-снапшота (report_content) со всеми реквизитами сдачи:
 *        delivered, requested, screenshots, prLinks, testSummary.
 *      * Иммутабельность во времени (Temporal Continuity / Replay):
 *        Последующие изменения или удаление задач в базе данных не изменяют однажды зафиксированный снапшот.
 *      * Round-trip десериализация: восстановление исходного ClientDeliveryDto без потерь информации.
 *
 * 2. DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER [D007 Evidence gap]:
 *    - Институциональный факт клиентской приёмки (acceptProject в ProjectFlowService):
 *      * Остановка генерации компилятора (technicalLeadCompiler.stopGeneration).
 *      * Получение среза сдачи через ClientDeliveryService.getDelivery.
 *      * Персистентное сохранение финального отчета в ProjectFinalReportRepository.
 *      * Переход статуса проекта в терминальное состояние 'accepted' с фиксацией acceptedAt.
 *      * Пресечение любых фоновых активностей (cancelAllActiveWorkForProject), гарантирующее отсутствие мутаций после сдачи.
 */
class V15ProjectFinalReportPersistenceFalsificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("DEREK_PARFIT_01: Персистентный снапшот сдачи и иммутабельность идентичности (D010)")
    class PersistenceSnapshotTests {

        @Test
        @DisplayName("PARFIT_01: saveFinalReport материализует полный снапшот сдачи в ProjectFinalReportEntity")
        void saveFinalReportMaterializesCompleteDeliverySnapshot() {
            ProjectFinalReportRepository reportRepository = mock(ProjectFinalReportRepository.class);
            UUID projectId = UUID.randomUUID();

            List<Map<String, Object>> requested = List.of(
                    Map.of("id", UUID.randomUUID().toString(), "content", "Requirement 1: Authentication"),
                    Map.of("id", UUID.randomUUID().toString(), "content", "Requirement 2: Billing API")
            );

            List<Map<String, Object>> delivered = List.of(
                    Map.of("id", UUID.randomUUID().toString(), "title", "Implement Auth Controller", "status", "done"),
                    Map.of("id", UUID.randomUUID().toString(), "title", "Setup DB Migrations", "status", "done"),
                    Map.of("id", UUID.randomUUID().toString(), "title", "Configure Security Filter", "status", "done")
            );

            List<String> screenshots = List.of("https://s3.local/screen1.png", "https://s3.local/screen2.png");
            List<String> prLinks = List.of("https://github.com/org/repo/pull/1", "https://github.com/org/repo/pull/2");
            String testSummary = "100% tests passed (42/42 green, 0 failures)";

            ClientDeliveryDto snapshot = new ClientDeliveryDto(requested, delivered, screenshots, prLinks, testSummary);

            ProjectFlowService flowService = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(flowService, "projectFinalReportRepository", reportRepository);
            ReflectionTestUtils.setField(flowService, "objectMapper", objectMapper);

            // Вызов приватного метода сохранения снапшота
            ReflectionTestUtils.invokeMethod(flowService, "saveFinalReport", projectId, snapshot);

            ArgumentCaptor<ProjectFinalReportEntity> captor = ArgumentCaptor.forClass(ProjectFinalReportEntity.class);
            verify(reportRepository).save(captor.capture());

            ProjectFinalReportEntity savedReport = captor.getValue();
            assertThat(savedReport).isNotNull();
            assertThat(savedReport.getProjectId()).isEqualTo(projectId);
            assertThat(savedReport.getTotalTasksCompleted()).isEqualTo(3);
            assertThat(savedReport.getTotalWishlistItems()).isEqualTo(2);
            assertThat(savedReport.getGeneratedAt()).isNotNull();

            // Проверка структуры JSON
            JsonNode content = savedReport.getReportContent();
            assertThat(content).isNotNull();
            assertThat(content.has("requested")).isTrue();
            assertThat(content.get("requested").size()).isEqualTo(2);
            assertThat(content.has("delivered")).isTrue();
            assertThat(content.get("delivered").size()).isEqualTo(3);
            assertThat(content.get("screenshots").size()).isEqualTo(2);
            assertThat(content.get("prLinks").size()).isEqualTo(2);
            assertThat(content.get("testSummary").asText()).isEqualTo(testSummary);
        }

        @Test
        @DisplayName("PARFIT_01: Иммутабельность снапшота во времени защищена от мутаций исходных коллекций")
        void snapshotContentIsImmutableAndProtectedFromLiveMutations() throws Exception {
            ProjectFinalReportRepository reportRepository = mock(ProjectFinalReportRepository.class);
            UUID projectId = UUID.randomUUID();

            List<Map<String, Object>> mutableRequested = new ArrayList<>();
            mutableRequested.add(Map.of("key", "wish1"));

            List<Map<String, Object>> mutableDelivered = new ArrayList<>();
            mutableDelivered.add(Map.of("key", "task1"));

            ClientDeliveryDto snapshot = new ClientDeliveryDto(
                    mutableRequested, mutableDelivered, List.of("s1"), List.of("pr1"), "Initial green"
            );

            ProjectFlowService flowService = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(flowService, "projectFinalReportRepository", reportRepository);
            ReflectionTestUtils.setField(flowService, "objectMapper", objectMapper);

            ReflectionTestUtils.invokeMethod(flowService, "saveFinalReport", projectId, snapshot);

            ArgumentCaptor<ProjectFinalReportEntity> captor = ArgumentCaptor.forClass(ProjectFinalReportEntity.class);
            verify(reportRepository).save(captor.capture());
            ProjectFinalReportEntity report = captor.getValue();

            // Симулируем мутацию исходного списка после сохранения
            mutableDelivered.clear();
            mutableRequested.add(Map.of("key", "wish2_post_delivery"));

            // Проверяем, что в сохраненном снапшоте ничего не изменилось (Parfit Persistence Continuity)
            JsonNode reportContent = report.getReportContent();
            assertThat(reportContent.get("delivered").size()).isEqualTo(1);
            assertThat(reportContent.get("requested").size()).isEqualTo(1);

            // Проверяем round-trip десериализацию
            ClientDeliveryDto restored = objectMapper.treeToValue(reportContent, ClientDeliveryDto.class);
            assertThat(restored.delivered()).hasSize(1);
            assertThat(restored.requested()).hasSize(1);
            assertThat(restored.testSummary()).isEqualTo("Initial green");
        }

        @Test
        @DisplayName("PARFIT_01: Граничные условия пустой сдачи корректно сохраняются без NPE")
        void emptyDeliveryBoundaryConditionsPersistSafely() {
            ProjectFinalReportRepository reportRepository = mock(ProjectFinalReportRepository.class);
            UUID projectId = UUID.randomUUID();

            ClientDeliveryDto emptySnapshot = new ClientDeliveryDto(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null
            );

            ProjectFlowService flowService = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(flowService, "projectFinalReportRepository", reportRepository);
            ReflectionTestUtils.setField(flowService, "objectMapper", objectMapper);

            ReflectionTestUtils.invokeMethod(flowService, "saveFinalReport", projectId, emptySnapshot);

            ArgumentCaptor<ProjectFinalReportEntity> captor = ArgumentCaptor.forClass(ProjectFinalReportEntity.class);
            verify(reportRepository).save(captor.capture());

            ProjectFinalReportEntity report = captor.getValue();
            assertThat(report.getTotalTasksCompleted()).isEqualTo(0);
            assertThat(report.getTotalWishlistItems()).isEqualTo(0);
            assertThat(report.getReportContent()).isNotNull();
            assertThat(report.getReportContent().get("delivered").size()).isEqualTo(0);
            assertThat(report.getReportContent().get("requested").size()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("DZHON_SERL_05: Реестр институциональных фактов приёмки проекта (D007)")
    class InstitutionalFactRegisterTests {

        @Test
        @DisplayName("SERLE_05: acceptProject фиксирует институциональный факт сдачи и статус accepted")
        void acceptProjectEstablishesTerminalAcceptedFact() {
            UUID projectId = UUID.randomUUID();
            ProjectEntity project = new ProjectEntity();
            project.setId(projectId);
            project.setName("test-delivery-project");
            project.setStatus(ProjectStatus.active);

            ProjectRepository projectRepository = mock(ProjectRepository.class);
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

            TechnicalLeadCompiler technicalLeadCompiler = mock(TechnicalLeadCompiler.class);
            ClientDeliveryService clientDeliveryService = mock(ClientDeliveryService.class);
            ProjectFinalReportRepository projectFinalReportRepository = mock(ProjectFinalReportRepository.class);
            TaskRepository taskRepository = mock(TaskRepository.class);
            JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
            JulesDispatchService julesDispatchService = mock(JulesDispatchService.class);
            ClaimService claimService = mock(ClaimService.class);
            GitHubPullRequestService gitHubPullRequestService = mock(GitHubPullRequestService.class);

            ClientDeliveryDto deliveryDto = new ClientDeliveryDto(
                    List.of(Map.of("wish", "w1")),
                    List.of(Map.of("task", "t1")),
                    List.of("screen.png"),
                    List.of("https://pr/1"),
                    "All 120 tests passed"
            );
            when(clientDeliveryService.getDelivery(projectId)).thenReturn(deliveryDto);

            // Создаем задачу предшествующего проекта, ожидающую завершения
            UUID activeTaskId = UUID.randomUUID();
            TaskEntity remainingTask = new TaskEntity();
            remainingTask.setId(activeTaskId);
            remainingTask.setStatus(TaskStatus.in_progress);

            when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                    .thenReturn(List.of(remainingTask));
            when(julesSessionRepository.findByTaskId(activeTaskId)).thenReturn(Collections.emptyList());

            AccountRepository accountRepository = mock(AccountRepository.class);
            when(accountRepository.findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc())
                    .thenReturn(Collections.emptyList());

            ProjectFlowService flowService = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(flowService, "projectRepository", projectRepository);
            ReflectionTestUtils.setField(flowService, "accountRepository", accountRepository);
            ReflectionTestUtils.setField(flowService, "technicalLeadCompiler", technicalLeadCompiler);
            ReflectionTestUtils.setField(flowService, "clientDeliveryService", clientDeliveryService);
            ReflectionTestUtils.setField(flowService, "projectFinalReportRepository", projectFinalReportRepository);
            ReflectionTestUtils.setField(flowService, "taskRepository", taskRepository);
            ReflectionTestUtils.setField(flowService, "julesSessionRepository", julesSessionRepository);
            ReflectionTestUtils.setField(flowService, "julesDispatchService", julesDispatchService);
            ReflectionTestUtils.setField(flowService, "claimService", claimService);
            ReflectionTestUtils.setField(flowService, "gitHubPullRequestService", gitHubPullRequestService);
            ReflectionTestUtils.setField(flowService, "objectMapper", objectMapper);

            // Выполняем переход приёмки проекта
            flowService.acceptProject(projectId);

            // 1. Остановка генерации компилятора
            verify(technicalLeadCompiler).stopGeneration(projectId);

            // 2. Персистентное сохранение финального отчета (V15)
            ArgumentCaptor<ProjectFinalReportEntity> reportCaptor = ArgumentCaptor.forClass(ProjectFinalReportEntity.class);
            verify(projectFinalReportRepository).save(reportCaptor.capture());
            ProjectFinalReportEntity savedReport = reportCaptor.getValue();
            assertThat(savedReport.getProjectId()).isEqualTo(projectId);
            assertThat(savedReport.getTotalTasksCompleted()).isEqualTo(1);
            assertThat(savedReport.getTotalWishlistItems()).isEqualTo(1);

            // 3. Терминальный статус проекта
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.accepted);
            assertThat(project.getAcceptedAt()).isNotNull();
            verify(projectRepository).save(project);

            // 4. Отмена всех активных работ и закрытие PR
            verify(claimService).closeTaskAsFailed(eq(activeTaskId), contains("Project accepted: client took final delivery"));
            verify(gitHubPullRequestService).closeOpenPullRequests(eq(project), contains("Project accepted: client took final delivery"));
        }
    }
}
