package com.eneik.production.services.runtime;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.design.DesignDriftMonitorService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.toc.LaunchabilityConstraintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 1 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md: decides, on the existing
 * ContinuousOrchestrationService tick (never a new @Scheduled cron - see BetaPosterior's own javadoc
 * for why), whether it is time to spend one real ephemeral launch+health-check cycle on the active
 * project's own delivered product.
 *
 * Deliberately NOT registered as a LeverPromotionService lever: that framework compares a CANDIDATE
 * decision against an INCUMBENT one, judged by real ground truth. Nothing existed before this to be an
 * incumbent, and this service produces raw observations, not a decision to compare against anything -
 * forcing it through that shape would mean inventing a synthetic incumbent with no real referent, the
 * same "vocabulary without substance" the operator already flagged once tonight. Phase 2/3's control-
 * chart verdict (does genuinely have a real incumbent - the current naive "delivered=ok" dashboard
 * status) is where lever registration will honestly apply.
 */
@Service
public class ClientRuntimeObservabilityService {
    private static final Logger log = LoggerFactory.getLogger(ClientRuntimeObservabilityService.class);

    private final ClientRuntimeObservationRepository observationRepository;
    private final RuntimeLauncherClient launcherClient;
    private final SystemSettingsService settingsService;
    private final KaizenService kaizenService;
    private final DesignDriftMonitorService designDriftMonitorService;
    private final ProjectRepository projectRepository;
    private final com.eneik.production.repositories.TaskRepository taskRepository;
    private final ProductCapabilityService productCapabilityService;
    /** Identification of the constraint belongs where its evidence is produced - see plan L-9. */
    private final LaunchabilityConstraintService launchabilityConstraintService;

    @Value("${client-runtime-observability.base-delay-hours:24}")
    private long baseDelayHours;

    @Value("${client-runtime-observability.minimum-delay-hours:1}")
    private long minimumDelayHours;

    /** Consecutive unanswered launcher calls that turn a blip into a reportable factory defect (O-10). */
    @Value("${client-runtime-observability.instrument-outage-threshold:3}")
    private int instrumentOutageThreshold;

    // 2026-08-11 (live incident, test-forty-third): the old defaults (/actuator/health on 8090) never
    // matched a single real generated project - no client project depends on spring-boot-actuator, and
    // none exposes a separate management port. The client's own Dockerfile HEALTHCHECK directive already
    // declares the real, working convention (plain /health on the app's own port) - use that as the
    // default instead of a guess that has a 100% historical failure rate. Still fully operator-
    // configurable per deployment via these same properties.
    @Value("${client-runtime-observability.health-check-path:/health}")
    private String healthCheckPath;

    // Fallback only, used when runtime-launcher's /launch didn't report a remapped external port (e.g.
    // the compose file declared no published ports at all) - see RuntimeLauncherClient.LaunchResult.
    @Value("${client-runtime-observability.health-check-port:8090}")
    private int healthCheckPort;

    // 2026-08-11 (bounded live-preview window): a successful launch used to be torn down immediately
    // after its health check, so nothing was ever actually reachable by the time a human (dashboard link)
    // or a philosophical audit's live-fetch went looking for it. Leave it running for this long instead;
    // the next tick's reaper (see reapIdlePreviewIfExpired) tears it down once expired - never a new cron.
    @Value("${client-runtime-observability.live-preview-idle-minutes:15}")
    private long livePreviewIdleMinutes;

    public ClientRuntimeObservabilityService(ClientRuntimeObservationRepository observationRepository,
                                              RuntimeLauncherClient launcherClient,
                                              SystemSettingsService settingsService,
                                              KaizenService kaizenService,
                                              DesignDriftMonitorService designDriftMonitorService,
                                              ProjectRepository projectRepository,
                                              com.eneik.production.repositories.TaskRepository taskRepository,
                                              ProductCapabilityService productCapabilityService,
                                              LaunchabilityConstraintService launchabilityConstraintService) {
        this.observationRepository = observationRepository;
        this.launcherClient = launcherClient;
        this.settingsService = settingsService;
        this.kaizenService = kaizenService;
        this.designDriftMonitorService = designDriftMonitorService;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.productCapabilityService = productCapabilityService;
        this.launchabilityConstraintService = launchabilityConstraintService;
    }

