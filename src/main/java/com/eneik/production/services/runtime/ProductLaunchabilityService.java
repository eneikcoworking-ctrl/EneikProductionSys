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
    private static final String APPLICATION_PROPERTIES_PATH = "src/main/resources/application.properties";
    private static final String POM_PATH = "pom.xml";
    private static final String UNDECLARED = "UNDECLARED";
    private static final String TEST_PROPERTIES_PATH = "src/test/resources/application.properties";

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
        // 2026-08-19: the gate asks whether there is a product to check, NOT whether the product is
        // finished. It used to require decompositionComplete && ratio >= 1.0 - completeness - which
        // merged two axes that must stay apart: scope delivery is a quantity over the currently known
        // intent, operability is a state about now. Nothing about "does this repository describe a way to
        // run itself" depends on the scope being complete.
        //
        // The consequence was not cosmetic. This check is Phase 0: ClientRuntimeObservabilityService
        // refuses to observe until launchabilityCheckedAt is set, so gating it on completeness meant the
        // product could not be launched or observed AT ALL until every planned task had merged - while the
        // architecture subordinates everything to launchability precisely so that falsification has a real
        // running object to observe. A product that cannot be observed until it is finished cannot be
        // falsified while it is being built, which is the only time falsification could change it.
        //
        // The original intent survives: do not fetch from GitHub for a project whose repository has nothing
        // in it yet. That intent is served by EXISTENCE, not by completeness - the actualist rule
        // (RUT_BARKAN_MARKUS_01_ACTUAL_OBJECT_REGISTER): ask whether the object is there to be quantified
        // over, never whether it is complete.
        boolean somethingHasShipped = readiness.mergedDeliverables() > 0;
        if (!somethingHasShipped) {
            // Nothing merged yet - there is no product in the repository to describe a way of running.
            // Retried on a later tick, exactly as before.
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
    /**
     * Does the engine the application defaults to match the engine its compose stack actually provides?
     *
     * **This one is deliberately NOT behind `launchabilityCheckedAt`.** `checkOnce` runs exactly once per
     * project, ever - the flag is set in `recordLaunchabilityResult` and never cleared - so every check it
     * performs is a bootstrap gate that cannot see a defect introduced afterwards. Measured on
     * test-forty-ninth (plan §10.1): `application.properties` was committed at 05:58 and
     * `docker-compose.yml` at 06:10, twelve minutes later. By then this service had already run and would
     * never look again, so the disagreement between them was invisible to the factory for five days while
     * 148 tasks closed against a product that had never once answered.
     *
     * Two assertions, both decidable from the repository alone, no launch required:
     *
     *   A. the engine in `spring.datasource.url` is the engine compose provides
     *   B. the build manifest carries a driver for the engine compose provides
     *
     * A is the one that mattered: when they differ, every test and every review exercises one database
     * while delivery runs another, so an SQL statement valid in the first and meaningless in the second
     * passes every gate the factory has. That is not hypothetical - `CREATE ALIAS`, an H2-only statement,
     * survived 144 merged reviews and killed the product at character 8 of its first migration.
     *
     * Greenfield-safe by construction: no compose file, or a compose file declaring no datastore, means
     * there is nothing to disagree with and nothing is filed. A project on day zero cannot trip this.
     *
     * Both are heuristics over text, and they are written to be **quiet when unsure**: an engine this
     * cannot identify produces no claim rather than a guess.
     */
    public void checkDatastoreAgreement(ProjectEntity project) {
        if (wishlistRepository.existsByProjectIdAndSource(project.getId(),
                WishlistSource.datastore_artifacts_disagree)) {
            return;
        }
        String compose = gitHubPullRequestService
                .fetchFileContent(project, project.getDefaultBranch(), COMPOSE_FILE_PATH).orElse(null);
        if (compose == null) {
            return; // nothing ships yet - nothing to disagree with
        }
        String appProps = gitHubPullRequestService
                .fetchFileContent(project, project.getDefaultBranch(), APPLICATION_PROPERTIES_PATH).orElse(null);
        String pom = gitHubPullRequestService
                .fetchFileContent(project, project.getDefaultBranch(), POM_PATH).orElse(null);
        String testProps = gitHubPullRequestService
                .fetchFileContent(project, project.getDefaultBranch(), TEST_PROPERTIES_PATH).orElse(null);

        String shipped = datastoreProvidedByCompose(compose);
        if (shipped == null) {
            return; // the stack provides no datastore this check recognises - no claim
        }
        String defaulted = datastoreInDatasourceUrl(appProps);

        // The contract is the single source of truth; the other artifacts are consequences of it. Read it
        // FIRST, so a disagreement is reported against the declaration rather than as a quarrel between
        // two files of equal standing. Absent - older projects, or one whose bootstrap predates ADR-002 -
        // the check falls back to comparing the artifacts with each other, which is strictly weaker but
        // still decides the case that mattered.
        String contract = gitHubPullRequestService
                .fetchFileContent(project, project.getDefaultBranch(),
                        com.eneik.production.services.ProjectFlowService.RUNTIME_CONTRACT_PATH)
                .orElse(null);
        String declared = datastoreDeclaredByContract(contract);

        java.util.List<String> problems = new java.util.ArrayList<>();
        if (UNDECLARED.equals(declared)) {
            // The bootstrap wrote the question and nobody has answered it, yet the stack already provides
            // an engine. The decision was taken somewhere other than where it belongs - which is exactly
            // the shape ACP-104 describes, caught this time before it can rot.
            problems.add("the runtime contract still declares `datastore: UNDECLARED`, while `"
                    + COMPOSE_FILE_PATH + "` already provides **" + shipped + "**. The decision has been "
                    + "taken outside the contract. ARCHITECTURE (BARCAN-TAG-01) owns it: declare the engine "
                    + "there first, then make the other artifacts follow.");
        } else if (declared != null && !declared.equals(shipped)) {
            problems.add("the runtime contract declares **" + declared + "**, but `" + COMPOSE_FILE_PATH
                    + "` provides **" + shipped + "**. The contract is the source of truth; compose is a "
                    + "consequence of it.");
        }
        if (defaulted != null && !defaulted.equals(shipped)) {
            problems.add("`" + APPLICATION_PROPERTIES_PATH + "` defaults the application to **" + defaulted
                    + "**, while `" + COMPOSE_FILE_PATH + "` provides **" + shipped + "**. Every test and "
                    + "every review therefore exercises " + defaulted + " while delivery runs " + shipped
                    + ", so SQL that is valid in one and meaningless in the other passes every gate.");
        }
        // The fourth consequence, and the one that produced every incompatibility measured on this
        // factory. Both `CREATE ALIAS` and `bytea (Types#VARBINARY)` passed the whole suite and died in
        // delivery for the same reason: the suite ran on an in-memory substitute. Checking compose, the
        // manifest and the application config while leaving this unchecked catches the symptom and leaves
        // the mechanism intact - which is why it is read here rather than only stated in the contract.
        String tested = datastoreInDatasourceUrl(testProps);
        if (tested != null && !tested.equals(shipped)) {
            problems.add("the test suite runs against **" + tested + "** (`" + TEST_PROPERTIES_PATH
                    + "`) while `" + COMPOSE_FILE_PATH + "` ships **" + shipped + "**. Every migration and "
                    + "every entity mapping is therefore verified against an engine that is never "
                    + "delivered, so an incompatibility passes every gate and only appears at runtime - "
                    + "measured twice on this factory, as `CREATE ALIAS` and as "
                    + "`bytea (Types#VARBINARY)`.");
        }
        if (pom != null && !buildManifestHasDriverFor(pom, shipped)) {
            problems.add("the build manifest declares no driver for **" + shipped + "**, which is the engine "
                    + "`" + COMPOSE_FILE_PATH + "` provides.");
        }
        if (problems.isEmpty()) {
            return;
        }

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.datastore_artifacts_disagree);
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setLeanValue(LeanValue.essential);
        // The assembly is the work here, not any one component - the same reasoning that routes
        // product_not_launchable to this role. A datastore is a property of how the parts fit together.
        wishlist.setSourceRoleTag("BARCAN-TAG-00");
        wishlist.setCynefinDomain("clear");
        wishlist.setContent("This product's runtime artifacts disagree about which datastore it runs "
                + "against, which is decidable from the repository without launching anything:\n\n- "
                + String.join("\n- ", problems)
                + "\n\nDo not resolve this by picking a stack in one file. The runtime contract "
                + "(docs/architecture/adr-002-runtime-contract.md) is the single source of truth for which "
                + "services this product runs against; the compose file, the build manifest and the "
                + "application configuration must all be derivable from it. Where the contract does not yet "
                + "name the datastore, extending the contract is part of this work.");
        wishlist.setJtbd("When my product is tested and reviewed, I want it exercised against the same "
                + "datastore it is delivered with, so a passing test means the delivered product works "
                + "rather than that a different configuration works");
        wishlist.setAcceptanceCriteria("Given the repository at its default branch, When the runtime "
                + "contract, the compose file, the build manifest and the application configuration are "
                + "read, Then they all name the same datastore, and the test suite runs against that "
                + "datastore rather than an in-memory substitute");
        wishlist.setDod("BARCAN-TAG-00: the runtime contract names the datastore, and compose, the build "
                + "manifest and the application configuration are consistent with it. The test suite runs "
                + "against the shipped engine - an in-memory substitute is what let this defect through.");
        wishlistRepository.save(wishlist);
        log.warn("ProductLaunchabilityService: project {} - runtime artifacts disagree about the datastore "
                + "({}); created datastore_artifacts_disagree wishlist", project.getId(), problems);
    }

    /** What the runtime contract declares. Null when there is no contract or no line to read. */
    private String datastoreDeclaredByContract(String contract) {
        if (contract == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*datastore:\\s*([A-Za-z0-9_.:-]+)")
                .matcher(contract);
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1);
        if (UNDECLARED.equalsIgnoreCase(raw)) {
            return UNDECLARED;
        }
        // `postgresql:15` -> `postgresql`; the version is the contract's business, not this check's.
        return normaliseEngine(raw.split(":")[0]);
    }

    /** The engine a compose stack actually provides. Null when it provides none this recognises. */
    private String datastoreProvidedByCompose(String compose) {
        // An explicit SPRING_DATASOURCE_URL override in compose is the most direct statement of what the
        // application will really connect to, so it wins over guessing from image names.
        java.util.regex.Matcher url = java.util.regex.Pattern
                .compile("SPRING_DATASOURCE_URL\\s*[=:]\\s*jdbc:([a-zA-Z0-9]+):")
                .matcher(compose);
        if (url.find()) {
            return normaliseEngine(url.group(1));
        }
        String lower = compose.toLowerCase(java.util.Locale.ROOT);
        for (String image : new String[]{"postgres", "mariadb", "mysql", "mongo"}) {
            if (lower.contains("image:") && lower.contains(image)) {
                return normaliseEngine(image);
            }
        }
        return null;
    }

    /** The engine the application defaults to. Null when the file is absent or names none. */
    private String datastoreInDatasourceUrl(String applicationProperties) {
        if (applicationProperties == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("spring\\.datasource\\.url\\s*[=:]\\s*jdbc:([a-zA-Z0-9]+):")
                .matcher(applicationProperties);
        return m.find() ? normaliseEngine(m.group(1)) : null;
    }

    private boolean buildManifestHasDriverFor(String pom, String engine) {
        String lower = pom.toLowerCase(java.util.Locale.ROOT);
        return switch (engine) {
            case "postgresql" -> lower.contains("org.postgresql");
            case "mysql" -> lower.contains("mysql-connector");
            case "mariadb" -> lower.contains("mariadb-java-client");
            case "mongodb" -> lower.contains("mongodb-driver") || lower.contains("spring-boot-starter-data-mongodb");
            // An engine whose driver coordinates this does not know: say nothing rather than guess.
            default -> true;
        };
    }

    private String normaliseEngine(String raw) {
        String e = raw.toLowerCase(java.util.Locale.ROOT);
        if (e.startsWith("postgres")) {
            return "postgresql";
        }
        if (e.startsWith("mariadb")) {
            return "mariadb";
        }
        if (e.startsWith("mysql")) {
            return "mysql";
        }
        if (e.startsWith("mongo")) {
            return "mongodb";
        }
        return e;
    }

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
