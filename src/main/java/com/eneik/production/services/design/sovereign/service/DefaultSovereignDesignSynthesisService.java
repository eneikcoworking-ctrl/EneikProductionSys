package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.DesignTokenSet;
import com.eneik.production.services.design.sovereign.domain.LayoutGridPattern;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.SynthesisSession;
import com.eneik.production.services.design.sovereign.domain.SynthesisStatus;
import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;
import com.eneik.production.services.design.sovereign.domain.VisionAuditReport;
import com.eneik.production.services.design.sovereign.telemetry.DesignTelemetryCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default orchestrator for the Autonomous Sovereign Designer Engine pipeline,
 * integrating brief-conditioned synthesis, Stanford Memorilla latent slots (K=16),
 * empirical Playwright viewport captures, and GQM telemetry recording.
 */
@Service
public class DefaultSovereignDesignSynthesisService implements SovereignDesignSynthesisService {

    private final DesignTokenCompiler tokenCompiler;
    private final LayoutPatternRegistry patternRegistry;
    private final SovereignProjectAssetResolver assetService;
    private final SemanticLayoutSynthesizer layoutSynthesizer;
    private final PlaywrightVisionHarness visionHarness;
    private final VisionAuditLoop auditLoop;
    private final DesignTelemetryCollector telemetryCollector;
    private final PrimaryAssetManagementSystem primaryAssetManagementSystem;
    private final Clock clock;

    @Autowired
    public DefaultSovereignDesignSynthesisService(
            DesignTokenCompiler tokenCompiler,
            LayoutPatternRegistry patternRegistry,
            SovereignProjectAssetResolver assetService,
            SemanticLayoutSynthesizer layoutSynthesizer,
            PlaywrightVisionHarness visionHarness,
            VisionAuditLoop auditLoop,
            DesignTelemetryCollector telemetryCollector,
            PrimaryAssetManagementSystem primaryAssetManagementSystem
    ) {
        this(tokenCompiler, patternRegistry, assetService, layoutSynthesizer, visionHarness, auditLoop, telemetryCollector, primaryAssetManagementSystem, Clock.systemUTC());
    }

    public DefaultSovereignDesignSynthesisService(
            DesignTokenCompiler tokenCompiler,
            LayoutPatternRegistry patternRegistry,
            SovereignProjectAssetResolver assetService,
            SemanticLayoutSynthesizer layoutSynthesizer,
            PlaywrightVisionHarness visionHarness,
            VisionAuditLoop auditLoop,
            DesignTelemetryCollector telemetryCollector,
            PrimaryAssetManagementSystem primaryAssetManagementSystem,
            Clock clock
    ) {
        this.tokenCompiler = Objects.requireNonNull(tokenCompiler, "tokenCompiler cannot be null");
        this.patternRegistry = Objects.requireNonNull(patternRegistry, "patternRegistry cannot be null");
        this.assetService = Objects.requireNonNull(assetService, "assetService cannot be null");
        this.layoutSynthesizer = Objects.requireNonNull(layoutSynthesizer, "layoutSynthesizer cannot be null");
        this.visionHarness = Objects.requireNonNull(visionHarness, "visionHarness cannot be null");
        this.auditLoop = Objects.requireNonNull(auditLoop, "auditLoop cannot be null");
        this.telemetryCollector = Objects.requireNonNull(telemetryCollector, "telemetryCollector cannot be null");
        this.primaryAssetManagementSystem = Objects.requireNonNull(primaryAssetManagementSystem, "primaryAssetManagementSystem cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public SynthesisSession executeDesignSynthesis(ProductArchetype archetype, String projectContextKey) {
        return executeDesignSynthesis(archetype, projectContextKey, null, List.of());
    }

    @Override
    public SynthesisSession executeDesignSynthesis(
            ProductArchetype archetype,
            String projectContextKey,
            String brief,
            List<String> memorillaLatentSlots
    ) {
        Objects.requireNonNull(archetype, "archetype cannot be null");
        SynthesisSession session = SynthesisSession.create(archetype, clock.instant());

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("projectContextKey", projectContextKey != null ? projectContextKey : "default");
        if (brief != null && !brief.isBlank()) {
            meta.put("briefLength", String.valueOf(brief.length()));
        }
        if (memorillaLatentSlots != null && !memorillaLatentSlots.isEmpty()) {
            meta.put("memorillaSlots", String.valueOf(memorillaLatentSlots.size()));
        }

        try {
            DesignTokenSet tokenSet = tokenCompiler.compileTokens(archetype);
            LayoutGridPattern gridPattern = patternRegistry.resolveDefaultPattern(archetype);
            Map<String, String> assets = assetService.resolveProjectAssets(projectContextKey);

            session.transitionToSynthesizing();
            SynthesizedDomDocument currentDocument = layoutSynthesizer.synthesizeLayout(
                    tokenSet,
                    gridPattern,
                    assets,
                    session.getCurrentRound(),
                    brief,
                    memorillaLatentSlots != null ? memorillaLatentSlots : List.of()
            );

            List<ViewportType> viewports = List.of(ViewportType.DESKTOP_1440, ViewportType.MOBILE_375);

            while (true) {
                session.recordDomAndTransitionToAuditing(currentDocument);

                List<ViewportCapture> captures = visionHarness.captureViewports(currentDocument, viewports);
                session.recordCaptures(captures);
                VisionAuditReport report = auditLoop.auditDocument(currentDocument, captures);

                telemetryCollector.recordAuditCompleted(
                        session.getId(),
                        archetype,
                        session.getCurrentRound(),
                        report.overallScore(),
                        report.deonticGateResult().allGatesPassed(),
                        meta
                );

                SynthesisStatus status = session.evaluateAuditAndTransition(report);

                if (status == SynthesisStatus.COMPLETED_APPROVED || status == SynthesisStatus.COMPLETED_FALLBACK) {
                    telemetryCollector.recordGenerationSuccess(
                            session.getId(),
                            archetype,
                            session.getCurrentRound(),
                            report.overallScore(),
                            meta
                    );
                    primaryAssetManagementSystem.submitAssets(projectContextKey, currentDocument, captures);
                    break;
                } else if (status == SynthesisStatus.REFINING) {
                    session.transitionToSynthesizing();
                    currentDocument = layoutSynthesizer.refineLayout(
                            currentDocument,
                            tokenSet,
                            report.correctionDirectives(),
                            session.getCurrentRound()
                    );
                } else {
                    String reason = "Unexpected state after audit evaluation: " + status;
                    session.transitionToFailed(reason);
                    telemetryCollector.recordGenerationFailure(
                            session.getId(),
                            archetype,
                            session.getCurrentRound(),
                            reason,
                            meta
                    );
                    break;
                }
            }
        } catch (Exception e) {
            String failureReason = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            if (!session.getStatus().isTerminal()) {
                session.transitionToFailed(failureReason);
            }
            telemetryCollector.recordGenerationFailure(
                    session.getId(),
                    archetype,
                    session.getCurrentRound(),
                    failureReason,
                    meta
            );
        }

        return session;
    }
}
