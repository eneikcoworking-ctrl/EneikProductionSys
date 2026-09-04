package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Law 5 (Закон независимого свидетеля) Test Suite:
 *
 *   доставка   ⟸ реально слитый diff
 *   работа     ⟸ реально запущенный продукт
 *   дизайн     ⟸ реально отданный HTML
 *
 *   done(τ) ⇏ merged(τ)
 *   ℒ₂ ⇏ ℒ₃    слияние всех PR не есть доставка ценности
 *
 * "Сторона, производящая результат, не может быть единственным подтверждением его корректности.
 *  Три наблюдателя, ни один не заменяет другого.
 *  Утверждение «ни одна метрика ценности не считает status = done за доставку без проверки слияния»
 *  обязано иметь строгий проверяемый заслон."
 */
class IndependentWitnessLaw5Test {

    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
    private final FeatureThreadRepository featureThreadRepository = mock(FeatureThreadRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final OperationalPolicyService operationalPolicyService = mock(OperationalPolicyService.class);
    private final OperationalRealityFindingRepository findingRepository = mock(OperationalRealityFindingRepository.class);
    private final EvidenceNodeRepository evidenceNodeRepository = mock(EvidenceNodeRepository.class);
    private final PlannedWorkRecoveryService plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);

    private ClientDeliverableReadinessService readinessService;
    private DeliveryRealityProducerService deliveryRealityService;

    @BeforeEach
    void setUp() {
        readinessService = new ClientDeliverableReadinessService(
                wishlistRepository, featureRepository, taskRepository,
                julesSessionRepository, prReviewRepository, featureThreadRepository,
                projectRepository, operationalPolicyService);

        deliveryRealityService = new DeliveryRealityProducerService(
                projectRepository, taskRepository, readinessService,
                findingRepository, evidenceNodeRepository, wishlistRepository,
                plannedWorkRecoveryService);

        when(operationalPolicyService.authorize(any(UUID.class), eq(OperationalAction.DISPATCH_QUEUED_TASKS)))
                .thenReturn(new OperationalPolicyService.OperationalDecision(
                        null, OperationalAction.DISPATCH_QUEUED_TASKS,
                        true, "ACTIVE", "authorized", "allowed", List.of(), null));
    }

