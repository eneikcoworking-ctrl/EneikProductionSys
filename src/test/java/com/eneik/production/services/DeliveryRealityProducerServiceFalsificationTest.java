package com.eneik.production.services;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.runtime.ClientRuntimeObservabilityService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for DeliveryRealityProducerService.
 * Validates epistemic and architectural delivery reality invariants:
 * - D002 Austin Category Error Scan: Task done without merge evidence creates delivery defect fact, NOT unbound customer requirement; carrier tasks never order product scope.
 * - D013 Wittgenstein Anti-Mirror Telemetry: Physical runtime launch observation failures are projected into durable EvidenceNodeEntity in the reasoning graph.
 * - D006 Raz Prohibition As Code: Idempotent evidence generation (a standing defect refreshes existing evidence timestamp rather than duplicating finding rows).
 */
class DeliveryRealityProducerServiceFalsificationTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final OperationalRealityFindingRepository findingRepository = mock(OperationalRealityFindingRepository.class);
    private final EvidenceNodeRepository evidenceNodeRepository = mock(EvidenceNodeRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final PlannedWorkRecoveryService plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);
    private final DefectJournalRepository defectJournalRepository = mock(DefectJournalRepository.class);
    private final SystemSettingsService systemSettingsService = mock(SystemSettingsService.class);
    private final ClientRuntimeObservabilityService runtimeObservabilityService = mock(ClientRuntimeObservabilityService.class);

    private DeliveryRealityProducerService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID productEpicId = UUID.randomUUID();
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        service = new DeliveryRealityProducerService(
                projectRepository,
                taskRepository,
                readinessService,
                findingRepository,
                evidenceNodeRepository,
                wishlistRepository,
                plannedWorkRecoveryService
        );
        service.setDefectJournalRepository(defectJournalRepository);
        service.setSystemSettingsService(systemSettingsService);

        project = new ProjectEntity();
        project.setId(projectId);
        project.setName("customer-product");
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(readinessService.listEpicDiagnostics(projectId)).thenReturn(List.of(
                new ClientDeliverableReadinessService.EpicDiagnostic(
                        productEpicId, "Customer Core API", null, Instant.now(), true, false, 0, 0)
        ));
        when(systemSettingsService.effectiveInt(eq(DeliveryRealityProducerService.MAX_REPAIR_DEPTH_KEY), anyInt()))
                .thenReturn(2);
    }

    @Test
    @DisplayName("D002 Austin Category Error: Carrier task done without merge evidence orders NO product wishlist scope")
    void falsifyAustinCategoryError_carrierTaskWithoutMergeNeverOrdersProductScope() {
        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setId(UUID.randomUUID());
        carrierTask.setProject(project);
        carrierTask.setStatus(TaskStatus.done);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-00");
        carrierTask.setRole(role);

        // Carrier payload: taskType indicates internal factory operation
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("taskType", "TechnicalLeadCompiler");
        carrierTask.setPayload(payload);

        assertThat(carrierTask.isCarrier()).isTrue();

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(carrierTask));
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(readinessService.hasRequiredMergeEvidence(carrierTask)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(carrierTask)).thenReturn(false);

        service.produce();

        // Verification: Carrier task does NOT produce OperationalRealityFindingEntity nor WishlistEntity
        verify(findingRepository, never()).save(any());
        verify(wishlistRepository, never()).save(any());
        verify(evidenceNodeRepository, never()).save(any());

        // Carrier non-delivery is recorded in DefectJournal carrier channel instead
        ArgumentCaptor<DefectJournalEntity> journalCaptor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(journalCaptor.capture());
        DefectJournalEntity defect = journalCaptor.getValue();
        assertThat(defect.getCategory()).isEqualTo(DeliveryRealityProducerService.CARRIER_CHANNEL_CATEGORY);
        assertThat(defect.getDefectType()).isEqualTo(DeliveryRealityProducerService.CARRIER_DELIVERY_MISSING);
    }

    @Test
    @DisplayName("D002 Austin Category Error: Repeated failure of ordered requirement produces DefectJournal record, NOT infinite wishlists")
    void falsifyAustinCategoryError_repeatedDeliveryFailureCreatesDefectJournalRecordNotWishlist() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFeatureId(productEpicId);
        task.setStatus(TaskStatus.done);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);

        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndStatusNot(
                projectId, productEpicId, WishlistStatus.dismissed)).thenReturn(true);

        service.fileTheMissingWorkAsScope(project, task);

        // Verification: No new wishlist is created
        verify(wishlistRepository, never()).save(any());

        // DefectJournal records the terminal absorbing condition under Law 3 / D002
        ArgumentCaptor<DefectJournalEntity> journalCaptor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(journalCaptor.capture());
        DefectJournalEntity defect = journalCaptor.getValue();
        assertThat(defect.getCategory()).isEqualTo(DeliveryRealityProducerService.DELIVERY_EXHAUSTED_CATEGORY);
        assertThat(defect.getDefectType()).isEqualTo(DeliveryRealityProducerService.REPEATED_DELIVERY_FAILURE);
        assertThat(defect.getProjectId()).isEqualTo(projectId);
        assertThat(defect.getFeatureId()).isEqualTo(productEpicId);
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Unhealthy runtime observation is projected to EvidenceNodeEntity in reasoning graph")
    void falsifyWittgensteinAntiMirror_unhealthyRuntimeObservationProjectsToEvidenceNode() {
        // Field-inject runtime observability service
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "runtimeObservabilityService", runtimeObservabilityService);

        ClientRuntimeObservabilityService.RuntimeHealthSummary summary = mock(ClientRuntimeObservabilityService.RuntimeHealthSummary.class);
        when(summary.lastObservationHealthy()).thenReturn(false);

        com.eneik.production.models.persistence.ClientRuntimeObservationEntity obs =
                new com.eneik.production.models.persistence.ClientRuntimeObservationEntity();
        obs.setErrorText("Container test-fiftieth_backend failed health check on :18080");
        when(summary.lastProductObservation()).thenReturn(Optional.of(obs));

        when(runtimeObservabilityService.summarize(projectId)).thenReturn(summary);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());

        UUID pseudoTaskId = UUID.fromString("00000000-0000-0000-0000-00000000fa11");
        when(findingRepository.findByTaskId(pseudoTaskId)).thenReturn(List.of());

        OperationalRealityFindingEntity savedFinding = new OperationalRealityFindingEntity();
        savedFinding.setId(UUID.randomUUID());
        savedFinding.setTaskId(pseudoTaskId);
        when(findingRepository.save(any(OperationalRealityFindingEntity.class))).thenReturn(savedFinding);

        service.produce();

        // Verification: Finding and EvidenceNodeEntity were created with negative polarity
        ArgumentCaptor<EvidenceNodeEntity> nodeCaptor = ArgumentCaptor.forClass(EvidenceNodeEntity.class);
        verify(evidenceNodeRepository).save(nodeCaptor.capture());
        EvidenceNodeEntity node = nodeCaptor.getValue();
        assertThat(node.getProjectId()).isEqualTo(projectId);
        assertThat(node.getPolarity()).isEqualTo(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        assertThat(node.getSummaryText()).contains("The delivered product's most recent runtime observation was not healthy");
        assertThat(node.getSummaryText()).contains("Container test-fiftieth_backend failed health check on :18080");
    }

    @Test
    @DisplayName("D006 Raz Prohibition As Code: Standing finding refreshes existing EvidenceNode timestamp without duplicate rows")
    void falsifyRazProhibitionAsCode_standingFindingRefreshesTimestampWithoutDuplicateRows() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFeatureId(productEpicId);
        task.setStatus(TaskStatus.done);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);

        // Pre-existing finding for this task
        OperationalRealityFindingEntity existingFinding = new OperationalRealityFindingEntity();
        UUID findingId = UUID.randomUUID();
        existingFinding.setId(findingId);
        existingFinding.setTaskId(task.getId());
        when(findingRepository.findByTaskId(task.getId())).thenReturn(List.of(existingFinding));

        EvidenceNodeEntity existingNode = new EvidenceNodeEntity();
        existingNode.setId(UUID.randomUUID());
        existingNode.setProjectId(projectId);
        Instant originalTimestamp = Instant.now().minusSeconds(3600);
        existingNode.setCreatedAt(originalTimestamp);
        when(evidenceNodeRepository.findByOperationalRealityFindingId(findingId)).thenReturn(List.of(existingNode));

        // Wishlist for repair already exists
        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(
                eq(projectId), eq(WishlistSource.delivery_never_reached_main), eq(task.getId()))).thenReturn(true);

        service.produce();

        // Invariant: No NEW OperationalRealityFindingEntity created
        verify(findingRepository, never()).save(any());

        // Invariant: Existing evidence node timestamp is updated/refreshed
        ArgumentCaptor<EvidenceNodeEntity> nodeCaptor = ArgumentCaptor.forClass(EvidenceNodeEntity.class);
        verify(evidenceNodeRepository).save(nodeCaptor.capture());
        EvidenceNodeEntity refreshedNode = nodeCaptor.getValue();
        assertThat(refreshedNode.getId()).isEqualTo(existingNode.getId());
        assertThat(refreshedNode.getCreatedAt()).isAfter(originalTimestamp);
    }

    @Test
    @DisplayName("D006 Raz Prohibition As Code: Missing delivery creates exactly one new repair wishlist with inherited epic")
    void falsifyRazProhibitionAsCode_missingDeliveryCreatesSingleRepairWishlistInheritingProductEpic() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFeatureId(productEpicId);
        task.setStatus(TaskStatus.done);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        task.setRole(role);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);

        when(findingRepository.findByTaskId(task.getId())).thenReturn(List.of());
        OperationalRealityFindingEntity savedFinding = new OperationalRealityFindingEntity();
        savedFinding.setId(UUID.randomUUID());
        savedFinding.setTaskId(task.getId());
        when(findingRepository.save(any(OperationalRealityFindingEntity.class))).thenReturn(savedFinding);

        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(
                eq(projectId), eq(WishlistSource.delivery_never_reached_main), eq(task.getId()))).thenReturn(false);
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndStatusNot(
                projectId, productEpicId, WishlistStatus.dismissed)).thenReturn(false);

        service.produce();

        // Invariant: Creates exactly one repair wishlist with source delivery_never_reached_main inheriting product epic
        ArgumentCaptor<WishlistEntity> wishlistCaptor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository, times(1)).save(wishlistCaptor.capture());
        WishlistEntity repairWishlist = wishlistCaptor.getValue();

        assertThat(repairWishlist.getProjectId()).isEqualTo(projectId);
        assertThat(repairWishlist.getSource()).isEqualTo(WishlistSource.delivery_never_reached_main);
        assertThat(repairWishlist.getSourceTaskId()).isEqualTo(task.getId());
        assertThat(repairWishlist.getFeatureId()).isEqualTo(productEpicId);
        assertThat(repairWishlist.getStatus()).isEqualTo(WishlistStatus.pending);
    }
}
