package com.eneik.production.services;

import com.eneik.production.dto.operational.OperationalTruthDto;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.InvariantStatusChangeRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProcessControlSnapshotRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.ReviewConcernRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.TrustSignalSnapshotRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.audit.SixSigmaAuditService.DefectOpportunityCount;
import com.eneik.production.services.design.sovereign.service.DefaultPlaywrightVisionHarness;
import com.eneik.production.services.operational.OperationalTruthService;
import com.eneik.production.services.operational.TrustSnapshotService;
import com.eneik.production.services.quality.ProcessControlService;
import com.eneik.production.services.worker.agy.LocalAgyCliClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Falsification gate for Mechanism #9 Step B (D005 / D010 / D013):
 * guarantees that long-running @Scheduled sweeps and network-calling merge paths never
 * hold a JDBC connection open across network I/O or full-project loops, that ProcessControlService
 * eliminates N+1 findByFeatureId queries, and that thread/HttpClient resources are strictly bounded.
 */
class HikariLeakAndThreadHygieneGateTest {

    @Test
    void scheduledAndNetworkPathsMustNotDeclareOuterTransactionalAnnotation() throws Exception {
        Method executeMerge = AutoMergeService.class.getDeclaredMethod("executeMerge", PrReviewEntity.class);
        assertNull(executeMerge.getAnnotation(Transactional.class),
                "AutoMergeService.executeMerge performs GitHub HTTP I/O and must NOT be @Transactional");

        Method deleteValuelessEpics = ClientDeliverableReadinessService.class.getDeclaredMethod("deleteValuelessEpics");
        assertNull(deleteValuelessEpics.getAnnotation(Transactional.class),
                "ClientDeliverableReadinessService.deleteValuelessEpics must NOT hold one @Transactional across all projects");

        Method captureAndBackfill = TrustSnapshotService.class.getDeclaredMethod("captureAndBackfillSnapshots");
        assertNull(captureAndBackfill.getAnnotation(Transactional.class),
                "TrustSnapshotService.captureAndBackfillSnapshots must NOT hold one @Transactional across build() + backfill");

        Method recomputeForProject = ProcessControlService.class.getDeclaredMethod("recomputeForProject", UUID.class);
        assertNull(recomputeForProject.getAnnotation(Transactional.class),
                "ProcessControlService.recomputeForProject must NOT hold one @Transactional across full recompute");
    }

    @Test
    void trustSnapshotBuildExecutesOutsideActiveTransaction() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        OperationalTruthService operationalTruthService = mock(OperationalTruthService.class);
        TrustSignalSnapshotRepository snapshotRepository = mock(TrustSignalSnapshotRepository.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        InvariantStatusChangeRepository invariantChangeRepository = mock(InvariantStatusChangeRepository.class);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(snapshotRepository.findByEventualOutcomeIsNull()).thenReturn(List.of());

        AtomicBoolean txActiveDuringBuild = new AtomicBoolean(true);
        var evidence = new OperationalTruthDto.EvidenceSummary(0, 0, 0, 0, 0, 0, 0, List.of());
        var defects = new OperationalTruthDto.DefectSummary(0, List.of());
        var trust = new OperationalTruthDto.Trust(0.9, "healthy", List.of(), List.of());
        OperationalTruthDto dto = new OperationalTruthDto(
                null, null, null, null, trust, null, null, evidence, defects, null,
                null, List.of(), null, null, null);
        when(operationalTruthService.build(projectId)).thenAnswer(inv -> {
            txActiveDuringBuild.set(TransactionSynchronizationManager.isActualTransactionActive());
            return dto;
        });

        TrustSnapshotService service = new TrustSnapshotService(
                projectRepository, operationalTruthService, snapshotRepository, readinessService, invariantChangeRepository);
        service.captureAndBackfillSnapshots();

        assertFalse(txActiveDuringBuild.get(),
                "operationalTruthService.build() must execute outside any active DB transaction");
        verify(snapshotRepository).save(any());
    }

