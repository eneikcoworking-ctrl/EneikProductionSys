package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Phase 0 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md: a one-shot,
// non-repeating check of whether the active project has a documented way to run itself. These
// tests pin down the three real requirements the operator pushed back on twice before accepting
// this plan: (1) never checked before "delivered", (2) checked at most once ever, (3) a missing
// compose file creates exactly one dedup-guarded wishlist item through the normal compiler path,
// never code written directly into the client repo.
class ProductLaunchabilityServiceTest {

    private ProjectEntity deliveredProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setDefaultBranch("main");
        return project;
    }

    @Test
    void neverChecksAProjectThatIsNotYetDelivered() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = new ProductLaunchabilityService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 0, 1, 0, 0.0, false));

        service.checkOnce(project);

        verify(gitHub, never()).fetchFileContent(any(), any(), any());
        verify(projects, never()).save(any());
        assertNull(project.getLaunchabilityCheckedAt());
    }

    @Test
    void aDeliveredProjectWithNoComposeFileGetsExactlyOneDedupGuardedWishlist() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = new ProductLaunchabilityService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.empty());
        when(wishlists.existsByProjectIdAndSource(project.getId(), WishlistSource.runtime_observability_gap))
                .thenReturn(false);

        service.checkOnce(project);

        verify(wishlists, times(1)).save(any(WishlistEntity.class));
        verify(projects).save(project);
        assertNotNull(project.getLaunchabilityCheckedAt());
    }

    @Test
    void aDeliveredProjectThatAlreadyHasAWishlistItemNeverGetsASecondOne() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = new ProductLaunchabilityService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.empty());
        when(wishlists.existsByProjectIdAndSource(project.getId(), WishlistSource.runtime_observability_gap))
                .thenReturn(true);

        service.checkOnce(project);

        verify(wishlists, never()).save(any());
        assertNotNull(project.getLaunchabilityCheckedAt());
    }

    @Test
    void aDeliveredProjectWithAComposeFileCreatesNoWishlistAndIsMarkedCheckedForever() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = new ProductLaunchabilityService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(gitHub.fetchFileContent(eq(project), eq("main"), eq("docker-compose.yml")))
                .thenReturn(Optional.of("services: {}"));

        service.checkOnce(project);

        verify(wishlists, never()).save(any());
        assertNotNull(project.getLaunchabilityCheckedAt());
    }

    @Test
    void anAlreadyCheckedProjectIsNeverCheckedAgain() {
        var projects = mock(ProjectRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var gitHub = mock(GitHubPullRequestService.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var service = new ProductLaunchabilityService(projects, wishlists, gitHub, readiness);

        ProjectEntity project = deliveredProject();
        project.setLaunchabilityCheckedAt(Instant.now());

        service.checkOnce(project);

        verify(readiness, never()).computeForProject(any());
        verify(gitHub, never()).fetchFileContent(any(), any(), any());
        verify(projects, never()).save(any());
    }
}
