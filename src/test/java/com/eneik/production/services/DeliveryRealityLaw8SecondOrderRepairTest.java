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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Proof obligations for Laws 2, 7, and 8:
 * - Law 2 (Carrier Category Isolation): carrier(τ) → epic(τ) = ∅. A carrier task is internal factory machinery
 *   and must never be treated as a missing product deliverable or order product scope.
 * - Law 7 (Belonging of Repair): epic(w_repair) = epic(requirement). A repair inherits the product epic of
 *   the requirement it attempts to fulfill and never founds one of its own.
 * - Law 8 (Variant Function & Absorbing Condition): v = maxRepairDepth - repairDepth strictly decreases per turn.
 *   Second-order repairs successfully inherit the product epic. When the repair budget is exhausted,
 *   the absorbing condition is explicitly recorded in DefectJournalEntity rather than silently dropped.
 */
class DeliveryRealityLaw8SecondOrderRepairTest {

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
        when(systemSettingsService.effectiveInt(eq("max_repair_depth"), anyInt())).thenReturn(2);
    }

    @Test
    @DisplayName("Law 2: Carrier tasks are ignored by produceForProject and never file product scope")
    void carrierTasksNeverGenerateDeliveryFindingsOrOrderProductScope() {
        TaskEntity carrier = new TaskEntity();
        carrier.setId(UUID.randomUUID());
        carrier.setProject(project);
        carrier.setStatus(TaskStatus.done);
        carrier.setTitle("TechnicalLeadCompiler carrier");
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put(TaskEntity.CARRIER_PAYLOAD_KEY, "TechnicalLeadCompiler");
        carrier.setPayload(payload);

        assertTrue(carrier.isCarrier(), "Task must be recognized as carrier");

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(carrier));
        when(plannedWorkRecoveryService.mayStillBeResumed(carrier)).thenReturn(false);

        // Run sweep
        service.produce();

        // Must never save an OperationalRealityFindingEntity for carrier task
        verify(findingRepository, never()).save(any());
        // Must never save a WishlistEntity scope for carrier task
        verify(wishlistRepository, never()).save(any());

        // Calling fileTheMissingWorkAsScope directly on carrier also does nothing
        service.fileTheMissingWorkAsScope(project, carrier);
        verify(wishlistRepository, never()).save(any());

        // epicOfRequirement on carrier task is always null
        assertNull(service.epicOfRequirement(carrier));
    }

    @Test
    @DisplayName("Law 7: First-order repair files a scope inheriting the product epic and sets depth to 1")
    void firstOrderRepairInheritsProductEpicAndComputesDepthOne() {
        TaskEntity productTask = new TaskEntity();
        productTask.setId(UUID.randomUUID());
        productTask.setProject(project);
        productTask.setStatus(TaskStatus.done);
        productTask.setTitle("Customer Upload API");
        productTask.setFeatureId(productEpicId);

        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(
                projectId, WishlistSource.delivery_never_reached_main, productTask.getId()))
                .thenReturn(false);

        assertEquals(1, service.repairDepthForTask(productTask), "Initial task repair depth must be 1");

        service.fileTheMissingWorkAsScope(project, productTask);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());

        WishlistEntity saved = captor.getValue();
        assertEquals(projectId, saved.getProjectId());
        assertEquals(WishlistSource.delivery_never_reached_main, saved.getSource());
        assertEquals(productTask.getId(), saved.getSourceTaskId());
        assertEquals(productEpicId, saved.getFeatureId(), "Repair must inherit product epic");
    }

    @Test
    @DisplayName("Law 8: Second-order repair successfully inherits product epic and computes depth 2")
    void secondOrderRepairInheritsProductEpicAndComputesDepthTwo() {
        // Initial product task
        UUID initialTaskId = UUID.randomUUID();
        TaskEntity initialTask = new TaskEntity();
        initialTask.setId(initialTaskId);
        initialTask.setProject(project);
        initialTask.setFeatureId(productEpicId);
        when(taskRepository.findById(initialTaskId)).thenReturn(Optional.of(initialTask));

        // First-order repair wishlist
        UUID firstOrderWishlistId = UUID.randomUUID();
        WishlistEntity firstOrderWishlist = new WishlistEntity();
        firstOrderWishlist.setId(firstOrderWishlistId);
        firstOrderWishlist.setProjectId(projectId);
        firstOrderWishlist.setSource(WishlistSource.delivery_never_reached_main);
        firstOrderWishlist.setSourceTaskId(initialTaskId);
        firstOrderWishlist.setFeatureId(productEpicId);
        when(wishlistRepository.findById(firstOrderWishlistId)).thenReturn(Optional.of(firstOrderWishlist));

        // First-order repair task (created from first-order repair wishlist)
        UUID firstOrderRepairTaskId = UUID.randomUUID();
        TaskEntity firstOrderRepairTask = new TaskEntity();
        firstOrderRepairTask.setId(firstOrderRepairTaskId);
        firstOrderRepairTask.setProject(project);
        firstOrderRepairTask.setStatus(TaskStatus.done);
        firstOrderRepairTask.setTitle("Repair of Customer Upload API (Order 1)");
        firstOrderRepairTask.setSourceWishlistId(firstOrderWishlistId);
        firstOrderRepairTask.setFeatureId(productEpicId);
        when(taskRepository.findById(firstOrderRepairTaskId)).thenReturn(Optional.of(firstOrderRepairTask));

        when(wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(
                projectId, WishlistSource.delivery_never_reached_main, firstOrderRepairTaskId))
                .thenReturn(false);

        // Verify calculated depth of the next repair
        int depth = service.repairDepthForTask(firstOrderRepairTask);
        assertEquals(2, depth, "Second-order repair depth must be 2");

        // File scope for failed first-order repair task
        service.fileTheMissingWorkAsScope(project, firstOrderRepairTask);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());

        WishlistEntity secondOrderWishlist = captor.getValue();
        assertEquals(firstOrderRepairTaskId, secondOrderWishlist.getSourceTaskId());
        assertEquals(productEpicId, secondOrderWishlist.getFeatureId(), "Second-order repair must inherit original product epic");
    }

    @Test
    @DisplayName("Law 8: Absorbing condition records terminal failure in DefectJournal when repair depth exceeds limit")
    void absorbingConditionEnforcedWhenRepairDepthExceedsLimit() {
        // Setup a chain at depth 2
        UUID task0Id = UUID.randomUUID();
        TaskEntity task0 = new TaskEntity();
        task0.setId(task0Id);
        task0.setProject(project);
        task0.setFeatureId(productEpicId);
        when(taskRepository.findById(task0Id)).thenReturn(Optional.of(task0));

        UUID w1Id = UUID.randomUUID();
        WishlistEntity w1 = new WishlistEntity();
        w1.setId(w1Id);
        w1.setSource(WishlistSource.delivery_never_reached_main);
        w1.setSourceTaskId(task0Id);
        when(wishlistRepository.findById(w1Id)).thenReturn(Optional.of(w1));

        UUID task1Id = UUID.randomUUID();
        TaskEntity task1 = new TaskEntity();
        task1.setId(task1Id);
        task1.setProject(project);
        task1.setSourceWishlistId(w1Id);
        when(taskRepository.findById(task1Id)).thenReturn(Optional.of(task1));

        UUID w2Id = UUID.randomUUID();
        WishlistEntity w2 = new WishlistEntity();
        w2.setId(w2Id);
        w2.setSource(WishlistSource.delivery_never_reached_main);
        w2.setSourceTaskId(task1Id);
        when(wishlistRepository.findById(w2Id)).thenReturn(Optional.of(w2));

        // Task 2 is the second-order repair task
        UUID task2Id = UUID.randomUUID();
        TaskEntity task2 = new TaskEntity();
        task2.setId(task2Id);
        task2.setProject(project);
        task2.setStatus(TaskStatus.failed);
        task2.setSourceWishlistId(w2Id);
        task2.setFeatureId(productEpicId);
        when(taskRepository.findById(task2Id)).thenReturn(Optional.of(task2));

        // Next repair would be depth 3 > maxRepairDepth (2)
        assertEquals(3, service.repairDepthForTask(task2));

        service.fileTheMissingWorkAsScope(project, task2);

        // Wishlist must NOT be saved (repair cycle halts)
        verify(wishlistRepository, never()).save(any());

        // Absorbing condition must be recorded in DefectJournalEntity
        ArgumentCaptor<DefectJournalEntity> journalCaptor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(journalCaptor.capture());

        DefectJournalEntity defect = journalCaptor.getValue();
        assertEquals(projectId, defect.getProjectId());
        assertEquals(productEpicId, defect.getFeatureId());
        assertEquals("REPAIR_BUDGET_EXHAUSTED", defect.getDefectType());
        assertEquals("CRITICAL", defect.getSeverity());
        assertEquals("DeliveryRealityProducerService", defect.getSourceComponent());
        assertTrue(defect.getDescription().contains("reached max repair depth (3/2)"));
    }

    @Test
    @DisplayName("Law 7 & 8: Orphan product task without reachable product epic records PRODUCT_EPIC_UNREACHABLE")
    void orphanProductTaskWithoutEpicRecordsTerminalDefect() {
        TaskEntity orphan = new TaskEntity();
        orphan.setId(UUID.randomUUID());
        orphan.setProject(project);
        orphan.setStatus(TaskStatus.done);
        orphan.setTitle("Orphan Task");
        orphan.setFeatureId(null);
        orphan.setSourceWishlistId(null);

        service.fileTheMissingWorkAsScope(project, orphan);

        // Scope must NOT be filed
        verify(wishlistRepository, never()).save(any());

        // Must record terminal defect in journal
        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());

        DefectJournalEntity defect = captor.getValue();
        assertEquals(projectId, defect.getProjectId());
        assertEquals("PRODUCT_EPIC_UNREACHABLE", defect.getDefectType());
        assertEquals("CRITICAL", defect.getSeverity());
    }

    @Test
    @DisplayName("Law 7: Canonical epic resolution returns canonical winner from union-find")
    void canonicalEpicResolutionResolvesUnionFindWinner() {
        UUID loserEpicId = UUID.randomUUID();
        UUID canonicalWinnerId = productEpicId;

        when(readinessService.canonicalFeatureId(loserEpicId)).thenReturn(canonicalWinnerId);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFeatureId(loserEpicId);

        UUID resolved = service.epicOfRequirement(task);
        assertEquals(canonicalWinnerId, resolved, "Must resolve canonical winner from union-find");
    }
}
