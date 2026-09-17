package com.eneik.production.services;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.dashboard.EmsMetricsService;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Заслон для Предписания 39 (TELEOSEMANTIC_FEEDBACK [D011] + FALSIFICATION_HARNESS [D008]):
 * Проверка Drum-Buffer-Rope верёвки на входе отправки (ProjectFlowService.dispatchQueuedTasks):
 * 1. Верёвка душит выпуск работы (dispatchQueuedTasks) при переполнении буфера ограничения.
 * 2. Буфер измеряется по внешней ёмкости Jules-аккаунтов (estimatedDailyCapacity).
 * 3. Ноль срабатываний ограничителя эксплицитно виден в телеметрии как UNVERIFIED.
 */
class ProjectFlowServiceDbrRopeTest {

    private ProjectRepository projectRepository;
    private TaskRepository taskRepository;
    private OperationalPolicyService operationalPolicyService;
    private ProjectFlowService service;

    private TocExecutionGraph graph;
    private TocOptimizer optimizer;
    private TocSentinelService sentinelService;

    private UUID projectId;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        taskRepository = mock(TaskRepository.class);
        operationalPolicyService = mock(OperationalPolicyService.class);

        projectId = UUID.randomUUID();
        project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        graph = new TocExecutionGraph();
        optimizer = new TocOptimizer(graph, 2);
        sentinelService = new TocSentinelService(graph, new TocAnomalyDetector(graph), optimizer);

        service = new ProjectFlowService(
                projectRepository,
                mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(AccountRepository.class),
                taskRepository,
                mock(com.eneik.production.repositories.ClaimRepository.class),
                mock(com.eneik.production.repositories.RoleRepository.class),
                mock(ClaimService.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(ProjectFactoryService.class),
                mock(GitHubProjectFactoryClient.class),
                mock(SystemSettingsService.class),
                null,
                null,
                mock(TechnicalLeadCompiler.class),
                mock(ClientDeliveryService.class),
                mock(com.eneik.production.repositories.ProjectFinalReportRepository.class),
                mock(com.eneik.production.repositories.JulesSessionRepository.class),
                mock(com.eneik.production.repositories.JulesActivityResponseRepository.class),
                mock(com.eneik.production.repositories.ProjectGenerationStateRepository.class),
                mock(com.fasterxml.jackson.databind.ObjectMapper.class),
                "test-org",
                mock(com.eneik.production.services.onboarding.OnboardingAuditService.class),
                mock(EmsMetricsService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.design.DesignAssetService.class),
                mock(com.eneik.production.services.github.GitHubPullRequestService.class),
                mock(ClientDeliverableReadinessService.class),
                mock(FeatureService.class),
                mock(PersistentWorkerSessionService.class),
                mock(SelfFalsificationEpicMatcher.class),
                operationalPolicyService,
                mock(com.eneik.production.repositories.ProjectFileClaimRepository.class),
                mock(RequirementGroundingService.class),
                mock(GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.repositories.LinearIssueMetadataRepository.class),
                mock(com.eneik.production.repositories.FeatureRepository.class),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(PlannedWorkRecoveryService.class),
                null
        );
        service.setTocSentinelService(sentinelService);
    }

    @Test
    @DisplayName("Заслон: отправка отклоняется верёвкой DBR при переполнении буфера ограничения")
    void dispatchQueuedTasksIsThrottledWhenConstraintBufferOverflows() {
        // Заполняем буфер ограничения выше maxBufferCapacity (2)
        TocNode node = graph.getOrCreateNode("JULES_CAPACITY_CONSTRAINT");
        node.incrementInFlight();
        node.incrementInFlight(); // 2 >= 2
        node.recordExecution(10_000_000_000L, true);

        optimizer.evaluateConstraintsAndDbr();
        assertThat(optimizer.isRopeThrottlingActive()).isTrue();

        service.dispatchQueuedTasks(projectId);

        // Диспетчеризация обязана быть прервана до выборки очереди задач
        verify(taskRepository, never()).findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(any(), any());
        assertThat(optimizer.getTotalThrottleActivations()).isEqualTo(1);
        assertThat(optimizer.getLatestDbrStatus().isLimiterVerified()).isTrue();
        assertThat(optimizer.getLatestDbrStatus().limiterStatus()).contains("VERIFIED (1 throttle events observed)");
    }

    @Test
    @DisplayName("Отправка беспрепятственно выполняется, когда буфер ограничения свободен")
    void dispatchQueuedTasksProceedsWhenConstraintBufferWithinCapacity() {
        // Буфер пуст (0 < 2)
        optimizer.evaluateConstraintsAndDbr();
        assertThat(optimizer.isRopeThrottlingActive()).isFalse();

        service.dispatchQueuedTasks(projectId);

        // Очередь задач запрашивается штатно
        verify(taskRepository).findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(projectId, com.eneik.production.models.persistence.TaskStatus.queued);
        assertThat(optimizer.getTotalThrottleActivations()).isEqualTo(0);
    }

    @Test
    @DisplayName("FALSIFICATION_HARNESS: статус ограничителя UNVERIFIED до первого реального срабатывания")
    void limiterStatusReportsUnverifiedWhenZeroThrottlingHasOccurred() {
        assertThat(sentinelService.getDbrStatus().isLimiterVerified()).isFalse();
        assertThat(sentinelService.getDbrStatus().limiterStatus()).contains("UNVERIFIED");
    }

    @Test
    @DisplayName("Ёмкость буфера динамически выводится из estimatedDailyCapacity доступных аккаунтов")
    void maxBufferCapacityDerivesFromOperationalAccounts() {
        AccountRepository accountRepo = mock(AccountRepository.class);

        AccountEntity acc1 = new AccountEntity();
        acc1.setEnabled(true);
        acc1.setStatus(AccountStatus.idle);
        acc1.setEstimatedDailyCapacity(12);

        AccountEntity acc2 = new AccountEntity();
        acc2.setEnabled(true);
        acc2.setStatus(AccountStatus.idle);
        acc2.setEstimatedDailyCapacity(8);

        AccountEntity decommissioned = new AccountEntity();
        decommissioned.setEnabled(true);
        decommissioned.setStatus(AccountStatus.decommissioned);
        decommissioned.setEstimatedDailyCapacity(50);

        when(accountRepo.findAll()).thenReturn(List.of(acc1, acc2, decommissioned));

        optimizer.setAccountRepository(accountRepo);
        optimizer.evaluateConstraintsAndDbr();

        // acc1 (12) + acc2 (8) = 20, decommissioned исключён
        assertThat(optimizer.getMaxBufferCapacity()).isEqualTo(20);
    }
}
