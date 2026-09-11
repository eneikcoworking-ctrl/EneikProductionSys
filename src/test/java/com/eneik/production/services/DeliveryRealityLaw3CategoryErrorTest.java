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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Proof obligations for Law 3 and D002 Category Error Scan:
 * - Pattern: `DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` (D002 Invalid state / Category Error).
 *   Anchor: How to Do Things with Words - speech acts and performatives.
 *   Principle: An observation about our own delivery failure is not authority to order new customer scope.
 *   Writing a requirement is a performative act that establishes scope; establishing scope from our own failure
 *   treats an observation as authority without an adapter, causing a self-ordering loop (196 briefs for 15 requests).
 * - Law 3 (Domain Restriction of Finding):
 *   An observation that delivery failed for an already ordered and uncancelled requirement is a delivery defect fact,
 *   NOT a new customer requirement.
 * - Invariant / Guard: Failure of delivery of an already ordered requirement creates NO new wishlist.
 * - Refutation: "провалили доставку клиентского требования дважды — заявок ноль, записей дефекта доставки две".
 */
class DeliveryRealityLaw3CategoryErrorTest {

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
        project.setName("customer-product");
        project.setStatus(ProjectStatus.active);

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(readinessService.listEpicDiagnostics(projectId)).thenReturn(List.of(
                new ClientDeliverableReadinessService.EpicDiagnostic(
                        productEpicId, "Customer Core API", null, Instant.now(), true, false, 0, 0)
        ));
        when(systemSettingsService.effectiveInt(eq(DeliveryRealityProducerService.MAX_REPAIR_DEPTH_KEY), anyInt()))
                .thenReturn(2);
    }

    @Test
    @DisplayName("Law 3 Refutation: failing delivery of client requirement twice creates 0 wishlists and 2 delivery defect records")
    void failingDeliveryOfClientRequirementTwice_createsZeroWishlistsAndTwoDeliveryDefectRecords() {
        // Given: client requirement already ordered and uncancelled
        UUID clientWishlistId = UUID.randomUUID();
        WishlistEntity clientWishlist = new WishlistEntity();
        clientWishlist.setId(clientWishlistId);
        clientWishlist.setProjectId(projectId);
        clientWishlist.setFeatureId(productEpicId);
        clientWishlist.setSource(WishlistSource.client);
        clientWishlist.setStatus(WishlistStatus.converted_to_task);

        when(wishlistRepository.findById(clientWishlistId)).thenReturn(Optional.of(clientWishlist));
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndStatusNot(
                projectId, productEpicId, WishlistStatus.dismissed)).thenReturn(true);

        List<DefectJournalEntity> recordedDefects = new ArrayList<>();
        when(defectJournalRepository.save(any(DefectJournalEntity.class))).thenAnswer(invocation -> {
            DefectJournalEntity d = invocation.getArgument(0);
            recordedDefects.add(d);
            return d;
        });

        // Turn 1: First delivery failure of the client requirement
        UUID task1Id = UUID.randomUUID();
        TaskEntity task1 = new TaskEntity();
        task1.setId(task1Id);
        task1.setProject(project);
        task1.setStatus(TaskStatus.done);
        task1.setTitle("Customer Checkout API (Attempt 1)");
        task1.setSourceWishlistId(clientWishlistId);
        task1.setFeatureId(productEpicId);
        when(taskRepository.findById(task1Id)).thenReturn(Optional.of(task1));

        service.fileTheMissingWorkAsScope(project, task1);

        // Turn 2: Second delivery failure of the same client requirement
        UUID task2Id = UUID.randomUUID();
        TaskEntity task2 = new TaskEntity();
        task2.setId(task2Id);
        task2.setProject(project);
        task2.setStatus(TaskStatus.done);
        task2.setTitle("Customer Checkout API (Attempt 2)");
        task2.setSourceWishlistId(clientWishlistId);
        task2.setFeatureId(productEpicId);
        when(taskRepository.findById(task2Id)).thenReturn(Optional.of(task2));

        service.fileTheMissingWorkAsScope(project, task2);

        // Invariant under Law 3 & Austin adapter (DZHON_OSTIN_02_CATEGORY_ERROR_SCAN):
        // 1. Zero new wishlists are created (no self-ordering loop)
        verify(wishlistRepository, never()).save(any(WishlistEntity.class));

        // 2. Exactly two delivery defect records are written to DefectJournalEntity
        assertEquals(2, recordedDefects.size(),
                "Refutation: failing delivery of client requirement twice must create exactly 2 delivery defect records");

        for (DefectJournalEntity defect : recordedDefects) {
            assertEquals(projectId, defect.getProjectId());
            assertEquals(productEpicId, defect.getFeatureId());
            assertEquals(DeliveryRealityProducerService.DELIVERY_EXHAUSTED_CATEGORY, defect.getCategory());
            assertEquals(DeliveryRealityProducerService.REPEATED_DELIVERY_FAILURE, defect.getDefectType());
            assertTrue(defect.getDescription().contains("Law 3"),
                    "Defect description must ground in Law 3 / D002");
        }
    }

    @Test
    @DisplayName("Law 3: Un-ordered synthetic work without existing requirement is permitted to file scope")
    void unOrderedSyntheticWorkWithoutExistingRequirementFilesInitialScope() {
        TaskEntity syntheticTask = new TaskEntity();
        syntheticTask.setId(UUID.randomUUID());
        syntheticTask.setProject(project);
        syntheticTask.setStatus(TaskStatus.done);
        syntheticTask.setTitle("One-off Migration Tool");
        syntheticTask.setFeatureId(productEpicId);
        // Has no sourceWishlistId, and no wishlists exist for this epic in repository
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndStatusNot(
                projectId, productEpicId, WishlistStatus.dismissed)).thenReturn(false);
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndSource(
                projectId, productEpicId, WishlistSource.delivery_never_reached_main)).thenReturn(false);

        service.fileTheMissingWorkAsScope(project, syntheticTask);

        // Files initial scope
        verify(wishlistRepository).save(any(WishlistEntity.class));
        verify(defectJournalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Law 8: DEFAULT_MAX_REPAIR_DEPTH is 2 (kept as declared in record)")
    void defaultMaxRepairDepthIsTwo() {
        assertEquals(2, DeliveryRealityProducerService.DEFAULT_MAX_REPAIR_DEPTH,
                "DEFAULT_MAX_REPAIR_DEPTH must be 2 as declared");
    }

    @Test
    @DisplayName("Law 3: isRequirementAlreadyOrdered recognizes active client wishlists and epics")
    void isRequirementAlreadyOrderedRecognizesActiveClientWishlistsAndEpics() {
        // Case 1: Task with active client wishlist -> true
        UUID clientWishlistId = UUID.randomUUID();
        WishlistEntity clientWishlist = new WishlistEntity();
        clientWishlist.setId(clientWishlistId);
        clientWishlist.setStatus(WishlistStatus.converted_to_task);
        when(wishlistRepository.findById(clientWishlistId)).thenReturn(Optional.of(clientWishlist));

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setSourceWishlistId(clientWishlistId);
        assertTrue(service.isRequirementAlreadyOrdered(projectId, productEpicId, task));

        // Case 2: Task with dismissed client wishlist and no active epic wishlists -> false
        clientWishlist.setStatus(WishlistStatus.dismissed);
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndStatusNot(
                projectId, productEpicId, WishlistStatus.dismissed)).thenReturn(false);
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndSource(
                projectId, productEpicId, WishlistSource.delivery_never_reached_main)).thenReturn(false);
        assertFalse(service.isRequirementAlreadyOrdered(projectId, productEpicId, task));

        // Case 3: Epic has active uncancelled wishlists -> true
        when(wishlistRepository.existsByProjectIdAndFeatureIdAndStatusNot(
                projectId, productEpicId, WishlistStatus.dismissed)).thenReturn(true);
        assertTrue(service.isRequirementAlreadyOrdered(projectId, productEpicId, new TaskEntity()));
    }
}
