package com.eneik.production.services.coherence;

import com.eneik.production.kaizen.repository.KaizenProposalRepository;
import com.eneik.production.models.persistence.CoherenceRunEntity;
import com.eneik.production.models.persistence.CoherenceRunNodeResultEntity;
import com.eneik.production.models.persistence.EvidenceNodeEntity;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.CoherenceRunNodeResultRepository;
import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.WishlistContentSimilarityMatcher;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.operational.OperationalTruthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test shield for Prescription 48 (D011 TELEOSEMANTIC_FEEDBACK, Millikan 1984):
 * "A signal without a reader is not an observation; a signal must alter action."
 *
 * Falsification criteria:
 * - A negative or sub-threshold coherence score MUST alter the readiness gate decision,
 *   blocking isProjectDeliverable and shifting OperationalTruthService away from "delivered".
 * - A feature with an accepted negative defect finding and no positive confirmation
 *   MUST block isFeatureReadyForCloseout, preventing premature PR merge to main.
 */
class TeleosemanticFeedbackCoherenceTest {

    private EvidenceNodeRepository evidenceNodeRepository;
    private CoherenceRunRepository coherenceRunRepository;
    private CoherenceRunNodeResultRepository coherenceRunNodeResultRepository;
    private WishlistContentSimilarityMatcher similarityMatcher;
    private ProjectRepository projectRepository;
    private KaizenProposalRepository kaizenProposalRepository;

    private WishlistRepository wishlistRepository;
    private FeatureRepository featureRepository;
    private TaskRepository taskRepository;
    private JulesSessionRepository julesSessionRepository;
    private PrReviewRepository prReviewRepository;
    private FeatureThreadRepository featureThreadRepository;
    private OperationalPolicyService operationalPolicyService;

    private EvidenceCoherenceService coherenceService;
    private ClientDeliverableReadinessService readinessService;

