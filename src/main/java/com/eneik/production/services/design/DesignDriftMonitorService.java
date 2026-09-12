package com.eneik.production.services.design;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.runtime.RuntimeLauncherClient;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Design shop Stage 4: regular design-drift monitoring against the REAL running product, not static
 * GitHub source. Piggybacks on ClientRuntimeObservabilityService's own live-launched-instance window
 * (between RuntimeLauncherClient.launch() and teardown()) rather than opening a second one - called
 * from a new, additive hook there, never re-implementing the launch/health/teardown cycle itself.
 *
 * Reuses DesignConsistencyAuditService's E(f) predicate (already live for Stitch generation) against
 * the real served HTML/CSS instead of a generated mockup - "is it still on-brand alive", not just
 * "is it alive".
 *
 * Confirmed live 2026-08-10 (test-forty-third): there is no established per-project canonical palette
 * anywhere in this codebase to audit against - the factory's own "Verdant Flow" tokens were wrongly used
 * here (and in DesignShopOrchestrationService, since fixed) as a stand-in, which false-flagged a
 * perfectly on-brand client screen as 100% off-token. Until a real per-project design-system baseline
 * exists (e.g. captured once from a project's first Stitch generation), this only confirms the live page
 * is genuinely reachable and does not attempt the token comparison at all - a wrong comparison is worse
 * than no comparison, see the E(f) predicate's own point.
 */
@Service
public class DesignDriftMonitorService {
    private static final Logger log = LoggerFactory.getLogger(DesignDriftMonitorService.class);

    private final RuntimeLauncherClient launcherClient;
    private final DesignConsistencyAuditService auditService;
    private final SystemSettingsService settingsService;
    private final KaizenService kaizenService;
    private final com.eneik.production.repositories.DesignShopCycleRepository designShopCycleRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public DesignDriftMonitorService(RuntimeLauncherClient launcherClient,
                                      DesignConsistencyAuditService auditService,
                                      SystemSettingsService settingsService,
                                      KaizenService kaizenService,
                                      com.eneik.production.repositories.DesignShopCycleRepository designShopCycleRepository) {
        this.launcherClient = launcherClient;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.kaizenService = kaizenService;
        this.designShopCycleRepository = designShopCycleRepository;
    }

    public DesignDriftMonitorService(RuntimeLauncherClient launcherClient,
                                      DesignConsistencyAuditService auditService,
                                      SystemSettingsService settingsService,
                                      KaizenService kaizenService) {
        this(launcherClient, auditService, settingsService, kaizenService, null);
    }

    /** Called only while the caller's own live instance window is genuinely open - never launches or
     * tears down anything itself. */
    public void checkLiveInstance(ProjectEntity project, String rootUrl) {
        if (!settingsService.effectiveBoolean("design_shop_enabled")) {
            return;
        }

        // Falsification Harness / Level of Abstraction (Prescription 28: FALSIFICATION_HARNESS / D008 + LEVEL_OF_ABSTRACTION_LOCK / D010):
        // Drift comparison requires an established per-project design-system baseline (captured by
        // DesignShopOrchestrationService.captureBaseline into DesignShopCycleEntity).
        // If no baseline exists, fetching the 70KB live HTML body every cycle is pure unexecutable waste (muda).
        java.util.Optional<com.eneik.production.models.persistence.DesignShopCycleEntity> cycleOpt =
                designShopCycleRepository != null && project != null && project.getId() != null
                        ? designShopCycleRepository.findByProjectId(project.getId())
                        : java.util.Optional.empty();

        if (cycleOpt.isEmpty() || cycleOpt.get().getDeclaredColors() == null || cycleOpt.get().getDeclaredColors().isBlank()) {
            log.info("DesignDriftMonitorService: project {} has no established per-project design-system baseline yet; skipping live page fetch and drift comparison",
                    project != null ? project.getId() : "null");
            return;
        }

        RuntimeLauncherClient.FetchResult fetched = launcherClient.fetchHtml(rootUrl);
        if (fetched.body() == null || fetched.body().isBlank()) {
            log.info("DesignDriftMonitorService: no fetchable body from {} for project {} ({}); skipping drift check",
                    rootUrl, project.getId(), fetched.error());
            return;
        }

        com.eneik.production.models.persistence.DesignShopCycleEntity cycle = cycleOpt.get();
        var declaredTokens = DesignConsistencyAuditService.TokenSet.of(cycle.declaredColorsList(), cycle.declaredFontsList());
        DesignConsistencyAuditService.ConsistencyReport report = auditService.audit(fetched.body(), declaredTokens, java.util.List.of());

        if (report.isCannotJudge()) {
            log.info("DesignDriftMonitorService: fetched live page for project {} ({} chars) - verdict: {} ({}); "
                            + "raw server response contains no extractable CSS tokens (SPA shell/client-rendered); cannot judge drift at this abstraction layer",
                    project.getId(), fetched.body().length(), report.verdict(), report.displayVerdict());
            return;
        }

        if (report.traceAccepted()) {
            log.info("DesignDriftMonitorService: drift comparison PASSED for project {} (traceRatio={}, declaredTokens={})",
                    project.getId(), report.traceRatio(), report.declaredTokens());
        } else {
            log.warn("DesignDriftMonitorService: drift comparison FAILED for project {} (traceRatio={}, required>={}, offTokens={}, declaredTokens={})",
                    project.getId(), report.traceRatio(), DesignConsistencyAuditService.MIN_TRACE_RATIO,
                    report.offTokenValues(), report.declaredTokens());
        }
    }
}
