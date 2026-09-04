package com.eneik.production.models.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @ManyToOne
    @JoinColumn(name = "tag", nullable = false)
    private RoleEntity role;

    @Column(nullable = false)
    private String description;

    @Column(length = 80)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.queued;

    private String linearIssueId;

    private String julesSessionName;

    @Column(length = 2048)
    private String julesDispatchStatus;

    @Column(name = "quality_gate_passed")
    private boolean qualityGatePassed = false;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private int priority = 0;

    @Column(name = "file_scope", columnDefinition = "TEXT")
    private String fileScope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on")
    private TaskEntity dependsOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_gate_report")
    private JsonNode qualityGateReport;

    @Column(name = "cynefin_domain")
    private String cynefinDomain;

    // Real FK mirror of payload.source_wishlist_id (JSON) - see V40 migration. Lets readiness/phase
    // gating join tasks back to their originating client wishlist item without a JSON text search.
    @Column(name = "source_wishlist_id")
    private UUID sourceWishlistId;

    // The feature this task belongs to - always populated at creation (TechnicalLeadCompiler.
    // createAndSaveTask via FeatureService), either inherited from the wishlist that produced it or
    // minted fresh if nothing set one. Scopes FeatureThreadEntity continuation so unrelated features never
    // share a branch just because the same role worked on both.
    @Column(name = "feature_id")
    private UUID featureId;

    // 2026-08-04 (3-layer Factory/Delivery/Product model): immutable lineage - set once, at the same
    // moment featureId is first assigned at task creation, and never touched again by any of the several
    // places that freely rewrite featureId afterward (admin repair endpoints, follow-up-task inheritance,
    // closeout wiring). featureId still means "what this task currently counts toward"; this field means
    // "where it actually came from" - Layer 2 delivery history is grouped by this, not by featureId.
    @Column(name = "origin_feature_id")
    private UUID originFeatureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_context")
    private TargetContext targetContext = TargetContext.PRODUCT_CODEBASE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) { this.project = project; }
    public RoleEntity getRole() { return role; }
    public void setRole(RoleEntity role) { this.role = role; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }

    // The statement this task's completion can be refuted against. It has always been written into
    // payload.acceptance_criteria by TechnicalLeadCompiler, but only ever read back to build the agent's
    // prompt - never to test what came back. These accessors make a task's own falsifier reachable to
    // whatever has to judge delivery, and keep payload the single home of the fact rather than adding a
    // second column that can disagree with it.
    public String getAcceptanceCriteria() {
        if (payload == null) { return null; }
        String value = payload.path("acceptance_criteria").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    // Blank is refused rather than stored: a task whose doneness has no statement is the defect this
    // accessor exists to make unconstructible, and accepting an empty string here would reintroduce it
    // in the one place meant to prevent it.
    public void setAcceptanceCriteria(String acceptanceCriteria) {
        if (acceptanceCriteria == null || acceptanceCriteria.isBlank()) {
            throw new IllegalArgumentException(
                    "A task may not carry blank acceptance criteria: " + title);
        }
        ObjectNode node = (payload instanceof ObjectNode existing)
                ? existing
                : JsonNodeFactory.instance.objectNode();
        node.put("acceptance_criteria", acceptanceCriteria);
        this.payload = node;
    }
    public TaskStatus getStatus() { return status; }

    /**
     * True if the current status of this task is terminal (done, failed, spike_completed).
     */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * Sets the status of this task.
     * Enforces Law 20 / Invariant S2: terminal(τ) ⟹ status(τ) cannot be overwritten.
     *
     * <p>An existing terminal status (done, failed, spike_completed) cannot be overwritten with a different status.
     * Idempotent transitions (setting the same terminal status) are permitted as no-ops.
     * For initialization of freshly minted task entities, use {@link #initializeStatus(TaskStatus)}.
     *
     * @throws IllegalStateException if this task is already terminal and the new status differs
     */
    public void setStatus(TaskStatus status) {
        if (this.status != null && this.status.isTerminal() && this.status != status) {
            throw new IllegalStateException(
                    "Terminal status " + this.status + " of task " + id + " cannot be overwritten with " + status);
        }
        this.status = status;
    }

    /**
     * Explicit initial status assignment during new entity creation.
     */
    public void initializeStatus(TaskStatus status) {
        this.status = status;
    }
    public String getLinearIssueId() { return linearIssueId; }
    public void setLinearIssueId(String linearIssueId) { this.linearIssueId = linearIssueId; }
    public String getJulesSessionName() { return julesSessionName; }
    public void setJulesSessionName(String julesSessionName) { this.julesSessionName = julesSessionName; }
    public String getJulesDispatchStatus() { return julesDispatchStatus; }
    public void setJulesDispatchStatus(String julesDispatchStatus) { this.julesDispatchStatus = julesDispatchStatus; }
    public boolean isQualityGatePassed() { return qualityGatePassed; }
    public void setQualityGatePassed(boolean qualityGatePassed) { this.qualityGatePassed = qualityGatePassed; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    /**
     * Was this task verified for DELIVERY, as opposed to verified for being well specified?
     *
     * `qualityGatePassed` cannot answer that. GateOrchestrator has two entry points writing the same
     * boolean into the same field: runTaskSpecGate at task CREATION, and runQualityGate when an
     * implementer FINISHES. Both writes are true statements, which is what makes the substitution
     * invisible - it cannot be caught by asking whether the value is correct, only by asking what it is
     * about (ACP-102). Measured on test-forty-ninth: every task in the project has a gate log written
     * 2-5 seconds after creation, so a `done` task with the flag raised may never have been looked at
     * after its work was produced.
     *
     * The subject is already recorded - GateOrchestrator writes the stages into the report - and the
     * 2026-08-18 change that added it said plainly that no reader had been changed to ask. This is that
     * reader. A verdict about specification is not evidence about delivery, and by the Evidence Algebra
     * the absence of a check is 0, never 5.
     */
    /**
     * The payload schema for a delivery ruling. Declared on the entity, not on the service that writes it:
     * the fact lives in this row, and every reader of it - FlowSpineService's counters, this class's own
     * predicates - must agree on one spelling. DeliveredWorkJudgmentService delegates to these.
     */
    public static final String ACCEPTANCE_VERDICT_KEY = "acceptance_verdict";
    public static final String VERDICT_SATISFIED = "SATISFIED";
    public static final String VERDICT_REFUTED = "REFUTED";

    public static final String ACCEPTANCE_VERDICT_REASON_KEY = "acceptance_verdict_reason";

    /**
     * Payload marker for factory carrier tasks (model §II, carrier(τ) ⟺ payload(τ).taskType ≠ ∅).
     * Single point of application (Law 1, |impl(I)| = 1).
     */
    public static final String CARRIER_PAYLOAD_KEY = "taskType";
    public static final String WISHLIST_COMPILER_TASK_TYPE = "wishlist_compiler";

    /**
     * A task the factory created to carry its own process (model §II, carrier(τ) ⟺ payload(τ).taskType ≠ ∅).
     * Single point of implementation (Law 1, |impl(I)| = 1).
     */
    public boolean isCarrier() {
        return payload != null && payload.hasNonNull(CARRIER_PAYLOAD_KEY);
    }

    /** The type of carrier this task is, or null if it is not a factory carrier. */
    public String carrierTaskType() {
        return isCarrier() ? payload.path(CARRIER_PAYLOAD_KEY).asText(null) : null;
    }

    /**
     * A carrier of exactly this type.
     *
     * <p>Law 1 (single point of application): the difference between one kind of carrier and another is an
     * ARGUMENT to this predicate, not a second reading of the payload written out at each caller. Seven
     * callers had written their own copy of "payload is not null and its taskType equals mine"; the
     * structural guard that claims uniqueness banned only the `has`/`hasNonNull` spellings and could not
     * see the `path(...).asText(...)` one, so every copy passed it.
     */
    public boolean isCarrierOfType(String taskType) {
        return taskType != null && taskType.equals(carrierTaskType());
    }

    /** A carrier tasked with compiling wishlists into epic task graphs. */
    public boolean isWishlistCompiler() {
        return isCarrierOfType(WISHLIST_COMPILER_TASK_TYPE);
    }

    /** A carrier performing factory housekeeping/audit duties rather than compiling wishlists. */
    public boolean isHousekeepingCarrier() {
        return isCarrier() && !isWishlistCompiler();
    }

    /**
     * Why the recorded verdict came out as it did, or null when no ground was recorded.
     *
     * <p>Model rule 8.22: an order reissued after a denial must carry that denial's ground. The ground is
     * written next to the verdict when the judgment is recorded, but nothing outside the judgment service
     * could read it, so every repair brief reissued the failed order unchanged - the next agent received
     * the same task, in the same words, knowing exactly what the previous one knew.
     */
    public String acceptanceVerdictReason() {
        if (payload == null) {
            return null;
        }
        String value = payload.path(ACCEPTANCE_VERDICT_REASON_KEY).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /** The verdict recorded for this task, or null if nothing has ruled. */
    public String acceptanceVerdict() {
        if (payload == null) {
            return null;
        }
        String value = payload.path(ACCEPTANCE_VERDICT_KEY).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Did the criterion instrument RULE on this task - as opposed to recording that it could not?
     *
     * <p>UNDECIDABLE and NOT_JUDGED_NO_DIFF are deliberately excluded. They are recorded ignorance, and
     * counting them as answers is exactly the ACP-105 error this file already carries a comment about:
     * a verdict field with no place to put "not measured" starts reporting silence as a result.
     */
    public boolean deliveryRuledByCriteria() {
        String verdict = acceptanceVerdict();
        return VERDICT_SATISFIED.equals(verdict) || VERDICT_REFUTED.equals(verdict);
    }

    /** The merged diff was judged NOT to satisfy this task's own acceptance criteria. */
    public boolean deliveryRefuted() {
        return VERDICT_REFUTED.equals(acceptanceVerdict());
    }

    /**
     * Verified for delivery by EITHER instrument the factory owns.
     *
     * <p>Why a union, measured 2026-08-28 on test-fiftieth over 365 tasks: the gate instrument applied to
     * ZERO of them, while the criterion instrument had ruled on 127 (82 satisfied, 45 refuted). The gate is
     * not weak, it is unreachable - GateOrchestrator.runQualityGate stands on one of the five paths that
     * write TaskStatus.done and covers five of the thirteen roles. Adding gates for the other eight would
     * inspect finished output eight more times; asking each task its own acceptance criteria asks what the
     * client wanted, and every task carries those. Both instruments are kept because each answers where the
     * other is silent.
     */
    public boolean isVerifiedForDelivery() {
        if (VERDICT_SATISFIED.equals(acceptanceVerdict())) {
            return true;
        }
        if (qualityGateReport == null || !qualityGatePassed) {
            return false;
        }
        // The `stages` array records the stages REQUESTED, and runQualityGate always requests all of
        // them - so finding IMPLEMENTATION_RESULT there says it was asked for, never that any check for
        // it applied. Only the per-stage count answers the delivery question (ACP-105): `allMatch` over
        // an empty list returns true, so a task whose role no delivery gate supports was recorded as
        // having passed every applicable check when none was applied.
        return deliveryChecksApplied() > 0;
    }

    /** How many checks of the delivery stage actually applied. Zero means nobody was asked. */
    private int deliveryChecksApplied() {
        if (qualityGateReport == null) {
            return 0;
        }
        return qualityGateReport.path("applicableChecksByStage").path("IMPLEMENTATION_RESULT").asInt(0);
    }

    /**
     * The question WAS put to the criterion instrument and came back without a ruling.
     *
     * <p>This is not the same defect as never being asked, and the two must not be counted as one. Nobody
     * asking means no witness ran at all (model rule 8.15); asking and getting no ruling means the witness
     * ran and could not decide, which points at the criteria rather than at the coverage. Both leave the
     * delivery unverified, so {@link #isDeliveryVerificationAbsent()} still holds for both - but a record
     * that cannot be read correctly is not a record (rule 8.11 O8), and a line reporting both as "never
     * asked" says something false about whichever of them it is.
     */
    public boolean deliveryQuestionPutButUnsettled() {
        String verdict = acceptanceVerdict();
        return verdict != null && !deliveryRuledByCriteria();
    }

    /**
     * True when nothing has ever asked this task's delivery question - neither pass nor fail, by either
     * instrument. A recorded UNDECIDABLE still counts as absent: the question was put and could not be
     * settled, which is not the same as an answer.
     */
    public boolean isDeliveryVerificationAbsent() {
        if (deliveryRuledByCriteria()) {
            return false;
        }
        if (qualityGateReport == null) {
            return true;
        }
        return deliveryChecksApplied() == 0;
    }

    public JsonNode getQualityGateReport() { return qualityGateReport; }
    public void setQualityGateReport(JsonNode qualityGateReport) { this.qualityGateReport = qualityGateReport; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getFileScope() { return fileScope; }
    public void setFileScope(String fileScope) { this.fileScope = fileScope; }
    public TaskEntity getDependsOn() { return dependsOn; }
    public void setDependsOn(TaskEntity dependsOn) { this.dependsOn = dependsOn; }
    public String getCynefinDomain() { return cynefinDomain; }
    public void setCynefinDomain(String cynefinDomain) { this.cynefinDomain = cynefinDomain; }
    public UUID getSourceWishlistId() { return sourceWishlistId; }
    public void setSourceWishlistId(UUID sourceWishlistId) { this.sourceWishlistId = sourceWishlistId; }
    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }
    public UUID getOriginFeatureId() { return originFeatureId; }
    public void setOriginFeatureId(UUID originFeatureId) { this.originFeatureId = originFeatureId; }
    public TargetContext getTargetContext() { return targetContext == null ? TargetContext.PRODUCT_CODEBASE : targetContext; }
    public void setTargetContext(TargetContext targetContext) { this.targetContext = targetContext; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Ordinary saves move the mark too (2026-08-30, plan §4.37) - the same hook JulesSessionEntity has
     * carried all along. The CAS queries in TaskRepository write it themselves, because a bulk JPQL update
     * never reaches this callback.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
