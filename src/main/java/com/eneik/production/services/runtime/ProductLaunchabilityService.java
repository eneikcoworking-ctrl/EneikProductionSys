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
    private static final String DOCKERFILE_PATH = "Dockerfile";
    private static final String FRONTEND_MARKER_PATH = "frontend/package.json";

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final ClientDeliverableReadinessService readinessService;

    // Self-injected proxy reference (2026-08-14, same pattern/reason as JulesDispatchService.self): a plain
    // `this.recordLaunchabilityResult(...)` call bypasses the Spring AOP proxy entirely, so
    // @Transactional(REQUIRES_NEW) on it would silently never activate. @Lazy breaks the constructor
    // circular dependency this would otherwise create.
    private final ProductLaunchabilityService self;

    public ProductLaunchabilityService(ProjectRepository projectRepository,
                                        WishlistRepository wishlistRepository,
                                        GitHubPullRequestService gitHubPullRequestService,
                                        ClientDeliverableReadinessService readinessService,
                                        @org.springframework.context.annotation.Lazy ProductLaunchabilityService self) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.readinessService = readinessService;
        this.self = self;
    }

    // 2026-08-14 (bug-hunt sweep): used to be one @Transactional method holding a DB transaction open
    // across up to 3 sequential real GitHub fetchFileContent calls (compose file, Dockerfile, frontend
    // marker - the Dockerfile was even fetched twice, once per check). Same bug class as the 2026-08-07
    // lock-timeout incident. Now: readiness check and all GitHub fetches happen with no transaction open,
    // the Dockerfile is fetched once and reused, and only the final decision + wishlist/project writes run
    // inside a short REQUIRES_NEW transaction.
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

        String dockerfile = null;
        boolean hasFrontendMarker = false;
        if (hasComposeFile) {
            dockerfile = gitHubPullRequestService
                    .fetchFileContent(project, project.getDefaultBranch(), DOCKERFILE_PATH)
                    .orElse(null);
            hasFrontendMarker = gitHubPullRequestService
                    .fetchFileContent(project, project.getDefaultBranch(), FRONTEND_MARKER_PATH)
                    .isPresent();
        }

        self.recordLaunchabilityResult(project.getId(), hasComposeFile, dockerfile, hasFrontendMarker);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordLaunchabilityResult(java.util.UUID projectId, boolean hasComposeFile, String dockerfile,
                                           boolean hasFrontendMarker) {
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }

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
            checkDockerfileIsSelfBuildable(project, dockerfile);
            checkFrontendIsDeployed(project, dockerfile, hasFrontendMarker);
        }

        project.setLaunchabilityCheckedAt(Instant.now());
        projectRepository.save(project);
    }

    /**
     * Confirmed live gap (test-forty-third, 2026-08-11): a Dockerfile doing `COPY target/*.jar app.jar`
     * with no build stage looks fine at review time (the repo compiles, tests pass) but fails the moment
     * anyone actually runs `docker compose up --build` on a fresh clone, because target/ is gitignored.
     * A crude but reliable signal: any `COPY` line referencing a `target/` path, with no earlier `FROM
     * ... AS` build-stage line in the same file - a real multi-stage build (see this system's own
     * Dockerfile.backend) always has one.
     */
    private void checkDockerfileIsSelfBuildable(ProjectEntity project, String dockerfile) {
        if (wishlistRepository.existsByProjectIdAndSource(project.getId(), WishlistSource.dockerfile_missing_build_stage)) {
            return;
        }
        if (dockerfile == null) {
            return;
        }
        boolean copiesPrebuiltArtifact = dockerfile.lines()
                .anyMatch(line -> line.stripLeading().startsWith("COPY") && line.contains("target/"));
        boolean hasBuildStage = dockerfile.lines()
                .anyMatch(line -> line.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("FROM")
                        && line.toUpperCase(java.util.Locale.ROOT).contains(" AS "));
        if (!copiesPrebuiltArtifact || hasBuildStage) {
            return;
        }

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.dockerfile_missing_build_stage);
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setLeanValue(LeanValue.valuable);
        wishlist.setContent("This project's " + DOCKERFILE_PATH + " copies a pre-built artifact from a "
                + "target/ path with no preceding build stage, so `docker compose up --build` fails on a "
                + "fresh clone (target/ is gitignored, nothing ever builds it). Rewrite it as a multi-stage "
                + "build - a Maven build stage that produces the jar, then a slim runtime stage that copies "
                + "it - the same pattern this factory's own Dockerfile.backend already uses.");
        wishlist.setJtbd("When someone clones this repository fresh and runs `docker compose up --build`, "
                + "I want the image to build successfully without any manual pre-build step, so the product "
                + "can actually be launched and observed for real");
        wishlist.setAcceptanceCriteria("Given a fresh clone of the repository at its default branch, When "
                + "`docker compose up --build` is run, Then the image builds successfully with no pre-existing "
                + "target/ artifact required");
        wishlist.setDod(DOCKERFILE_PATH + " uses a multi-stage build that compiles the artifact itself");
        wishlistRepository.save(wishlist);
        log.info("ProductLaunchabilityService: project {} Dockerfile copies a pre-built target/ artifact with "
                + "no build stage - created dockerfile_missing_build_stage wishlist", project.getId());
    }

    /**
     * Confirmed live gap (test-forty-third, 2026-08-11): a real frontend/ directory exists but the
     * Dockerfile never references it, so the deployable image is backend-only - a real user opening the
     * launched product sees a bare API, not the actual UI.
     */
    private void checkFrontendIsDeployed(ProjectEntity project, String dockerfile, boolean hasFrontendMarker) {
        if (wishlistRepository.existsByProjectIdAndSource(project.getId(), WishlistSource.frontend_not_deployed)) {
            return;
        }
        if (!hasFrontendMarker) {
            return;
        }
        if (dockerfile == null || dockerfile.toLowerCase(java.util.Locale.ROOT).contains("frontend")) {
            return;
        }

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.frontend_not_deployed);
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setLeanValue(LeanValue.valuable);
        wishlist.setContent("This project has a real frontend/ directory, but " + DOCKERFILE_PATH
                + " never builds or serves it - the deployable image is backend-only. Build the frontend "
                + "(npm ci && npm run build) as part of the image build and have the backend serve the "
                + "built static output, so a real user opening the launched product sees the actual UI, not "
                + "a bare API.");
        wishlist.setJtbd("When the product is launched, I want to see the real frontend, not just the "
                + "backend API, so the delivered product is actually usable");
        wishlist.setAcceptanceCriteria("Given the launched product, When its root URL is opened in a "
                + "browser, Then the real frontend UI is served, not a bare API response");
        wishlist.setDod(DOCKERFILE_PATH + " builds the frontend and the running product serves it");
        wishlistRepository.save(wishlist);
        log.info("ProductLaunchabilityService: project {} has frontend/ but Dockerfile never references it - "
                + "created frontend_not_deployed wishlist", project.getId());
    }
}
