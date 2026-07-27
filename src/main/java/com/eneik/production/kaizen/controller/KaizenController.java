package com.eneik.production.kaizen.controller;

import com.eneik.production.kaizen.model.KaizenProposal;
import com.eneik.production.kaizen.service.KaizenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Kaizen Micro-Improvement Engine.
 */
@RestController
@RequestMapping("/api/kaizen")
public class KaizenController {

    private final KaizenService kaizenService;

    public KaizenController(KaizenService kaizenService) {
        this.kaizenService = kaizenService;
    }

    @GetMapping("/opportunities")
    public ResponseEntity<List<KaizenProposal>> getOpportunities(@RequestParam(name = "projectId", required = false) java.util.UUID projectId) {
        List<KaizenProposal> open = kaizenService.getProposalsForProject(projectId).stream()
                .filter(p -> p.getStatus() == KaizenProposal.ProposalStatus.PROPOSED)
                .toList();
        return ResponseEntity.ok(open);
    }

    @GetMapping("/history")
    public ResponseEntity<Collection<KaizenProposal>> getHistory(@RequestParam(name = "projectId", required = false) java.util.UUID projectId) {
        return ResponseEntity.ok(kaizenService.getProposalsForProject(projectId));
    }

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanOpportunities(@RequestParam(name = "projectId", required = false) java.util.UUID projectId) {
        List<KaizenProposal> scanned = kaizenService.scanForOpportunities(projectId);
        return ResponseEntity.ok(Map.of(
                "scannedCount", scanned.size(),
                "proposals", scanned
        ));
    }

    @PostMapping("/step/{proposalId}")
    public ResponseEntity<Map<String, Object>> executeKaizenStep(@PathVariable String proposalId) {
        boolean applied = kaizenService.applyMicroStep(proposalId);
        KaizenProposal result = null;
        if (applied) {
            result = kaizenService.evaluateAndStandardize(proposalId);
        }
        return ResponseEntity.ok(Map.of(
                "applied", applied,
                "proposal", result != null ? result : Map.of("id", proposalId, "status", "NOT_FOUND")
        ));
    }
}