    @Transactional
    public void maybeObserve(ProjectEntity project) {
        if (!settingsService.effectiveBoolean("client_runtime_observability_enabled")) {
            return;
        }
        // Phase 0 not yet satisfied (no compose file, or not yet even checked) - nothing to observe.
        if (project.getLaunchabilityCheckedAt() == null) {
            return;
        }

        // Runs every tick regardless of whether a new observation is due below - this IS the reaper for
        // the bounded live-preview window, piggybacked on the same existing per-project tick rather than
        // a dedicated cron.
        reapIdlePreviewIfExpired(project);

        List<ClientRuntimeObservationEntity> history = observationRepository.findByProjectIdOrderByObservedAtDesc(project.getId());
        BetaPosterior posterior = posteriorFrom(history);

        java.util.Optional<ClientRuntimeObservationEntity> lastReal = lastRealObservation(history);

        // 2026-08-21 (ACP-103, plan section 9.3): the clock below runs on the newest row of ANY kind, not
        // on the newest row that observed the product. What this gate governs is the rate of ATTEMPTS, and
        // an unanswered launcher call is an attempt - it cost a real POST. Clocking a rate limiter on
        // successful measurements means it stops limiting exactly when measurement stops working:
        // `lastRealObservation` freezes, `sinceLast` grows without bound, and the condition is true on
        // every tick.
        //
        // Measured on test-forty-ninth, 2026-08-20: the launcher became unreachable at 11:40 and the
        // observation rate went from 1/hour to 28/hour - 45 calls into nothing, each correctly recorded,
        // correctly excluded from the posterior, and therefore invisible in every number anyone looks at.
        // Positive feedback aimed at the component least able to serve it.
        //
        // V104 is untouched and remains right: instrument rows stay out of the PRODUCT's accounting. This
        // is the one consumer of that classification whose question is about the instrument.
        if (!history.isEmpty()) {
            Instant lastAttemptAt = history.get(0).getObservedAt();
            Duration sinceAttempt = Duration.between(lastAttemptAt, Instant.now());
            Duration floor = Duration.ofHours(minimumDelayHours);
            Duration nextDelay = posterior.nextCheckDelay(Duration.ofHours(baseDelayHours), floor);
            // "Did the product change" is a product question, so it still measures from the last time the
            // product was actually looked at; the floor is applied against the attempt clock so a merge
            // stream can pull the check forward but never past the rate limit.
            boolean changed = lastReal.isPresent()
                    && productChangedSince(project, lastReal.get().getObservedAt(), sinceAttempt, floor);
            if (sinceAttempt.compareTo(nextDelay) < 0 && !changed) {
                return; // not due yet, per the adaptive-cadence formula - no hard-coded schedule anywhere here
            }
        }

        observeOnce(project);
    }

    /** Tears down a lingering live-preview instance once its bounded window has expired - see the
     * livePreviewIdleMinutes javadoc above. A no-op when nothing is currently tracked as live. */
    private void reapIdlePreviewIfExpired(ProjectEntity project) {
        Instant launchedAt = project.getLastRuntimePreviewLaunchedAt();
        if (launchedAt == null) {
            return;
        }
        if (Duration.between(launchedAt, Instant.now()).compareTo(Duration.ofMinutes(livePreviewIdleMinutes)) < 0) {
            return; // still within the window - leave it running for the dashboard link / a live-fetch
        }
        launcherClient.teardown();
        project.setLastRuntimePreviewLaunchedAt(null);
        project.setLastRuntimePreviewPort(null);
        projectRepository.save(project);
        log.info("ClientRuntimeObservabilityService: project {} live-preview window expired, torn down", project.getId());
    }