    @Test
    void processControlServiceUsesBatchFindByFeatureIdInInsteadOfNPlusOne() {
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProcessControlSnapshotRepository snapshotRepository = mock(ProcessControlSnapshotRepository.class);
        SixSigmaAuditService sixSigmaAuditService = mock(SixSigmaAuditService.class);
        ReviewConcernRepository reviewConcernRepository = mock(ReviewConcernRepository.class);
        PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
        TaskConflictRepository taskConflictRepository = mock(TaskConflictRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
        DefectJournalRepository defectJournalRepository = mock(DefectJournalRepository.class);
        KaizenService kaizenService = mock(KaizenService.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);

        UUID projectId = UUID.randomUUID();
        UUID feature1Id = UUID.randomUUID();
        UUID feature2Id = UUID.randomUUID();

        FeatureEntity f1 = new FeatureEntity();
        f1.setId(feature1Id);
        f1.setProjectId(projectId);
        f1.setCreatedAt(Instant.now().minusSeconds(7200));

        FeatureEntity f2 = new FeatureEntity();
        f2.setId(feature2Id);
        f2.setProjectId(projectId);
        f2.setCreatedAt(Instant.now().minusSeconds(3600));

        TaskEntity t1 = new TaskEntity();
        t1.setId(UUID.randomUUID());
        t1.setFeatureId(feature1Id);
        t1.setStatus(TaskStatus.done);
        t1.setUpdatedAt(Instant.now().minusSeconds(6000));

        TaskEntity t2 = new TaskEntity();
        t2.setId(UUID.randomUUID());
        t2.setFeatureId(feature2Id);
        t2.setStatus(TaskStatus.done);
        t2.setUpdatedAt(Instant.now().minusSeconds(3000));

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(f1, f2));
        when(taskRepository.findByFeatureIdIn(any())).thenReturn(List.of(t1, t2));
        when(julesSessionRepository.findByTaskIdIn(any())).thenReturn(List.of());
        when(taskConflictRepository.findByTaskIdIn(any())).thenReturn(List.of());
        when(snapshotRepository.findByProjectIdAndStreamOrderBySequenceIndexAsc(any(), any())).thenReturn(List.of());
        when(snapshotRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sixSigmaAuditService.computeQualityGateCounts(any(), any())).thenReturn(new DefectOpportunityCount(0, 1));
        when(sixSigmaAuditService.computePrConflictCounts(any(), any(), any(), any())).thenReturn(new DefectOpportunityCount(0, 1));

        ProcessControlService service = new ProcessControlService(
                featureRepository, taskRepository, snapshotRepository, sixSigmaAuditService,
                reviewConcernRepository, prReviewRepository, taskConflictRepository, julesSessionRepository,
                defectJournalRepository, kaizenService, projectRepository, null);

        service.recomputeForProject(projectId);

        verify(taskRepository, atLeastOnce()).findByFeatureIdIn(any());
        verify(taskRepository, never()).findByFeatureId(any());
    }

    @Test
    void nativeThreadPoolsAndSharedHttpClientsAreBounded() throws Exception {
        DefaultPlaywrightVisionHarness harness = new DefaultPlaywrightVisionHarness();
        try {
            Object executor = ReflectionTestUtils.getField(harness, "executor");
            assertInstanceOf(ThreadPoolExecutor.class, executor);
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
            assertTrue(tpe.getMaximumPoolSize() <= 4,
                    "DefaultPlaywrightVisionHarness must use a bounded thread pool, not newCachedThreadPool()");
        } finally {
            harness.shutdown();
        }

        LocalAgyCliClient cliClient = new LocalAgyCliClient(null, null, null);
        try {
            Object streamPool = ReflectionTestUtils.getField(cliClient, "streamReaderPool");
            assertInstanceOf(ThreadPoolExecutor.class, streamPool);
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) streamPool;
            assertTrue(tpe.getMaximumPoolSize() <= 8,
                    "LocalAgyCliClient must use a bounded daemon pool instead of unbounded raw new Thread()");
        } finally {
            cliClient.shutdown();
        }

        // Verify ProjectFlowService declares a reusable sharedHttpClient field
        var field = ProjectFlowService.class.getDeclaredField("sharedHttpClient");
        assertNotNull(field);
        assertTrue(HttpClient.class.isAssignableFrom(field.getType()));
    }
}
