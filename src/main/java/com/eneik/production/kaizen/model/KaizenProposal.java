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
        SYSTEMIC_DEFECT,
        // 2026-08-01 (Layer 4 of the unified Lean/TOC/Six Sigma system, closing Measure->Analyze->
        // Improve->Control): a u-chart out-of-control signal (ProcessControlService) whose underlying
        // defect events already carry a rootCausePatternId matching one of the 12 documented patterns in
        // docs/ENGINEERING_INVARIANTS_CHARTER.md - a KNOWN fix shape, cited by number. Review-only, same
        // boundary as SYSTEMIC_DEFECT (expectedGainPercent=0, never auto-applied) - the specific code fix
        // still needs human/Claude judgment, but the diagnosis itself is no longer free-text guesswork.
        KNOWN_PATTERN_VIOLATION,
        // 2026-08-07: a role's own historical ems_defect_weight (computed per-task at compile time,
        // TechnicalLeadCompiler.criticalityScore, previously persisted into every task's payload and never
        // read back - confirmed live audit) trending meaningfully upward across a project's own history -
        // a leading indicator of a systemic quality problem in how that role is being executed, not a
        // one-off task failure. Review-only, same boundary as SYSTEMIC_DEFECT/KNOWN_PATTERN_VIOLATION
        // (expectedGainPercent=0, never auto-applied) - a real trend needs human/Claude judgment on cause.
        ROLE_QUALITY_DRIFT,
        // 2026-08-09 (Phase 3 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md): a
        // RuntimeHealthShiftDetector-confirmed statistical shift in the ACTIVE PRODUCT's own real launch/
        // health-check behavior (not the factory's construction quality, not this codebase's own defects -
        // SYSTEMIC_DEFECT already owns that). Deliberately its own category, never folded into
        // SYSTEMIC_DEFECT, so the two streams stay visibly separate on the same dashboard (operator's own
        // requirement: "явно помечена как улучшение продукта, не смешивается с заводским списком").
        // Review-only, same boundary as every category above (expectedGainPercent=0, never auto-applied).
        PRODUCT_RUNTIME_DEFECT
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

    /**
     * Reconstruction from persistence (KaizenProposalEntity.toDomain()) - the other constructors always
     * stamp createdAt=now(), which would silently fabricate a fresh timestamp for every proposal read back
     * from the database instead of its real original creation time.
     */
    public KaizenProposal(String id, String title, KaizenCategory category, String targetComponent,
                          String actionDescription, double expectedGainPercent,
                          java.util.UUID projectId, String projectName, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.targetComponent = targetComponent;
        this.actionDescription = actionDescription;
        this.expectedGainPercent = expectedGainPercent;
        this.projectId = projectId;
        this.projectName = projectName;
        this.createdAt = createdAt;
        this.status = ProposalStatus.PROPOSED;
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