    private void observeOnce(ProjectEntity project) {
        String repoUrl = project.getRepositoryUrl();
        if (repoUrl == null || repoUrl.isBlank()) {
            log.warn("ClientRuntimeObservabilityService: project {} has no repository URL, skipping", project.getId());
            return;
        }
        String projectSlug = project.getSlug() != null ? project.getSlug() : project.getId().toString();

        RuntimeLauncherClient.LaunchResult launch = launcherClient.launch(repoUrl, project.getDefaultBranch(), projectSlug);

        ClientRuntimeObservationEntity observation = new ClientRuntimeObservationEntity();
        observation.setProjectId(project.getId());
        // 2026-08-19: what this row is a fact ABOUT. An unanswered launch call tells us nothing about the
        // product - it is a missing observation, not a negative one (V104, ACP-102).
        observation.setInstrumentFailure(!launch.observed());
        // Which artifact this row is an observation of (V109). Null when the launcher did not reach one,
        // or when it predates the field - never inferred from anything else.
        observation.setCommitSha(launch.commitSha());
        observation.setLaunchSuccess(launch.success());
        observation.setLaunchDurationMs(launch.durationMs());
        observation.setErrorText(launch.error());

        if (launch.success()) {
            int port = launch.externalPort() != null ? launch.externalPort() : healthCheckPort;
            String healthUrl = "http://localhost:" + port + healthCheckPath;
            RuntimeLauncherClient.HealthCheckResult health = launcherClient.healthcheck(healthUrl);
            observation.setHealthStatusCode(health.statusCode());
            observation.setHealthLatencyMs(health.latencyMs());
            if (health.error() != null) {
                observation.setErrorText(health.error());
            }

            // Design shop Stage 4 (additive, DesignDriftMonitorService): piggyback on this same
            // still-open live-instance window rather than opening a second one - only while the health
            // check itself looks genuinely alive, same "2xx" bar isHealthy() uses below.
            if (health.statusCode() != null && health.statusCode() >= 200 && health.statusCode() < 300) {
                String rootUrl = "http://localhost:" + port + "/";
                try {
                    designDriftMonitorService.checkLiveInstance(project, rootUrl);
                } catch (Exception e) {
                    log.warn("ClientRuntimeObservabilityService: design drift check failed for project {}: {}",
                            project.getId(), e.getMessage(), e);
                }
            }

            // 2026-08-20: while the instance is up, ask it about every capability the product DECLARES,
            // not only whether it booted. `docker compose up` is the expensive part and it is already paid;
            // each probe is one local HTTP call. This is the product-layer opportunity Six Sigma never had -
            // an observation of a declared capability is an opportunity, a capability that did not work is a
            // defect a user could experience, unlike a quality-gate check or a PR conflict.
            //
            // Probed whenever the launch succeeded, not only when /health passed: a product that serves its
            // routes while missing a health endpoint is a real case, and refusing to look would make the
            // measure depend on a convention rather than on the product.
            try {
                productCapabilityService.probeAll(project, "http://localhost:" + port);
            } catch (Exception e) {
                log.warn("ClientRuntimeObservabilityService: capability probing failed for project {}: {}",
                        project.getId(), e.getMessage());
            }

            // Bounded live-preview window (2026-08-11): leave it running instead of tearing down right
            // away, so the dashboard link and a philosophical audit's live-fetch have something real to
            // reach. reapIdlePreviewIfExpired (this same tick, next time) tears it down once the window
            // expires - never left running indefinitely.
            project.setLastRuntimePreviewLaunchedAt(Instant.now());
            project.setLastRuntimePreviewPort(port);
            projectRepository.save(project);
        } else {
            // Never leak a partial stack from a failed `docker compose up --build`.
            launcherClient.teardown();
        }

        observationRepository.save(observation);
        log.info("ClientRuntimeObservabilityService: project {} observed - launchSuccess={} healthStatus={} instrumentFailure={}",
                project.getId(), observation.isLaunchSuccess(), observation.getHealthStatusCode(),
                observation.isInstrumentFailure());

        // 2026-08-21 (plan L-9): the launchability constraint is identified HERE, at the moment the
        // evidence exists, and no longer only inside the philosophical cycle's five gates on a two-day
        // cron whose one accelerator is switched off. §7's rule is that a constraint is cleared by a fresh
        // healthy observation, not by a status - so the observation is exactly where it should be asked.
        //
        // Measured 2026-08-21: every filing that day happened because a human triggered the cycle by hand,
        // while the factory sat with an empty queue and the product answering nothing. An idle factory
        // with an unrefuted product is not "everything is done"; by §1 it means the system stopped looking.
        //
        // Re-filing is bounded inside the service (an attempt in flight blocks a second; a finished one
        // waits out a cooldown), so an hourly cadence cannot turn this into a compile loop.
        if (!observation.isInstrumentFailure() && !isHealthy(observation)) {
            try {
                launchabilityConstraintService.ensureOpen(project, observation.getErrorText());
            } catch (Exception e) {
                // Never let identification break the observation that produced it.
                log.warn("ClientRuntimeObservabilityService: could not ensure the launchability constraint for "
                        + "project {}: {}", project.getId(), e.getMessage());
            }
        }

        if (observation.isInstrumentFailure()) {
            // Nothing was learned about the product, so the product's shift test has nothing to re-run on -
            // it would only re-read the whole history to filter this row back out. What DID happen is an
            // attempt that reached nothing, and that belongs to the instrument's accounting.
            reportInstrumentOutage(project);
            return;
        }

        checkForRealShift(project);
    }

