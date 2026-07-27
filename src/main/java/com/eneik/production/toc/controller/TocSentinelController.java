package com.eneik.production.toc.controller;

import com.eneik.production.toc.model.AnomalyReport;
import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocToken;
import com.eneik.production.toc.service.TocSentinelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for TOC Sentinel Service.
 * Provides real-time metrics, constraint status, anomaly audit log, and telemetry endpoints.
 */
@RestController
@RequestMapping("/api/toc")
public class TocSentinelController {

    private final TocSentinelService tocSentinelService;

    public TocSentinelController(TocSentinelService tocSentinelService) {
        this.tocSentinelService = tocSentinelService;
    }

    @GetMapping("/status")
    public ResponseEntity<DbrStatus> getStatus() {
        return ResponseEntity.ok(tocSentinelService.getDbrStatus());
    }

    @GetMapping("/constraint")
    public ResponseEntity<Map<String, Object>> getConstraint() {
        DbrStatus status = tocSentinelService.getDbrStatus();
        Map<String, Object> res = new HashMap<>();
        res.put("primaryConstraint", status.primaryConstraintNode());
        res.put("queueLength", status.constraintQueueLength());
        res.put("utilization", status.constraintUtilization());
        res.put("meanDurationMs", status.constraintMeanDurationMs());
        res.put("throttlingActive", status.ropeThrottlingActive());
        res.put("recommendation", status.recommendation());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> getGraph() {
        Map<String, Object> res = new HashMap<>();
        Collection<TocNode> nodes = tocSentinelService.getGraph().getAllNodes();
        res.put("nodeCount", nodes.size());
        res.put("nodes", nodes);
        res.put("edges", tocSentinelService.getGraph().getEdges());
        res.put("activeTokenCount", tocSentinelService.getGraph().getActiveTokens().size());
        res.put("arrivalRatePerSec", tocSentinelService.getGraph().getGlobalArrivalRatePerSec());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<AnomalyReport>> getAnomalies() {
        return ResponseEntity.ok(tocSentinelService.getRecentAnomalies());
    }

    public record StepEnterRequest(String tokenId, String scenarioName, Integer priority, String stepName) {}
    public record StepExitRequest(String tokenId, String stepName, Boolean success) {}
    public record ResourceRequest(String tokenId, String resourceId) {}

    @PostMapping("/event/enter")
    public ResponseEntity<Map<String, Object>> enterStep(@RequestBody StepEnterRequest req) {
        String scenario = req.scenarioName() != null ? req.scenarioName() : "HTTP_SCENARIO";
        int prio = req.priority() != null ? req.priority() : 0;
        String tid = req.tokenId();

        TocToken token = tid != null ? tocSentinelService.getGraph().getToken(tid) : null;
        if (token == null) {
            token = tid != null
                    ? tocSentinelService.startExecutionWithId(tid, scenario, prio)
                    : tocSentinelService.startExecution(scenario, prio);
        }

        if (token.getStatus() == TocToken.TokenStatus.THROTTLED) {
            Map<String, Object> err = new HashMap<>();
            err.put("admitted", false);
            err.put("status", "THROTTLED");
            err.put("reason", "DBR Rope throttled execution due to constraint buffer overflow");
            return ResponseEntity.status(429).body(err);
        }

        boolean allowed = tocSentinelService.enterStep(token, req.stepName());
        Map<String, Object> res = new HashMap<>();
        res.put("tokenId", token.getTokenId());
        res.put("admitted", allowed);
        res.put("status", token.getStatus().name());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/event/exit")
    public ResponseEntity<Map<String, Object>> exitStep(@RequestBody StepExitRequest req) {
        TocToken token = tocSentinelService.getGraph().getToken(req.tokenId());
        if (token != null) {
            tocSentinelService.exitStep(token, req.stepName(), req.success() != null ? req.success() : true);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("tokenId", req.tokenId());
        res.put("status", token != null ? token.getStatus().name() : "NOT_FOUND");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/resource/acquire")
    public ResponseEntity<Map<String, Object>> acquireResource(@RequestBody ResourceRequest req) {
        TocToken token = tocSentinelService.getGraph().getToken(req.tokenId());
        if (token != null) {
            tocSentinelService.acquireResource(token, req.resourceId());
        }
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @PostMapping("/resource/wait")
    public ResponseEntity<Map<String, Object>> waitResource(@RequestBody ResourceRequest req) {
        TocToken token = tocSentinelService.getGraph().getToken(req.tokenId());
        boolean deadlockDetected = false;
        if (token != null) {
            deadlockDetected = tocSentinelService.waitResource(token, req.resourceId());
        }
        return ResponseEntity.ok(Map.of("deadlockDetected", deadlockDetected));
    }

    @PostMapping("/resource/release")
    public ResponseEntity<Map<String, Object>> releaseResource(@RequestBody ResourceRequest req) {
        TocToken token = tocSentinelService.getGraph().getToken(req.tokenId());
        if (token != null) {
            tocSentinelService.releaseResource(token, req.resourceId());
        }
        return ResponseEntity.ok(Map.of("status", "OK"));
    }
}
