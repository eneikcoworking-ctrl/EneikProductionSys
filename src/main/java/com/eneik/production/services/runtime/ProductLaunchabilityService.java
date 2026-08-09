package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Phase 0 of the client-runtime-observability plan (docs/reports/PLAN_client_runtime_observability_2026-08-09.md):
 * a one-shot, non-repeating check of whether the active project has a documented way to run itself
 * locally (docker-compose.yml at repo root). Deliberately the cheapest possible first step - everything
 * else in the plan (live health sampling, control-chart drift detection, product Kaizen) is meaningless
 * if the product can't even be started.
 *
 * Runs from ContinuousOrchestrationService's existing per-project tick (gated by
 * OperationalAction.CHECK_LAUNCHABILITY) - deliberately NOT a new @Scheduled cron. Checks at most once
 * per project ever: {@link ProjectEntity#getLaunchabilityCheckedAt()} is set unconditionally after the
 * first check, whatever the result, so this never re-fetches from GitHub on every tick forever.
 *
 * If the file is missing, this creates exactly one dedup-guarded wishlist item (never a second one -
 * see WishlistRepository.existsByProjectIdAndSource) and routes it through the normal
 * wishlist-compiler -> task -> Jules path, same as any other product requirement. This deliberately does
 * NOT write code into the client repo directly - see tonight's incident (test-forty-third,
 * 2026-08-07..09) for why bypassing that path is exactly how self-referential contamination happens.
 */
@Service
public class ProductLaunchabilityService {
    private static final Logger log = LoggerFactory.getLogger(ProductLaunchabilityService.class);
    private static final String COMPOSE_FILE_PATH = "docker-compose.yml";

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final ClientDeliverableReadinessService readinessService;

    public ProductLaunchabilityService(ProjectRepository projectRepository,
                                        WishlistRepository wishlistRepository,
                                        GitHubPullRequestService gitHubPullRequestService,
                                        ClientDeliverableReadinessService readinessService) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.readinessService = readinessService;
    }

    @Transactional
    public void checkOnce(ProjectEntity project) {
        if (project.getLaunchabilityCheckedAt() != null) {
            return;
        }
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        boolean delivered = readiness.decompositionComplete() && readiness.ratio() >= 1.0;
        if (!delivered) {
            // Not yet delivered - stays unchecked, tried again on a later tick once it genuinely is.
            return;
        }
        boolean hasComposeFile = gitHubPullRequestService
                .fetchFileContent(project, project.getDefaultBranch(), COMPOSE_FILE_PATH)
                .isPresent();

        if (!hasComposeFile && !wishlistRepository.existsByProjectIdAndSource(
                project.getId(), WishlistSource.runtime_observability_gap)) {
            WishlistEntity wishlist = new WishlistEntity();
            wishlist.setProjectId(project.getId());
            wishlist.setSource(WishlistSource.runtime_observability_gap);
            wishlist.setStatus(WishlistStatus.pending);
            wishlist.setLeanValue(LeanValue.valuable);
            wishlist.setContent("This delivered project has no " + COMPOSE_FILE_PATH + " at its repository "
                    + "root, so it cannot currently be started and observed the way a real running product "
                    + "needs to be. Add a docker-compose.yml (or equivalent) that starts the full stack and "
                    + "exposes a health-check endpoint, so the product can be launched and verified for real, "
                    + "not just assumed working because its build passed.");
            wishlist.setJtbd("When the product has been fully built, I want a documented, working way to "
                    + "actually run it, so its real behavior can be observed instead of only its build-time "
                    + "evidence");
            wishlist.setAcceptanceCriteria("Given the repository at its default branch, When `docker compose "
                    + "up` is run, Then the full product stack starts and a health-check endpoint responds");
            wishlist.setDod("docker-compose.yml exists at the repository root and a real health-check "
                    + "endpoint responds after `docker compose up`");
            wishlistRepository.save(wishlist);
            log.info("ProductLaunchabilityService: project {} has no {} - created runtime_observability_gap wishlist",
                    project.getId(), COMPOSE_FILE_PATH);
        } else if (hasComposeFile) {
            log.info("ProductLaunchabilityService: project {} already has {} - launchable", project.getId(), COMPOSE_FILE_PATH);
        }

        project.setLaunchabilityCheckedAt(Instant.now());
        projectRepository.save(project);
    }
}
