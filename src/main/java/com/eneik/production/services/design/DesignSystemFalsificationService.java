package com.eneik.production.services.design;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.logging.LogScope;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.stitch.StitchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase B of the design/QA acceptance redesign (2026-08-04): design's REAL completion signal, distinct
 * from the build-time BARCAN-TAG-03 exemption in ClientDeliverableReadinessService.hasRequiredMergeEvidence
 * (which only ever accepts a speculative draft mockup). Once an epic's real UI has genuinely merged to
 * main - {@link ClientDeliverableReadinessService#listEpicsWithMergedUiCode} - this applies Stitch's
 * design-system tooling against that REAL, already-shipped UI, not a pre-build guess. Revives a
 * previously-recorded, never-implemented idea ("Stitch-as-falsification": design work should refine real
 * UI, not pre-generate speculative mockups).
 *
 * Deliberately calls Stitch directly from backend code rather than dispatching a Jules session for this:
 * applying a design system is mechanical API composition with no role judgment involved - unlike
 * philosophical critique or coverage-gap work, a Jules session's own reasoning adds nothing here, and a
 * self-attested "I called Stitch" from inside a session would reopen exactly the self-attestation problem
 * DesignExcellenceGate's own history was built to close (see that class's javadoc). The backend calling
 * Stitch directly and recording the real designSystemId it received back is intrinsically the
 * independently-verified version.
 *
 * No Jules session means none of the account-capacity contention the other falsification tracks
 * (FalsificationCycleService) carefully manage - this can run frequently and cheaply.
 */
@Service
public class DesignSystemFalsificationService {

    private static final Logger log = LoggerFactory.getLogger(DesignSystemFalsificationService.class);

    private final ProjectRepository projectRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final StitchClient stitchClient;
    // F45 (2026-08-16): the only place a Stitch project id for this project is persisted.
    private final com.eneik.production.repositories.DesignShopCycleRepository designShopCycleRepository;
    private final WishlistRepository wishlistRepository;
    private final SystemSettingsService settingsService;

    // Self-injected proxy reference (2026-08-14, same pattern/reason as JulesDispatchService.self): a plain
    // `this.recordAuditTrail(...)` call bypasses the Spring AOP proxy entirely, so
    // @Transactional(REQUIRES_NEW) on it would silently never activate. @Lazy breaks the constructor
    // circular dependency this would otherwise create.
    private final DesignSystemFalsificationService self;

    public DesignSystemFalsificationService(ProjectRepository projectRepository,
                                             ClientDeliverableReadinessService readinessService,
                                             StitchClient stitchClient,
                                             com.eneik.production.repositories.DesignShopCycleRepository designShopCycleRepository,
                                             WishlistRepository wishlistRepository,
                                             SystemSettingsService settingsService,
                                             @org.springframework.context.annotation.Lazy DesignSystemFalsificationService self) {
        this.projectRepository = projectRepository;
        this.readinessService = readinessService;
        this.stitchClient = stitchClient;
        this.designShopCycleRepository = designShopCycleRepository;
        this.wishlistRepository = wishlistRepository;
        this.settingsService = settingsService;
        this.self = self;
    }

