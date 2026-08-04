package com.eneik.production.controllers.audit;

import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.toc.service.TocSentinelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

/**
 * REST Controller for System Quality & Operational Audit.
 * Provides endpoints for 6 Sigma quality audits and combined TOC operational audits.
 */
@RestController
@RequestMapping("/api/audit")
public class SystemAuditController {

    private final SixSigmaAuditService sixSigmaAuditService;
    private final TocSentinelService tocSentinelService;

    public SystemAuditController(SixSigmaAuditService sixSigmaAuditService,
                                 TocSentinelService tocSentinelService) {
        this.sixSigmaAuditService = sixSigmaAuditService;
        this.tocSentinelService = tocSentinelService;
    }

    // 2026-08-04 (3-layer Factory/Delivery/Product model): layer defaults to "delivery" - the pre-existing
    // whole-project flat aggregate, unchanged shape, for backward compatibility with any existing caller
    // that never sent this param. "factory" and "product" are the two new, honestly distinct numbers -
    // see SixSigmaAuditService.calculateFullSixSigmaAudit/calculateProductLayerSixSigmaAudit's own
    // javadoc for exactly what each one counts and why they're allowed to legitimately differ.
    @GetMapping("/six-sigma")
    public ResponseEntity<SixSigmaAuditService.SixSigmaAuditReport> getSixSigmaAudit(
            @RequestParam(name = "projectId", required = false) String projectId,
            @RequestParam(name = "layer", required = false, defaultValue = "delivery") String layer) {
        UUID pId = (projectId != null && !projectId.isBlank() && !"null".equalsIgnoreCase(projectId.trim()))
                ? UUID.fromString(projectId.trim())
                : sixSigmaAuditService.getActiveProjectId();
        return switch (layer.toLowerCase(java.util.Locale.ROOT)) {
            case "factory" -> ResponseEntity.ok(sixSigmaAuditService.calculateFullSixSigmaAudit());
            case "product" -> ResponseEntity.ok(sixSigmaAuditService.calculateProductLayerSixSigmaAudit(pId));
            default -> ResponseEntity.ok(sixSigmaAuditService.calculateProjectSixSigmaAudit(pId));
        };
    }

    @GetMapping("/six-sigma/project/{projectId}")
    public ResponseEntity<SixSigmaAuditService.SixSigmaAuditReport> getProjectSixSigmaAudit(
            @PathVariable String projectId) {
        UUID pId = (projectId != null && !projectId.isBlank()) ? UUID.fromString(projectId.trim()) : null;
        return ResponseEntity.ok(sixSigmaAuditService.calculateProjectSixSigmaAudit(pId));
    }

    @GetMapping("/full")
    public ResponseEntity<Map<String, Object>> getFullSystemAudit() {
        var sixSigma = sixSigmaAuditService.calculateFullSixSigmaAudit();
        var dbrStatus = tocSentinelService.getDbrStatus();
        var anomalies = tocSentinelService.getRecentAnomalies();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auditTimestamp", Instant.now());
        result.put("sixSigmaQualityAudit", sixSigma);
        result.put("tocOperationalStatus", dbrStatus);
        result.put("recentExecutionAnomalies", anomalies);
        result.put("systemHealthSummary", Map.of(
                "sigmaLevel", sixSigma.sigmaLevel(),
                "qualityTier", sixSigma.qualityTier(),
                "overallDpmo", sixSigma.dpmo(),
                "firstTimeYieldPercent", sixSigma.yieldRatePercent(),
                "primaryConstraintNode", dbrStatus.primaryConstraintNode(),
                "ropeThrottlingActive", dbrStatus.ropeThrottlingActive()
        ));

        return ResponseEntity.ok(result);
    }
}