    /**
     * The instrument's own denominator (plan O-10, ACP-103).
     *
     * Invariant 8 applies to the launcher as much as to delivery: a bearer nothing counts cannot be
     * refuted. Measured 2026-08-20 on test-forty-ninth - 46 consecutive unanswered launcher calls produced
     * zero findings, zero lever observations and zero invariant transitions, because every one of them was
     * correctly classified as "not about the product" and then belonged to no other accounting at all.
     *
     * A run, not a rate: what makes this a defect rather than noise is consecutiveness. One unreachable
     * call is a blip on a shared host; {@code instrumentOutageThreshold} in a row is a component that is
     * down. Keyed to the launcher so the Kaizen read path (category + targetComponent) holds it as one
     * standing finding instead of one per failed call.
     */
    private void reportInstrumentOutage(ProjectEntity project) {
        int threshold = Math.max(2, instrumentOutageThreshold);
        List<ClientRuntimeObservationEntity> head = observationRepository
                .findByProjectIdOrderByObservedAtDesc(project.getId(), org.springframework.data.domain.Limit.of(threshold));
        if (head.size() < threshold) {
            return;
        }
        for (ClientRuntimeObservationEntity row : head) {
            if (!row.isInstrumentFailure()) {
                return;
            }
        }
        log.warn("ClientRuntimeObservabilityService: the runtime launcher has failed to answer {} consecutive "
                + "times for project {}; reporting it as a factory defect", threshold, project.getId());
        kaizenService.recordSystemicDefectProposal(
                null,
                "Global",
                "runtime-launcher",
                "Runtime launcher unreachable for " + threshold + " consecutive observation attempts",
                "Every recent launch attempt returned without reaching the launcher, so the product has not "
                        + "been observed at all - the posterior is unchanged and the dashboard is clean while "
                        + "nothing is being measured. Check that the runtime-launcher container is running and "
                        + "answering on its own port before reading any runtime number as a fact about the product.");
    }