    // 2026-08-14 (bug-hunt sweep): used to be @Transactional, holding a DB transaction open across the
    // whole activeProjects loop AND, per eligible epic, two sequential real Stitch API calls
    // (createDesignSystem, applyDesignSystem) - same bug class as the 2026-08-07 lock-timeout incident, but
    // spanning every active project's every eligible epic in one transaction rather than a single call. No
    // longer @Transactional here: the readiness/dedup reads below are each independently transactional
    // (Spring Data JPA default / ClientDeliverableReadinessService has no transaction of its own to nest
    // into), the Stitch calls now run with no transaction open, and only the final audit-trail write
    // (recordAuditTrail) runs inside its own short REQUIRES_NEW transaction.
    @Scheduled(cron = "${design-system-falsification.cron:0 */30 * * * ?}")
    public void applyDesignSystemsToShippedEpics() {
        if (!settingsService.effectiveBoolean("design_system_falsification_enabled") || !stitchClient.hasStitchKey()) {
            return;
        }
        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            LogScope.project(project.getId());
            try {
                applyDesignSystemsForProject(project);
            } catch (Exception e) {
                log.error("DesignSystemFalsificationService: design-system pass failed for project {}: {}",
                        project.getId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
    }

    private void applyDesignSystemsForProject(ProjectEntity project) {
        List<ClientDeliverableReadinessService.UiCodeEpic> eligibleEpics =
                readinessService.listEpicsWithMergedUiCode(project.getId());
        for (ClientDeliverableReadinessService.UiCodeEpic epic : eligibleEpics) {
            if (wishlistRepository.existsByFeatureIdAndSource(epic.featureId(), WishlistSource.design_system_falsification)) {
                continue; // already processed this epic - see WishlistRepository.existsByFeatureIdAndSource
            }

            // 2026-08-10: create_design_system's real MCP schema is structural (displayName + theme
            // fields), not a free-text derivation prompt - confirmed live against tools/list. There is
            // no API-level way to have Stitch "derive" a theme from a text description of shipped UI;
            // the epic title becomes the display name and the project's standard Verdant Flow tokens
            // are used as the theme, same tokens as ProductTree.svelte.
            // F45 (2026-08-16, found on the live run): this used to pass project.getId() - a row id in
            // THIS system's database - as Stitch's projectId, and Stitch answered exactly what was true:
            // "Requested entity was not found". A Stitch project id is not an Eneik project id, which the
            // codebase already knew in one place (DesignAssetResult.stitchProjectId) and not in this one.
            //
            // The id is persisted per project on the design-shop cycle row, created by
            // StitchClient.createProject. When it is absent the argument is OMITTED rather than filled with
            // something else: the client treats a blank projectId as "not supplied", so the design system is
            // created unattached. Sending a wrong reference is worse than sending none - one fails loudly
            // every cycle, the other simply attaches nothing.
            String stitchProjectId = designShopCycleRepository.findByProjectId(project.getId())
                    .map(com.eneik.production.models.persistence.DesignShopCycleEntity::getStitchProjectId)
                    .filter(id -> id != null && !id.isBlank())
                    .orElse(null);
            StitchClient.DesignSystemResult created = stitchClient.createDesignSystem(
                    stitchProjectId, epic.title(), "LIBRE_CASLON_TEXT", "IBM_PLEX_SANS",
                    "#7d8570", "#c99a2e");
            if (!created.available()) {
                log.warn("DesignSystemFalsificationService: could not create a Stitch design system for epic {} ({}): {}",
                        epic.featureId(), epic.title(), created.message());
                continue;
            }

            StitchClient.ApplyDesignSystemResult applied =
                    stitchClient.applyDesignSystem(project.getId().toString(), created.designSystemId(), List.of());

            self.recordAuditTrail(project.getId(), epic, created, applied);
        }
    }

    // Audit-trail record only, deliberately status=dismissed - never picked up as compiler input by
    // TechnicalLeadCompiler/ProjectFlowService.orchestrate (both only ever act on `pending` items), same
    // convention already used elsewhere for "exists, but nothing to compile" rows.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordAuditTrail(java.util.UUID projectId, ClientDeliverableReadinessService.UiCodeEpic epic,
            StitchClient.DesignSystemResult created, StitchClient.ApplyDesignSystemResult applied) {
        WishlistEntity record = new WishlistEntity();
        record.setProjectId(projectId);
        record.setFeatureId(epic.featureId());
        record.setSource(WishlistSource.design_system_falsification);
        record.setStatus(WishlistStatus.dismissed);
        record.setContent("Stitch design system " + created.designSystemId() + " applied to epic '" + epic.title()
                + "': " + (applied.available() ? "applied (" + applied.status() + ")" : "apply failed - " + applied.message()));
        wishlistRepository.save(record);
        log.info("DesignSystemFalsificationService: recorded design-system pass for epic {} ({}), designSystemId={}, applied={}",
                epic.featureId(), epic.title(), created.designSystemId(), applied.available());
    }
}
