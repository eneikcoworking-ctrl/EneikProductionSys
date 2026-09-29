package com.eneik.production.services;

import com.eneik.production.dto.WishlistResponseDto;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Falsification test suite for {@link WishlistService} grounding Stage 4 invariants:
 *
 * <p>1. Varzi (D002, {@code AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER}):
 * Actual domain objects possess explicit identity, lifecycle transitions, and deletion semantics.
 * {@code dismiss} performs soft archiving (transitioning status to {@link WishlistStatus#dismissed}
 * to preserve audit trail and prevent compilation), whereas {@code hardDelete} physically purges
 * the record from the repository.
 *
 * <p>2. Raz (D006, {@code DZHOZEF_RAZ_01_PROHIBITION_AS_CODE}):
 * Normative prohibition as code. Attempting to dismiss or hard-delete a non-existent wishlist item
 * is strictly forbidden and produces an explicit {@link ResponseStatusException} with status
 * {@link HttpStatus#NOT_FOUND}, rather than failing silently.
 *
 * <p>3. Goldman (D010, {@code ELVIN_GOLDMAN_01_RELIABILITY_CHAIN}):
 * Causal theory of knowing and reliable data lineage. {@code purgeGhostWishlists} purges
 * decommissioned/unreliable sources (gemini_observer, delivery_never_reached_main,
 * design_review_concern_pattern) and historical phantom defect text patterns from the active pool,
 * while leaving genuine client and engineering requirements intact.
 */
class WishlistServiceFalsificationTest {

    private WishlistRepository wishlistRepository;
    private WishlistService wishlistService;

    @BeforeEach
    void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        wishlistService = new WishlistService(wishlistRepository);
    }

    private WishlistEntity createWishlist(UUID id, UUID projectId, WishlistSource source, String content, WishlistStatus status) {
        WishlistEntity entity = new WishlistEntity();
        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setSource(source);
        entity.setContent(content);
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    // =========================================================================
    // 1. Varzi (D002): AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER
    // =========================================================================

    @Test
    @DisplayName("Varzi [D002]: dismiss performs soft archiving, preserving record with dismissed status")
    void falsifyVarzi_actualObjectRegister_dismissSoftArchivesWishlist() {
        UUID wishlistId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        WishlistEntity entity = createWishlist(wishlistId, projectId, WishlistSource.client,
                "Configure JWT token rotation", WishlistStatus.pending);

        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(entity));

        wishlistService.dismiss(wishlistId);

        assertThat(entity.getStatus())
                .as("Dismiss must transition entity to dismissed status rather than deleting it")
                .isEqualTo(WishlistStatus.dismissed);

        // Verify hard delete was never called
        verify(wishlistRepository, never()).deleteById(any());
        verify(wishlistRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("Varzi [D002]: hardDelete executes permanent physical removal from repository")
    void falsifyVarzi_actualObjectRegister_hardDeletePhysicallyRemovesEntity() {
        UUID wishlistId = UUID.randomUUID();

        when(wishlistRepository.existsById(wishlistId)).thenReturn(true);

        wishlistService.hardDelete(wishlistId);

        verify(wishlistRepository).deleteById(wishlistId);
    }

    // =========================================================================
    // 2. Raz (D006): DZHOZEF_RAZ_01_PROHIBITION_AS_CODE
    // =========================================================================

    @Test
    @DisplayName("Raz [D006]: dismissing non-existent item is strictly prohibited and fails with 404 NOT_FOUND")
    void falsifyRaz_prohibitionAsCode_dismissingNonExistentItemThrowsNotFound() {
        UUID missingId = UUID.randomUUID();
        when(wishlistRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.dismiss(missingId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("Wishlist item not found");
                });
    }

    @Test
    @DisplayName("Raz [D006]: hard-deleting non-existent item is strictly prohibited and fails with 404 NOT_FOUND")
    void falsifyRaz_prohibitionAsCode_hardDeletingNonExistentItemThrowsNotFound() {
        UUID missingId = UUID.randomUUID();
        when(wishlistRepository.existsById(missingId)).thenReturn(false);

        assertThatThrownBy(() -> wishlistService.hardDelete(missingId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("Wishlist item not found");
                });

        verify(wishlistRepository, never()).deleteById(any());
    }

    // =========================================================================
    // 3. Goldman (D010): ELVIN_GOLDMAN_01_RELIABILITY_CHAIN
    // =========================================================================

    @Test
    @DisplayName("Goldman [D010]: purgeGhostWishlists reliably cleanses ghost sources and phantom patterns")
    @SuppressWarnings("unchecked")
    void falsifyGoldman_reliabilityChain_purgeGhostWishlistsCleansesDecommissionedSources() {
        UUID projectId = UUID.randomUUID();

        // 1. Ghost source: gemini_observer
        WishlistEntity ghost1 = createWishlist(UUID.randomUUID(), projectId, WishlistSource.gemini_observer,
                "Observer noticed gap", WishlistStatus.pending);

        // 2. Ghost source: delivery_never_reached_main
        WishlistEntity ghost2 = createWishlist(UUID.randomUUID(), projectId, WishlistSource.delivery_never_reached_main,
                "Delivery discrepancy", WishlistStatus.pending);

        // 3. Phantom content pattern: "Six Sigma u-chart out of control"
        WishlistEntity ghost3 = createWishlist(UUID.randomUUID(), projectId, WishlistSource.chaotic_debt,
                "Six Sigma u-chart out of control on build metrics", WishlistStatus.pending);

        // 4. Valid client requirement: must NOT be purged
        WishlistEntity validItem1 = createWishlist(UUID.randomUUID(), projectId, WishlistSource.client,
                "User profile avatar resize", WishlistStatus.pending);

        // 5. Valid coverage gap: must NOT be purged
        WishlistEntity validItem2 = createWishlist(UUID.randomUUID(), projectId, WishlistSource.coverage_gap,
                "Implement rate limiter on telegram outbound messages", WishlistStatus.pending);

        when(wishlistRepository.findByProjectId(projectId))
                .thenReturn(List.of(ghost1, ghost2, ghost3, validItem1, validItem2));

        int purgedCount = wishlistService.purgeGhostWishlists(projectId);

        assertThat(purgedCount).as("Must purge exactly the 3 ghost/phantom items").isEqualTo(3);

        ArgumentCaptor<List<WishlistEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(wishlistRepository).deleteAll(captor.capture());

        List<WishlistEntity> purgedList = captor.getValue();
        assertThat(purgedList).containsExactlyInAnyOrder(ghost1, ghost2, ghost3);
        assertThat(purgedList).doesNotContain(validItem1, validItem2);
    }

    @Test
    @DisplayName("Goldman [D010]: listByProject accurately projects entities to DTOs without leaking state")
    void falsifyGoldman_reliabilityChain_listByProjectDTOProjection() {
        UUID projectId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();
        WishlistEntity entity = createWishlist(wishlistId, projectId, WishlistSource.client,
                "Audit export PDF", WishlistStatus.pending);

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.pending))
                .thenReturn(List.of(entity));

        List<WishlistResponseDto> dtos = wishlistService.listByProject(projectId, WishlistStatus.pending);

        assertThat(dtos).hasSize(1);
        WishlistResponseDto dto = dtos.get(0);
        assertThat(dto.id()).isEqualTo(wishlistId);
        assertThat(dto.projectId()).isEqualTo(projectId);
        assertThat(dto.source()).isEqualTo(WishlistSource.client);
        assertThat(dto.content()).isEqualTo("Audit export PDF");
        assertThat(dto.status()).isEqualTo(WishlistStatus.pending);
    }
}
