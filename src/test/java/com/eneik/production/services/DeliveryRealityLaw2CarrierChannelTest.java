package com.eneik.production.services;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Proof obligations for the joint reading of Laws 2, 7 and 8 on a carrier that closed with nothing on main.
 *
 * <p>The three laws are unsatisfiable together on T_carrier as long as Law 3's arc must end in a wishlist:
 * Law 3 wants the finding filed as scope, Law 7 wants that scope to inherit epic(τ), and Law 2 says
 * epic(τ) = ∅ for a carrier - while founding one is what Law 7's second clause forbids and attaching the
 * carrier's repair to a product epic is what Law 2 forbids. The resolution weakens none of them: the domain
 * of repair is the product half of the partition, dom(repair) = T_product, and on the carrier half the
 * finding is still NAMED - in the carrier's own channel, because Law 8 forbids stopping silently.
 *
 * <p>Each test below is one half of that statement. The reverse cases matter as much as the positive ones:
 * without them "record the carrier" degenerates into "every carrier is a defect", which is the 2026-07-25
 * false positive in a new place.
 */
class DeliveryRealityLaw2CarrierChannelTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final OperationalRealityFindingRepository findingRepository = mock(OperationalRealityFindingRepository.class);
    private final EvidenceNodeRepository evidenceNodeRepository = mock(EvidenceNodeRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final PlannedWorkRecoveryService plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);
    private final DefectJournalRepository defectJournalRepository = mock(DefectJournalRepository.class);
    private final SystemSettingsService systemSettingsService = mock(SystemSettingsService.class);

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
        project.setName("test-product");
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(readinessService.listEpicDiagnostics(projectId)).thenReturn(List.of(
                new ClientDeliverableReadinessService.EpicDiagnostic(
                        productEpicId, "Core Feature", null, Instant.now(), true, false, 0, 0)
        ));
        when(systemSettingsService.effectiveInt(eq(DeliveryRealityProducerService.MAX_REPAIR_DEPTH_KEY), anyInt()))
                .thenReturn(2);
    }

    private TaskEntity carrier(String carrierType, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(status);
        task.setTitle("Housekeeping Carrier");
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put(TaskEntity.CARRIER_PAYLOAD_KEY, carrierType);
        task.setPayload(payload);
        return task;
    }

    @Test
    @DisplayName("Law 2+8: a housekeeping carrier that closed with nothing on main is named in the carrier channel")
    void housekeepingCarrierWithoutMergeEvidenceIsRecordedInCarrierChannel() {
        TaskEntity task = carrier("housekeeping_audit", TaskStatus.done);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));
        when(plannedWorkRecoveryService.mayStillBeResumed(task)).thenReturn(false);

        service.produce();

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        DefectJournalEntity defect = captor.getValue();

        assertEquals(projectId, defect.getProjectId());
        assertEquals(DeliveryRealityProducerService.CARRIER_DELIVERY_MISSING, defect.getDefectType());
        // The record is kept in its own category, so T = T_product ⊔ T_carrier survives in the journal too.
        assertEquals(DeliveryRealityProducerService.CARRIER_CHANNEL_CATEGORY, defect.getCategory());
        assertNotEquals(DeliveryRealityProducerService.DELIVERY_EXHAUSTED_CATEGORY, defect.getCategory());
        // carrier(τ) → epic(τ) = ∅, asserted in the record itself and not only in the code that wrote it.
        assertNull(defect.getFeatureId());
        assertTrue(defect.getDescription().contains(task.getId().toString()));

        // Law 2 and Law 7: no product finding, no evidence node, no scope, no epic.
        verify(findingRepository, never()).save(any());
        verify(evidenceNodeRepository, never()).save(any());
        verify(wishlistRepository, never()).save(any());
        assertNull(service.epicOfRequirement(task));
    }

    @Test
    @DisplayName("Reverse case: a carrier whose work did reach main is recorded nowhere")
    void carrierWithMergeEvidenceIsNotRecorded() {
        TaskEntity task = carrier("housekeeping_audit", TaskStatus.done);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(true);

        service.recordCarrierNonDelivery(project, task);

        verify(defectJournalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reverse case: an auxiliary carrier that never owed main is recorded nowhere")
    void auxiliaryCarrierIsNotRecorded() {
        TaskEntity task = carrier("housekeeping_audit", TaskStatus.done);
        when(readinessService.hasRequiredMergeEvidence(task)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(task)).thenReturn(true);

        service.recordCarrierNonDelivery(project, task);

        verify(defectJournalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Law 2: the compiler carrier is not measured against main at all - its product is a decomposition")
    void wishlistCompilerCarrierIsNotMeasuredAgainstMain() {
        TaskEntity compiler = carrier(TaskEntity.WISHLIST_COMPILER_TASK_TYPE, TaskStatus.done);
        when(readinessService.hasRequiredMergeEvidence(compiler)).thenReturn(false);
        when(readinessService.isAuxiliaryTask(compiler)).thenReturn(false);

        assertTrue(compiler.isWishlistCompiler());

        service.recordCarrierNonDelivery(project, compiler);

        // Asking whether the compiler's code landed is the category mistake in the other direction: its
        // spend is accounted by the compile budget (Law 9) and lastCompileReachedAt (Law 10), not here.
        verify(defectJournalRepository, never()).save(any());
        verify(readinessService, never()).hasRequiredMergeEvidence(compiler);
    }

    @Test
    @DisplayName("Law 2: a product task never lands in the carrier channel")
    void productTaskIsNotRecordedInCarrierChannel() {
        TaskEntity productTask = new TaskEntity();
        productTask.setId(UUID.randomUUID());
        productTask.setProject(project);
        productTask.setStatus(TaskStatus.done);
        productTask.setTitle("Customer Upload API");
        productTask.setFeatureId(productEpicId);

        assertFalse(productTask.isCarrier());

        service.recordCarrierNonDelivery(project, productTask);

        verify(defectJournalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Law 2: the product path is untouched - a product task still files scope on the product epic")
    void productTaskStillFilesScopeUnderTheSameSweep() {
        TaskEntity productTask = new TaskEntity();
        productTask.setId(UUID.randomUUID());
        productTask.setProject(project);
        productTask.setStatus(TaskStatus.done);
        productTask.setTitle("Customer Upload API");
        productTask.setFeatureId(productEpicId);

        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(
                projectId, WishlistSource.delivery_never_reached_main, productTask.getId()))
                .thenReturn(false);

        service.fileTheMissingWorkAsScope(project, productTask);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        assertEquals(productEpicId, captor.getValue().getFeatureId());
        verify(defectJournalRepository, never()).save(any());
    }
}
