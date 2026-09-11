package com.eneik.production.controllers;

import com.eneik.production.services.audit.SixSigmaAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * QualityGateController: HTTP observation surface for quality-gate defect aggregates.
 *
 * Antigravity L2 Alignment (Ideal Model):
 * - Unified Single Source of Truth: delegates defect computation to SixSigmaAuditService
 *   rather than duplicating all-task scanning and calculation.
 * - Belnap 4-valued logic / 3-way check outcome (NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D012):
 *   absence of "passed" field is explicitly counted as "undetermined" instead of crashing
 *   with NullPointerException or silently assuming success.
 * - Scoped acquisition: loads only tasks with non-null qualityGateReport, supporting
 *   both global (null) and project-scoped queries without full table scans.
 */
@RestController
@RequestMapping("/api/quality-gate")
public class QualityGateController {

    private final SixSigmaAuditService sixSigmaAuditService;

    public QualityGateController(SixSigmaAuditService sixSigmaAuditService) {
        this.sixSigmaAuditService = sixSigmaAuditService;
    }

    @GetMapping("/defect-rate")
    public Map<String, Object> getDefectRate(@RequestParam(value = "projectId", required = false) UUID projectId) {
        return sixSigmaAuditService.computeQualityGateDefectRate(projectId);
    }
}