    @Test
    @DisplayName("Law 5: task status done does NOT imply reachedMain or hasRequiredMergeEvidence without merged PR")
    void doneTaskWithoutMergedReviewsIsNotDelivered() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.initializeStatus(TaskStatus.done);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setStatus("closed_terminal_task");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));
        // No merged review recorded on GitHub
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of());

        // Self-reported done status must be rejected as proof of delivery by the independent witness
        assertFalse(readinessService.reachedMain(task), "done task without merged review must NOT reach main");
        assertFalse(readinessService.hasRequiredMergeEvidence(task), "done task without merged review has no merge evidence");

        // Mandatory reverse case: when a real merged PR with code exists on main, delivery is verified
        PrReviewEntity mergedReview = new PrReviewEntity();
        mergedReview.setMerged(true);
        mergedReview.setBaseRef("main");
        mergedReview.setHasCode(true);
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(mergedReview));

        assertTrue(readinessService.reachedMain(task), "verified merged review to main satisfies reachedMain");
        assertTrue(readinessService.hasRequiredMergeEvidence(task), "verified merged review with code satisfies merge evidence");
    }

    @Test
    @DisplayName("Law 5: client requirements are NOT fulfilled even if all tasks report done, until merged")
    void doneTasksDoNotFulfillPlannedRequirementWithoutMergeEvidence() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        WishlistEntity root = new WishlistEntity();
        root.setId(rootId);
        root.setProjectId(projectId);
        root.setSource(WishlistSource.client);
        root.setStatus(WishlistStatus.converted_to_task);

        FeatureEntity feature = new FeatureEntity();
        UUID featureId = UUID.randomUUID();
        feature.setId(featureId);
        feature.setProjectId(projectId);
        feature.setRootWishlistId(rootId);

        WishlistEntity item = new WishlistEntity();
        UUID itemId = UUID.randomUUID();
        item.setId(itemId);
        item.setProjectId(projectId);
        item.setSource(WishlistSource.client);
        item.setOriginWishlistId(rootId);
        item.setFeatureId(featureId);
        item.setStatus(WishlistStatus.converted_to_task);
        item.setCompiledByRole("BARCAN-TAG-09");
        item.setContent("Slice 1: Implement Domain Service");

        TaskEntity task = new TaskEntity();
        UUID taskId = UUID.randomUUID();
        task.setId(taskId);
        task.setProject(project);
        task.setFeatureId(featureId);
        task.setSourceWishlistId(itemId);
        task.initializeStatus(TaskStatus.done);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);

        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(root, item));
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature));
        when(taskRepository.findBySourceWishlistIdIn(List.of(itemId))).thenReturn(List.of(task));

        // No merged review
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());

        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(projectId);

        // All tasks done, yet value delivered is zero!
        assertEquals(0, readiness.mergedDeliverables(), "done tasks without merged PRs produce 0 merged deliverables");
        assertEquals(0, readiness.completeFeatures(), "feature is not complete without merged deliverables");
        assertEquals(0.0, readiness.ratio(), 0.0001, "readiness ratio must be 0 when no task has landed on main");

        // Mandatory reverse case: provide independent witness testimony (PR merged to main)
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));

        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setBaseRef("main");
        review.setHasCode(true);
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));

        ClientDeliverableReadinessService.Readiness verified = readinessService.computeForProject(projectId);
        assertEquals(1, verified.mergedDeliverables(), "verified merge delivers requirement");
        assertEquals(1, verified.completeFeatures(), "feature completes once all requirements are merged");
        assertEquals(1.0, verified.ratio(), 0.0001, "readiness ratio reaches 1.0 upon independent verification");
    }

    @Test
    @DisplayName("Law 5: PR merged to feature branch does NOT imply reachedMain until feature thread merges to main")
    void prMergedToFeatureBranchDoesNotImplyReachedMainUnlessThreadMerged() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setFeatureId(featureId);
        task.initializeStatus(TaskStatus.done);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);

        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setBaseRef("feature/user-auth"); // merged to intermediate thread branch, NOT main
        review.setHasCode(true);

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));

        FeatureThreadEntity thread = new FeatureThreadEntity();
        thread.setProjectId(projectId);
        thread.setFeatureId(featureId);
        thread.setMergedToMainAt(null); // feature thread has not folded into main yet

        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, featureId))
                .thenReturn(Optional.of(thread));

        assertFalse(readinessService.reachedMain(task),
                "PR merged to intermediate feature branch must NOT count as reachedMain while thread is unmerged");

        // Reverse case: feature thread merges to main
        thread.setMergedToMainAt(Instant.now());
        assertTrue(readinessService.reachedMain(task),
                "once feature thread merges to main, task work is verified in main");
    }

    @Test
    @DisplayName("Law 5: code-bearing task merged without code diff is NOT counted as delivered value")
    void codeOwedTaskMergedWithoutCodeDoesNotImplyDelivery() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.initializeStatus(TaskStatus.done);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02"); // Implementation owes code
        task.setRole(role);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);

        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setBaseRef("main");
        review.setHasCode(false); // e.g. only blocker recorded, no code diff!

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));

        assertTrue(readinessService.reachedMain(task), "PR physically landed in main");
        assertFalse(readinessService.hasRequiredMergeEvidence(task),
                "diff lacking code for code-bearing role cannot count as delivery of value");
    }

    @Test
    @DisplayName("Law 5: DeliveryRealityProducerService flags done task without merge evidence as defect, never as value")
    void deliveryRealityFlagsDoneTaskWithoutMergeEvidenceAsDefect() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);
        project.setName("test-project");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setTitle("Core Service Slice");
        task.initializeStatus(TaskStatus.done);
        task.setFeatureId(UUID.randomUUID());

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());

        ClientDeliverableReadinessService mockReadiness = mock(ClientDeliverableReadinessService.class);
        DeliveryRealityProducerService testDeliveryService = new DeliveryRealityProducerService(
                projectRepository, taskRepository, mockReadiness,
                findingRepository, evidenceNodeRepository, wishlistRepository,
                plannedWorkRecoveryService);

        when(mockReadiness.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(mockReadiness.isAuxiliaryTask(task)).thenReturn(false);
        when(mockReadiness.listEpicDiagnostics(project.getId())).thenReturn(List.of(
                new ClientDeliverableReadinessService.EpicDiagnostic(task.getFeatureId(), "Slice", null, Instant.now(), true, false, 0, 0)));

        when(findingRepository.findByTaskId(task.getId())).thenReturn(List.of());
        when(findingRepository.save(any(OperationalRealityFindingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        testDeliveryService.produce();

        // DeliveryRealityProducerService creates an operational finding for the unmerged done task
        verify(findingRepository, times(1)).save(argThat(finding ->
                finding.getTaskId().equals(task.getId())
                        && "no merge evidence: never reached main".equals(finding.getActualGithubState())
                        && "done".equals(finding.getExpectedStatus())));

        // Mandatory reverse case: when merge evidence exists, no defect is filed
        reset(findingRepository);
        when(mockReadiness.hasRequiredMergeEvidence(task)).thenReturn(true);
        testDeliveryService.produce();
        verify(findingRepository, never()).save(any(OperationalRealityFindingEntity.class));
    }

    @Test
    @DisplayName("Structural Law 5: ClientDeliverableReadinessService never equates TaskStatus.done to delivery")
    void structuralAuditNoStatusDoneCheckInReadinessPredicates() throws IOException {
        Path readinessFile = Path.of("src/main/java/com/eneik/production/services/ClientDeliverableReadinessService.java");
        if (!Files.exists(readinessFile)) {
            readinessFile = Path.of("C:/Projects/Eneik/docker-build/EneikProductionSys/src/main/java/com/eneik/production/services/ClientDeliverableReadinessService.java");
        }
        assertTrue(Files.exists(readinessFile),
                "Law 5 structural guard cannot run: source file not found at " + readinessFile.toAbsolutePath());

        String content = Files.readString(readinessFile);

        // Verification: reachedMain method must never inspect task.getStatus()
        int reachedMainIdx = content.indexOf("public boolean reachedMain(TaskEntity task)");
        assertTrue(reachedMainIdx > 0, "reachedMain method must exist in ClientDeliverableReadinessService");

        int methodEndIdx = content.indexOf("}", reachedMainIdx);
        String reachedMainBody = content.substring(reachedMainIdx, methodEndIdx);

        assertFalse(reachedMainBody.contains("getStatus()"),
                "Law 5 violation: reachedMain must never consult task.getStatus() - only independent merge witness");
        assertFalse(reachedMainBody.contains("TaskStatus.done"),
                "Law 5 violation: reachedMain must never rely on TaskStatus.done");
    }
}
