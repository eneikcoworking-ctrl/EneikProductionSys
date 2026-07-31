package com.eneik.production.kaizen.model;

import java.time.Instant;

/**
 * Model representing a micro-improvement proposal in the Kaizen continuous improvement engine.
 */
public class KaizenProposal {

    public enum KaizenCategory {
        WASTE_REDUCTION,      // Elimination of Muda (waiting waste, orphaned claims)
        SPEED_OPTIMIZATION,   // Latency & bottleneck throughput optimization
        DEFECT_ELIMINATION,   // Preventing repeated quality gate & conflict failures
        BUFFER_TUNING,        // DBR buffer capacity & timeout sensitivity adjustment
        // 2026-08-01: findings about the orchestrator/factory's OWN code (not the client product) - e.g.
        // Gemini Observer noticing a bug in EneikProductionSys itself. Deliberately review-only, never
        // auto-applied by periodicKaizenCycle (see KaizenService.recordSystemicDefectProposal's 0%
        // expectedGainPercent) - fixing the factory's own source code is never a safe automatic action,
        // unlike this engine's other three categories which only ever tune runtime parameters.
        SYSTEMIC_DEFECT
    }

    public enum ProposalStatus {
        PROPOSED,      // Identified opportunity, waiting execution
        APPLIED,       // Micro-improvement applied (Do phase)
        STANDARDIZED,  // Verified positive impact (Act phase)
        REVERTED       // Rolled back due to negative/neutral impact
    }

    private final String id;
    private final String title;
    private final KaizenCategory category;
    private final String targetComponent;
    private final String actionDescription;
    private final double expectedGainPercent;
    private final java.util.UUID projectId;
    private final String projectName;
    private final Instant createdAt;

    private volatile ProposalStatus status;
    private volatile Double baselineMetric;
    private volatile Double postMetric;
    private volatile Instant appliedAt;

    public KaizenProposal(String id, String title, KaizenCategory category, String targetComponent,
                          String actionDescription, double expectedGainPercent,
                          java.util.UUID projectId, String projectName) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.targetComponent = targetComponent;
        this.actionDescription = actionDescription;
        this.expectedGainPercent = expectedGainPercent;
        this.projectId = projectId;
        this.projectName = projectName;
        this.createdAt = Instant.now();
        this.status = ProposalStatus.PROPOSED;
    }

    public KaizenProposal(String id, String title, KaizenCategory category, String targetComponent,
                          String actionDescription, double expectedGainPercent) {
        this(id, title, category, targetComponent, actionDescription, expectedGainPercent, null, "Global");
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public KaizenCategory getCategory() { return category; }
    public String getTargetComponent() { return targetComponent; }
    public String getActionDescription() { return actionDescription; }
    public double getExpectedGainPercent() { return expectedGainPercent; }
    public java.util.UUID getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public Instant getCreatedAt() { return createdAt; }

    public ProposalStatus getStatus() { return status; }
    public void setStatus(ProposalStatus status) { this.status = status; }

    public Double getBaselineMetric() { return baselineMetric; }
    public void setBaselineMetric(Double baselineMetric) { this.baselineMetric = baselineMetric; }

    public Double getPostMetric() { return postMetric; }
    public void setPostMetric(Double postMetric) { this.postMetric = postMetric; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
}