    /**
     * Phase 2+3: after every new real observation, ask whether the product's actual health has
     * genuinely shifted (RuntimeHealthShiftDetector - a real statistical test, not a guess), and if so,
     * surface it as a reviewable Kaizen proposal (KaizenService.recordProductRuntimeDefectProposal) with
     * the real numbers cited, not a vague "something might be wrong."
     */
    private void checkForRealShift(ProjectEntity project) {
        List<ClientRuntimeObservationEntity> fullHistory = observationRepository.findByProjectIdOrderByObservedAtDesc(project.getId());
        List<Boolean> chronological = fullHistory.stream()
                .filter(row -> !row.isInstrumentFailure())
                .sorted((a, b) -> a.getObservedAt().compareTo(b.getObservedAt()))
                .map(this::isHealthy)
                .collect(Collectors.toList());

        RuntimeHealthShiftDetector.ShiftVerdict shiftVerdict = RuntimeHealthShiftDetector.detect(chronological);
        if (shiftVerdict.hasEnoughData() && shiftVerdict.shiftDetected()) {
            String title = "Real runtime health shift detected for " + (project.getName() != null ? project.getName() : project.getId());
            String description = String.format(
                    "RuntimeHealthShiftDetector: %d of the last %d observations failed (launch+health-check), "
                            + "against a historical baseline failure rate of %.1f%%. Two-sided exact binomial p-value = %.6f "
                            + "(threshold %.3f) - not noise, a real statistical shift.",
                    shiftVerdict.recentFailures(), shiftVerdict.recentWindowSize(), shiftVerdict.baselineFailureRate() * 100,
                    shiftVerdict.pValue(), RuntimeHealthShiftDetector.DEFAULT_SIGNIFICANCE_THRESHOLD);
            kaizenService.recordProductRuntimeDefectProposal(project.getId(), project.getName(), title, description);
            return;
        }

        // 2026-08-11 (live incident, test-forty-third): complementary ABSOLUTE test - catches a project
        // that was never in statistical control from its very first observation, which the relative
        // shift test above is structurally blind to (it needs minimumBaselineSamples of "before" history
        // to compare against; a project with 3/3 failures from observation #1 has no "before" to shift
        // away from). Same exact-binomial math family, applied against a pre-registered expected rate.
        RuntimeHealthShiftDetector.AbsoluteVerdict absoluteVerdict = RuntimeHealthShiftDetector.detectBelowExpectedRate(chronological);
        if (absoluteVerdict.shiftDetected()) {
            String title = "Runtime health never reached the expected rate for " + (project.getName() != null ? project.getName() : project.getId());
            String description = String.format(
                    "RuntimeHealthShiftDetector (absolute): only %d of %d observations succeeded (launch+health-check), "
                            + "against an expected minimum success rate of %.0f%%. One-sided exact binomial p-value = %.6f "
                            + "(threshold %.3f) - this project has never demonstrated it can reliably launch, not a drift "
                            + "from a working baseline.",
                    absoluteVerdict.successes(), absoluteVerdict.total(), absoluteVerdict.expectedSuccessRate() * 100,
                    absoluteVerdict.pValue(), RuntimeHealthShiftDetector.DEFAULT_ABSOLUTE_SIGNIFICANCE_THRESHOLD);
            kaizenService.recordProductRuntimeDefectProposal(project.getId(), project.getName(), title, description);
        }
    }

    private boolean isHealthy(ClientRuntimeObservationEntity observation) {
        if (!observation.isLaunchSuccess()) {
            return false;
        }
        Integer status = observation.getHealthStatusCode();
        return status != null && status >= 200 && status < 300;
    }

    /**
     * Read-only summary for the frontend (Кузница/Product room + Роща canopy glow) - a pure projection
     * of already-computed evidence, reuses the exact same posterior/health math as the real observation
     * cycle above so the two can never silently disagree.
     */
    public record RuntimeHealthSummary(int observationCount, double posteriorMean, double credibleIntervalWidth,
                                        Boolean lastObservationHealthy, Instant lastObservedAt,
                                        List<ClientRuntimeObservationEntity> recentAttempts,
                                        String liveUrl) {

        /**
         * The rows that actually observed the product, newest first.
         *
         * 2026-08-21: this component used to be called `recentObservations` and it was not one. It carries
         * every ATTEMPT, and an attempt that never reached the launcher observed nothing. The name said
         * otherwise, and three separate consumers read `get(0)` from it believing they held a fact about
         * the product: this file's own cadence clock, FalsificationCycleService.latestErrorText, and
         * DeliveryRealityProducerService.produceRuntimeObservationEvidence - which wrote it into the
         * coherence graph as "expected launchable, actually failed". Measured live: the constraint filed
         * at 23:25Z cited "runtime-launcher unreachable" as the thing to fix in the CLIENT's repository.
         *
         * The names are the fix, not a rule to remember. A caller asking what the product did calls a
         * method whose name says "product" and physically cannot receive an instrument row; a caller
         * asking how often we tried calls one whose name says "attempts". ACP-102: a designator must pick
         * out what it denotes. This is why the rename is not cosmetic - `recentObservations` was a name
         * with a bearer it did not have.
         */
        public List<ClientRuntimeObservationEntity> productObservations() {
            return recentAttempts == null ? List.of()
                    : recentAttempts.stream().filter(row -> !row.isInstrumentFailure()).toList();
        }

        /** The newest row that observed the product, empty when nothing has yet reached it. */
        public java.util.Optional<ClientRuntimeObservationEntity> lastProductObservation() {
            return productObservations().stream().findFirst();
        }
    }

