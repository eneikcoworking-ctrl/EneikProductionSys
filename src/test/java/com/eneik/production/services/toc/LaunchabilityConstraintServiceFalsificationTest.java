package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Strict Popperian falsification harness (KARL_POPPER_01_FALSIFICATION_HARNESS / D008)
 * for the single owner of the launchability constraint: {@link LaunchabilityConstraintService}.
 *
 * <p>Invariant checklist (D006 BOUNDARY_TOPOLOGY, D012 PRINCIPLED_INTEGRITY):
 * 1. Movability check: An in-flight movable attempt strictly suppresses double filing.
 * 2. Cooldown check: A finished/unmovable attempt enforces a 45-minute cooldown before re-filing.
 * 3. Cooldown expiration: An unmovable attempt older than 45 minutes permits re-filing.
 * 4. Boundary topology: The filed wishlist item is strictly routed to BARCAN-TAG-00, PRODUCT_CODEBASE,
 *    and carries the empirical launcher cause verbatim.
 */
class LaunchabilityConstraintServiceFalsificationTest {

    private WishlistRepository wishlistRepository;
    private LaunchabilityConstraintService service;
    private ProjectEntity project;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        service = new LaunchabilityConstraintService(wishlistRepository);
        ReflectionTestUtils.setField(service, "constraintRefileCooldownMinutes", 45L);

        projectId = UUID.randomUUID();
        project = new ProjectEntity();
        project.setId(projectId);
    }

    @Test
    void emptyExistingListTriggersImmediateFilingWithRigidInvariants() {
        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(eq(projectId), eq(WishlistSource.product_not_launchable), any()))
                .thenReturn(Collections.emptyList());

        String cause = "docker-compose: port 18080 already allocated";
        service.ensureOpen(project, cause);

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());

        WishlistEntity saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getTargetContext()).isEqualTo(TargetContext.PRODUCT_CODEBASE);
        assertThat(saved.getSource()).isEqualTo(WishlistSource.product_not_launchable);
        assertThat(saved.getStatus()).isEqualTo(WishlistStatus.pending);
        assertThat(saved.getLeanValue()).isEqualTo(LeanValue.essential);
        assertThat(saved.getCynefinDomain()).isEqualTo("clear");
        assertThat(saved.getSourceRoleTag()).isEqualTo("BARCAN-TAG-00");
        assertThat(saved.getContent()).contains(cause);
    }

    @Test
    void inFlightMovableAttemptPreventsDoubleFiling() {
        WishlistEntity inFlight = new WishlistEntity();
        inFlight.setStatus(WishlistStatus.pending);
        inFlight.setCompileAttempts(0); // compileAttempts < ceiling => movable() == true

        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(eq(projectId), eq(WishlistSource.product_not_launchable), any()))
                .thenReturn(List.of(inFlight));

        service.ensureOpen(project, "Connection refused: /actuator/health");

        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void failedUnmovableAttemptWithinCooldownPreventsReFiling() {
        // Attempt refused decomposition (budget exhausted + compiler reached) => movable() == false
        WishlistEntity refused = new WishlistEntity();
        refused.setStatus(WishlistStatus.pending);
        refused.setCompileAttempts(6);
        refused.setLastCompileReachedAt(Instant.now().minus(Duration.ofMinutes(10)));
        refused.setCreatedAt(Instant.now().minus(Duration.ofMinutes(15))); // within 45 min cooldown

        assertThat(refused.movable()).isFalse();

        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(eq(projectId), eq(WishlistSource.product_not_launchable), any()))
                .thenReturn(List.of(refused));

        service.ensureOpen(project, "Health check failed 503");

        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void failedUnmovableAttemptAfterCooldownExpiresTriggersReFiling() {
        WishlistEntity refusedOld = new WishlistEntity();
        refusedOld.setStatus(WishlistStatus.converted_to_task);
        refusedOld.setCreatedAt(Instant.now().minus(Duration.ofMinutes(50))); // > 45 min cooldown

        assertThat(refusedOld.movable()).isFalse();

        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(eq(projectId), eq(WishlistSource.product_not_launchable), any()))
                .thenReturn(List.of(refusedOld));

        service.ensureOpen(project, "CrashLoopBackOff");

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceRoleTag()).isEqualTo("BARCAN-TAG-00");
        assertThat(captor.getValue().getContent()).contains("CrashLoopBackOff");
    }

    @Test
    void nullOrBlankObservedCauseFallsBackGracefullyWithoutCrashing() {
        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(eq(projectId), eq(WishlistSource.product_not_launchable), any()))
                .thenReturn(Collections.emptyList());

        service.ensureOpen(project, "   ");

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).doesNotContain("Observed failure, exactly as the launcher recorded it");
        assertThat(captor.getValue().getSourceRoleTag()).isEqualTo("BARCAN-TAG-00");
    }
}
