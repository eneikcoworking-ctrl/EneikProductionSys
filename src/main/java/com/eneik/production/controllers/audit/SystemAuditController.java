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

    @GetMapping("/six-sigma")
    public ResponseEntity<SixSigmaAuditService.SixSigmaAuditReport> getSixSigmaAudit() {
        return ResponseEntity.ok(sixSigmaAuditService.calculateFullSixSigmaAudit());
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