    public RuntimeHealthSummary summarize(java.util.UUID projectId) {
        List<ClientRuntimeObservationEntity> history = observationRepository.findByProjectIdOrderByObservedAtDesc(projectId);
        BetaPosterior posterior = posteriorFrom(history);
        // Same rule as posteriorFrom: "how is the product" is answered by the last time the product was
        // really looked at. A row where the launcher never answered says nothing about it, and reporting it
        // as the product's last known health is how an instrument fault became a product verdict - the
        // finding DeliveryRealityProducerService then turns into evidence.
        java.util.Optional<ClientRuntimeObservationEntity> lastReal = lastRealObservation(history);
        Boolean lastHealthy = lastReal.map(this::isHealthy).orElse(null);
        Instant lastAt = lastReal.map(ClientRuntimeObservationEntity::getObservedAt).orElse(null);
        String liveUrl = currentLiveUrl(projectId).orElse(null);
        return new RuntimeHealthSummary(history.size(), posterior.mean(), posterior.credibleIntervalWidth(),
                lastHealthy, lastAt, history, liveUrl);
    }

    /** Empty unless a live-preview instance was launched within the last livePreviewIdleMinutes - a link
     * (or a philosophical audit's live-fetch) pointing at an already-expired window would just fail.
     * Public so FalsificationCycleService can splice real live evidence into a philosophical audit
     * without re-deriving this same window logic. */
    public java.util.Optional<String> currentLiveUrl(java.util.UUID projectId) {
        return projectRepository.findById(projectId).map(this::liveUrlIfWithinWindow);
    }

    private String liveUrlIfWithinWindow(ProjectEntity project) {
        Instant launchedAt = project.getLastRuntimePreviewLaunchedAt();
        Integer port = project.getLastRuntimePreviewPort();
        if (launchedAt == null || port == null) {
            return null;
        }
        if (Duration.between(launchedAt, Instant.now()).compareTo(Duration.ofMinutes(livePreviewIdleMinutes)) >= 0) {
            return null;
        }
        return "http://localhost:" + port + "/";
    }

    /**
     * 2026-08-19: rows where the launcher never answered are skipped entirely. Counting them was feedback
     * with the wrong sign - each instrument fault narrowed the posterior, which lengthened the delay to the
     * next attempt, so the more often the instrument failed the less often the product was tried. Measured:
     * the 16:42Z launcher timeout took this project from Beta(1,3) to Beta(1,4) and the next check from 7.2
     * to 9.7 hours. A launch the launcher itself reports as failed is fully observed and still counts.
     */
    private BetaPosterior posteriorFrom(List<ClientRuntimeObservationEntity> history) {
        BetaPosterior posterior = BetaPosterior.UNINFORMATIVE_PRIOR;
        // Oldest-first replay so the posterior reflects the real chronological update sequence.
        for (ClientRuntimeObservationEntity row : oneDrawPerArtifact(history)) {
            posterior = posterior.update(isHealthy(row));
        }
        return posterior;
    }

