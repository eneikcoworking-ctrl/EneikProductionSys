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
 * "is it alive". Same canonical Verdant Flow tokens already used by DesignSystemFalsificationService
 * and DesignShopOrchestrationService.
 */
@Service
public class DesignDriftMonitorService {
    private static final Logger log = LoggerFactory.getLogger(DesignDriftMonitorService.class);

    private static final List<String> DESIGN_TOKEN_COLORS = List.of(
            "#fbf9f1", "#7d8570", "#3f7d32", "#d97b29", "#e0342f", "#c99a2e");
    private static final List<String> DESIGN_TOKEN_FONTS = List.of("Libre Caslon Text", "IBM Plex Sans");

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

        DesignConsistencyAuditService.TokenSet declared =
                DesignConsistencyAuditService.TokenSet.of(DESIGN_TOKEN_COLORS, DESIGN_TOKEN_FONTS);
        DesignConsistencyAuditService.ConsistencyReport report = auditService.audit(fetched.body(), declared, List.of());
        if (report.traceAccepted()) {
            return;
        }

        String title = "Design drift on the live running product for "
                + (project.getName() != null ? project.getName() : project.getId());
        String description = String.format(
                "DesignConsistencyAuditService E(f): live-served page trace ratio %.2f (threshold %.2f), "
                        + "%d off-token value(s): %s",
                report.traceRatio(), DesignConsistencyAuditService.MIN_TRACE_RATIO,
                report.offTokenValues().size(), report.offTokenValues());
        kaizenService.recordProductRuntimeDefectProposal(project.getId(), project.getName(), title, description);
        log.info("DesignDriftMonitorService: recorded design drift finding for project {} (traceRatio={})",
                project.getId(), report.traceRatio());
    }
}
