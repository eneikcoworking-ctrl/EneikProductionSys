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

    public DesignDriftMonitorService(RuntimeLauncherClient launcherClient,
                                      DesignConsistencyAuditService auditService,
                                      SystemSettingsService settingsService,
                                      KaizenService kaizenService) {
        this.launcherClient = launcherClient;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.kaizenService = kaizenService;
    }

    /** Called only while the caller's own live instance window is genuinely open - never launches or
     * tears down anything itself. */
    public void checkLiveInstance(ProjectEntity project, String rootUrl) {
        if (!settingsService.effectiveBoolean("design_shop_enabled")) {
            return;
        }
        RuntimeLauncherClient.FetchResult fetched = launcherClient.fetchHtml(rootUrl);
        if (fetched.body() == null || fetched.body().isBlank()) {
            log.info("DesignDriftMonitorService: no fetchable body from {} for project {} ({}); skipping drift check",
                    rootUrl, project.getId(), fetched.error());
            return;
        }
        // No per-project canonical palette to audit against yet (see class javadoc) - the fetch above
        // already proves the live page is reachable and serving real content; the token-drift comparison
        // itself is intentionally not run until a real per-project baseline exists.
        log.info("DesignDriftMonitorService: fetched live page for project {} ({} chars) - drift comparison "
                        + "skipped, no established per-project design-system baseline yet",
                project.getId(), fetched.body().length());
    }
}