    /**
     * The sequence the posterior is entitled to fold over (V109).
     *
     * BetaPosterior is a conjugate Beta-Bernoulli model and de Finetti is what licenses it: the sequence
     * must be exchangeable, draws from one fixed unknown probability. Between merges that is false. The
     * outcome is determined by the artifact on main, which is constant until something merges, so inside
     * such a run the next reading equals the last one with probability 1 - and folding it in again is
     * counting the same fact twice, not gathering evidence.
     *
     * Measured on test-forty-ninth: seven hourly readings of one unchanged commit between 05h and 11h on
     * 2026-08-20, each pushing the credible interval's lower bound down, which - since the cadence is
     * keyed on that bound - brought the next check sooner, which produced another identical reading.
     *
     * Two exclusions, both deliberate:
     * - instrument failures, which are not observations of the product at all (V104);
     * - nothing else. A row whose commit is unknown is never collapsed into its neighbour, because
     *   claiming two rows saw the same artifact when neither recorded which artifact it saw is exactly
     *   the substitution this method exists to prevent (ACP-102).
     *
     * The last reading of a run is kept rather than the first: it is what the factory most recently knows
     * about that artifact.
     */
    private List<ClientRuntimeObservationEntity> oneDrawPerArtifact(List<ClientRuntimeObservationEntity> history) {
        List<ClientRuntimeObservationEntity> draws = new java.util.ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            ClientRuntimeObservationEntity row = history.get(i);
            if (row.isInstrumentFailure()) {
                continue;
            }
            if (!draws.isEmpty()) {
                String previous = draws.get(draws.size() - 1).getCommitSha();
                if (previous != null && previous.equals(row.getCommitSha())) {
                    draws.set(draws.size() - 1, row); // same artifact, still one draw - keep the newest
                    continue;
                }
            }
            draws.add(row);
        }
        return draws;
    }

    /** The most recent row that is actually an observation of the product - see {@link #posteriorFrom}.
     * The cadence clock must run from when the product was last really looked at, not from when the
     * instrument last failed to look. */
    private static java.util.Optional<ClientRuntimeObservationEntity> lastRealObservation(
            List<ClientRuntimeObservationEntity> history) {
        return history.stream().filter(row -> !row.isInstrumentFailure()).findFirst();
    }

    /**
     * 2026-08-20: the posterior is a belief about a specific object, and a merge to main replaces that
     * object. Until now the only thing that could make an observation due was elapsed time, so a fix that
     * had already landed stayed invisible for up to a full delay - measured on test-forty-ninth, where a
     * merged MinIO fix waited while the cadence pointed at 09:11Z the next morning.
     *
     * No new timestamp is invented: a task that is now `done` and was written after the last real
     * observation means the product on main is not the product that observation was about.
     * {@code TaskEntity.updatedAt} already records that.
     *
     * **The floor still binds.** An event may pull the check forward to the minimum delay, never below it.
     * That is deliberate: without the floor a busy merge stream would launch the product on every tick,
     * which is the same overproduction the adaptive cadence exists to prevent - the trigger changes *when*
     * we look, never *how often we are allowed to*.
     */
    private boolean productChangedSince(ProjectEntity project, Instant lastObservedAt,
                                        Duration sinceLast, Duration floor) {
        if (sinceLast.compareTo(floor) < 0) {
            return false;
        }
        try {
            long mergedSince = taskRepository.countByProjectIdAndStatusAndUpdatedAtAfter(
                    project.getId(),
                    com.eneik.production.models.persistence.TaskStatus.done,
                    lastObservedAt);
            if (mergedSince > 0) {
                log.info("ClientRuntimeObservabilityService: project {} - {} task(s) reached done since the "
                                + "last observation at {}; observing now rather than waiting out the timer",
                        project.getId(), mergedSince, lastObservedAt);
                return true;
            }
        } catch (Exception e) {
            // A failure to answer "did anything change" must never suppress the ordinary timed cadence.
            log.warn("ClientRuntimeObservabilityService: change check failed for project {}: {}",
                    project.getId(), e.getMessage());
        }
        return false;
    }

}