    @BeforeEach
    void setUp() {
        evidenceNodeRepository = mock(EvidenceNodeRepository.class);
        coherenceRunRepository = mock(CoherenceRunRepository.class);
        coherenceRunNodeResultRepository = mock(CoherenceRunNodeResultRepository.class);
        similarityMatcher = mock(WishlistContentSimilarityMatcher.class);
        projectRepository = mock(ProjectRepository.class);
        kaizenProposalRepository = mock(KaizenProposalRepository.class);

        coherenceService = new EvidenceCoherenceService(
                evidenceNodeRepository, coherenceRunRepository, coherenceRunNodeResultRepository,
                similarityMatcher, projectRepository, kaizenProposalRepository
        );
        ReflectionTestUtils.setField(coherenceService, "minCoherenceScore", 0.0);

        wishlistRepository = mock(WishlistRepository.class);
        featureRepository = mock(FeatureRepository.class);
        taskRepository = mock(TaskRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        prReviewRepository = mock(PrReviewRepository.class);
        featureThreadRepository = mock(FeatureThreadRepository.class);
        operationalPolicyService = mock(OperationalPolicyService.class);

        when(operationalPolicyService.authorize(any(UUID.class), eq(OperationalAction.DISPATCH_QUEUED_TASKS)))
                .thenReturn(new OperationalPolicyService.OperationalDecision(
                        null, OperationalAction.DISPATCH_QUEUED_TASKS,
                        true, "ACTIVE", "authorized", "allowed", List.of(), null));

        readinessService = new ClientDeliverableReadinessService(
                wishlistRepository, featureRepository, taskRepository, julesSessionRepository,
                prReviewRepository, featureThreadRepository, projectRepository, operationalPolicyService,
                coherenceService
        );
    }

    private void stubPlan(UUID projectId, WishlistEntity root, FeatureEntity feature,
                          List<WishlistEntity> items, List<TaskEntity> tasks) {
        List<WishlistEntity> all = new ArrayList<>();
        all.add(root);
        all.addAll(items);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(all);
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature));
        when(taskRepository.findBySourceWishlistIdIn(items.stream().map(WishlistEntity::getId).toList()))
                .thenReturn(tasks);
    }

    private TaskEntity task(UUID id, UUID projectId, UUID featureId, UUID wishlistId, String roleTag) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        task.setProject(project);
        task.setFeatureId(featureId);
        task.setSourceWishlistId(wishlistId);
        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        task.setRole(role);
        task.initializeStatus(TaskStatus.done);
        return task;
    }

    private void stubMerged(TaskEntity task, boolean hasCode) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setHasCode(hasCode);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));
    }

    @Test
    @DisplayName("Negative coherence score blocks project delivery even if all tasks are complete")
    void negativeCoherenceScoreBlocksProjectDelivery() {
        UUID projectId = UUID.randomUUID();
        UUID rootWishlistId = UUID.randomUUID();

        FeatureEntity feature = new FeatureEntity();
        feature.setId(UUID.randomUUID());
        feature.setProjectId(projectId);
        feature.setRootWishlistId(rootWishlistId);
        feature.setTitle("Core Feature");

        WishlistEntity root = new WishlistEntity();
        root.setId(rootWishlistId);
        root.setProjectId(projectId);
        root.setStatus(WishlistStatus.converted_to_task);
        root.setSource(WishlistSource.client);

        WishlistEntity item = new WishlistEntity();
        item.setId(UUID.randomUUID());
        item.setProjectId(projectId);
        item.setFeatureId(feature.getId());
        item.setCompiledByRole("BARCAN-TAG-02");
        item.setStatus(WishlistStatus.converted_to_task);
        item.setSource(WishlistSource.client);

        TaskEntity task = task(UUID.randomUUID(), projectId, feature.getId(), item.getId(), "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, List.of(item), List.of(task));
        stubMerged(task, true);

        // Setup INCOHERENT run (contradictory findings produce negative score)
        CoherenceRunEntity run = new CoherenceRunEntity();
        run.setId(UUID.randomUUID());
        run.setProjectId(projectId);
        run.setTotalNodes(4);
        run.setAcceptedNodes(2);
        run.setCoherenceScore(-0.18);
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId)).thenReturn(List.of(run));

        // Verify coherence service
        assertFalse(coherenceService.isCoherent(projectId), "Project with negative score must not be coherent");
        assertEquals(-0.18, coherenceService.getLatestCoherenceScore(projectId), 1e-6);

        // Verify readiness computation
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(projectId);
        assertEquals(1, readiness.totalFeatures());
        assertEquals(1, readiness.completeFeatures());
        assertFalse(readiness.coherent(), "Readiness must reflect incoherent evidence graph");
        assertEquals(-0.18, readiness.coherenceScore(), 1e-6);

        // Falsification check: isProjectDeliverable MUST be blocked by incoherent score
        assertFalse(readinessService.isProjectDeliverable(projectId),
                "Teleosemantic gate: delivery must be withheld when evidence graph is incoherent");

        // OperationalTruthService must report "incoherent" instead of "delivered"
        String deliveryStatus = OperationalTruthService.deliveryStatus(readiness);
        assertEquals("incoherent", deliveryStatus,
                "Operational truth must report 'incoherent' when features are complete but coherence is negative");
    }

    @Test
    @DisplayName("Positive coherence score permits project delivery when features are complete")
    void positiveCoherenceScorePermitsProjectDelivery() {
        UUID projectId = UUID.randomUUID();
        UUID rootWishlistId = UUID.randomUUID();

        FeatureEntity feature = new FeatureEntity();
        feature.setId(UUID.randomUUID());
        feature.setProjectId(projectId);
        feature.setRootWishlistId(rootWishlistId);
        feature.setTitle("Core Feature");

        WishlistEntity root = new WishlistEntity();
        root.setId(rootWishlistId);
        root.setProjectId(projectId);
        root.setStatus(WishlistStatus.converted_to_task);
        root.setSource(WishlistSource.client);

        WishlistEntity item = new WishlistEntity();
        item.setId(UUID.randomUUID());
        item.setProjectId(projectId);
        item.setFeatureId(feature.getId());
        item.setCompiledByRole("BARCAN-TAG-02");
        item.setStatus(WishlistStatus.converted_to_task);
        item.setSource(WishlistSource.client);

        TaskEntity task = task(UUID.randomUUID(), projectId, feature.getId(), item.getId(), "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, List.of(item), List.of(task));
        stubMerged(task, true);

        // Setup COHERENT run (harmonic consensus)
        CoherenceRunEntity run = new CoherenceRunEntity();
        run.setId(UUID.randomUUID());
        run.setProjectId(projectId);
        run.setTotalNodes(4);
        run.setAcceptedNodes(4);
        run.setCoherenceScore(0.35);
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId)).thenReturn(List.of(run));

        assertTrue(coherenceService.isCoherent(projectId));
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(projectId);
        assertTrue(readiness.coherent());
        assertTrue(readinessService.isProjectDeliverable(projectId));
        assertEquals("delivered", OperationalTruthService.deliveryStatus(readiness));
    }

    @Test
    @DisplayName("Accepted negative defect finding on a feature blocks closeout PR to main")
    void acceptedNegativeDefectBlocksFeatureCloseout() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        // Feature has merged work on its branch
        TaskEntity task = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(task));
        stubMerged(task, true);
        when(wishlistRepository.findByFeatureId(featureId)).thenReturn(List.of());

        // Overall project run is positive...
        CoherenceRunEntity run = new CoherenceRunEntity();
        run.setId(UUID.randomUUID());
        run.setProjectId(projectId);
        run.setTotalNodes(2);
        run.setAcceptedNodes(2);
        run.setCoherenceScore(0.10);
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId)).thenReturn(List.of(run));

        // ...but this specific feature has an accepted NEGATIVE_FINDING with no positive confirmation
        EvidenceNodeEntity defectNode = new EvidenceNodeEntity();
        defectNode.setId(UUID.randomUUID());
        defectNode.setProjectId(projectId);
        defectNode.setFeatureId(featureId);
        defectNode.setPolarity(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);

        CoherenceRunNodeResultEntity nodeResult = new CoherenceRunNodeResultEntity();
        nodeResult.setCoherenceRunId(run.getId());
        nodeResult.setEvidenceNodeId(defectNode.getId());
        nodeResult.setAccepted(true);

        when(evidenceNodeRepository.findByProjectIdAndFeatureId(projectId, featureId)).thenReturn(List.of(defectNode));
        when(coherenceRunNodeResultRepository.findByCoherenceRunId(run.getId())).thenReturn(List.of(nodeResult));

        // Teleosemantic gate check:
        assertFalse(coherenceService.isFeatureCoherent(projectId, featureId),
                "Feature with accepted negative defect must not be feature-coherent");
        assertFalse(readinessService.isFeatureReadyForCloseout(projectId, featureId),
                "AutoMerge closeout PR must be BLOCKED when feature has uncorroborated negative defect finding");
    }

    @Test
    @DisplayName("Feature closeout is permitted when negative defect is counterbalanced by positive confirmation")
    void featureCloseoutPermittedWhenDefectResolvedOrCorroborated() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        TaskEntity task = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(task));
        stubMerged(task, true);
        when(wishlistRepository.findByFeatureId(featureId)).thenReturn(List.of());

        CoherenceRunEntity run = new CoherenceRunEntity();
        run.setId(UUID.randomUUID());
        run.setProjectId(projectId);
        run.setTotalNodes(2);
        run.setAcceptedNodes(2);
        run.setCoherenceScore(0.20);
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId)).thenReturn(List.of(run));

        EvidenceNodeEntity posNode = new EvidenceNodeEntity();
        posNode.setId(UUID.randomUUID());
        posNode.setProjectId(projectId);
        posNode.setFeatureId(featureId);
        posNode.setPolarity(EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION);

        CoherenceRunNodeResultEntity posResult = new CoherenceRunNodeResultEntity();
        posResult.setCoherenceRunId(run.getId());
        posResult.setEvidenceNodeId(posNode.getId());
        posResult.setAccepted(true);

        when(evidenceNodeRepository.findByProjectIdAndFeatureId(projectId, featureId)).thenReturn(List.of(posNode));
        when(coherenceRunNodeResultRepository.findByCoherenceRunId(run.getId())).thenReturn(List.of(posResult));

        assertTrue(coherenceService.isFeatureCoherent(projectId, featureId));
        assertTrue(readinessService.isFeatureReadyForCloseout(projectId, featureId));
    }
}
