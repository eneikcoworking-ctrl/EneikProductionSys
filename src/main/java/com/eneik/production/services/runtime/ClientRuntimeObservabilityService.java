package com.eneik.production.services.runtime;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.services.design.DesignDriftMonitorService;
import com.eneik.production.services.settings.SystemSettingsService;
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

    @Value("${client-runtime-observability.base-delay-hours:24}")
    private long baseDelayHours;

    @Value("${client-runtime-observability.minimum-delay-hours:1}")
    private long minimumDelayHours;

    @Value("${client-runtime-observability.health-check-path:/actuator/health}")
    private String healthCheckPath;

    @Value("${client-runtime-observability.health-check-port:8090}")
    private int healthCheckPort;

    public ClientRuntimeObservabilityService(ClientRuntimeObservationRepository observationRepository,
                                              RuntimeLauncherClient launcherClient,
                                              SystemSettingsService settingsService,
                                              KaizenService kaizenService,
                                              DesignDriftMonitorService designDriftMonitorService) {
        this.observationRepository = observationRepository;
        this.launcherClient = launcherClient;
        this.settingsService = settingsService;
        this.kaizenService = kaizenService;
        this.designDriftMonitorService = designDriftMonitorService;
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

        List<ClientRuntimeObservationEntity> history = observationRepository.findByProjectIdOrderByObservedAtDesc(project.getId());
        BetaPosterior posterior = posteriorFrom(history);

        if (!history.isEmpty()) {
            Instant lastObservedAt = history.get(0).getObservedAt();
            Duration sinceLast = Duration.between(lastObservedAt, Instant.now());
            Duration nextDelay = posterior.nextCheckDelay(Duration.ofHours(baseDelayHours), Duration.ofHours(minimumDelayHours));
            if (sinceLast.compareTo(nextDelay) < 0) {
                return; // not due yet, per the adaptive-cadence formula - no hard-coded schedule anywhere here
            }
        }

        observeOnce(project);
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
        observation.setLaunchSuccess(launch.success());
        observation.setLaunchDurationMs(launch.durationMs());
        observation.setErrorText(launch.error());

        if (launch.success()) {
            String healthUrl = "http://localhost:" + healthCheckPort + healthCheckPath;
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
                String rootUrl = "http://localhost:" + healthCheckPort + "/";
                try {
                    designDriftMonitorService.checkLiveInstance(project, rootUrl);
                } catch (Exception e) {
                    log.warn("ClientRuntimeObservabilityService: design drift check failed for project {}: {}",
                            project.getId(), e.getMessage(), e);
                }
            }
        }

        launcherClient.teardown();
        observationRepository.save(observation);
        log.info("ClientRuntimeObservabilityService: project {} observed - launchSuccess={} healthStatus={}",
                project.getId(), observation.isLaunchSuccess(), observation.getHealthStatusCode());

        checkForRealShift(project);
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
                .sorted((a, b) -> a.getObservedAt().compareTo(b.getObservedAt()))
                .map(this::isHealthy)
                .collect(Collectors.toList());

        RuntimeHealthShiftDetector.ShiftVerdict verdict = RuntimeHealthShiftDetector.detect(chronological);
        if (!verdict.hasEnoughData() || !verdict.shiftDetected()) {
            return;
        }

        String title = "Real runtime health shift detected for " + (project.getName() != null ? project.getName() : project.getId());
        String description = String.format(
                "RuntimeHealthShiftDetector: %d of the last %d observations failed (launch+health-check), "
                        + "against a historical baseline failure rate of %.1f%%. Two-sided exact binomial p-value = %.6f "
                        + "(threshold %.3f) - not noise, a real statistical shift.",
                verdict.recentFailures(), verdict.recentWindowSize(), verdict.baselineFailureRate() * 100,
                verdict.pValue(), RuntimeHealthShiftDetector.DEFAULT_SIGNIFICANCE_THRESHOLD);

        kaizenService.recordProductRuntimeDefectProposal(project.getId(), project.getName(), title, description);
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
                                        List<ClientRuntimeObservationEntity> recentObservations) {}

    public RuntimeHealthSummary summarize(java.util.UUID projectId) {
        List<ClientRuntimeObservationEntity> history = observationRepository.findByProjectIdOrderByObservedAtDesc(projectId);
        BetaPosterior posterior = posteriorFrom(history);
        Boolean lastHealthy = history.isEmpty() ? null : isHealthy(history.get(0));
        Instant lastAt = history.isEmpty() ? null : history.get(0).getObservedAt();
        return new RuntimeHealthSummary(history.size(), posterior.mean(), posterior.credibleIntervalWidth(),
                lastHealthy, lastAt, history);
    }

    private BetaPosterior posteriorFrom(List<ClientRuntimeObservationEntity> history) {
        BetaPosterior posterior = BetaPosterior.UNINFORMATIVE_PRIOR;
        // Oldest-first replay so the posterior reflects the real chronological update sequence.
        for (int i = history.size() - 1; i >= 0; i--) {
            posterior = posterior.update(isHealthy(history.get(i)));
        }
        return posterior;
    }
}
