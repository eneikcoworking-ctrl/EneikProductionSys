package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wishlist")
public class WishlistEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WishlistSource source;

    @Column(name = "source_role_tag")
    private String sourceRoleTag;

    @Column(nullable = false)
    private String content;

    // 2026-08-07 (RequirementGroundingService): same text as `content`, with real established mathematical/
    // philosophical patterns (idempotency, atomicity, etc.) attached wherever a genuine match exists -
    // never a replacement for the client's own wording, computed lazily and cached on first compilation.
    // Null until grounding has actually run (or for non-client wishlist sources, which skip grounding).
    @Column(name = "grounded_content")
    private String groundedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WishlistStatus status = WishlistStatus.pending;

    private String jtbd;

    @Enumerated(EnumType.STRING)
    @Column(name = "lean_value")
    private LeanValue leanValue;

    @Column(name = "toc_constraint_ref")
    private String tocConstraintRef;

    @Column(name = "six_sigma_metric")
    private String sixSigmaMetric;

    private String dod;

    @Column(name = "acceptance_criteria")
    private String acceptanceCriteria;

    @Column(name = "compiled_by_role")
    private String compiledByRole;

    // The compiler's own real classification for this slice (TaskSliceMetadata.cynefinDomain), persisted
    // so TechnicalLeadCompiler.cynefinDomain(wishlist) can use real, already-decided data instead of
    // re-deriving it via a keyword search over the full replicated brief text - see that method's fix
    // commit (2026-08-03) for the live incident this closes (the word "researchers" in an unrelated design-
    // system note false-triggered "complex"/spike classification for every task in the project, since every
    // task's description embeds the whole brief, not just its own slice).
    @Column(name = "cynefin_domain")
    private String cynefinDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_context")
    private TargetContext targetContext = TargetContext.PRODUCT_CODEBASE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // The feature this item belongs to (see FeatureEntity/FeatureService) - null until this item is
    // actually compiled into a task for the first time, or inherited from an originating item/task for
    // follow-up work (design concerns, circuit-breaker recovery, role-mismatch follow-ups).
    @Column(name = "feature_id")
    private UUID featureId;

    // 2026-08-04 (3-layer Factory/Delivery/Product model): immutable lineage, set once alongside
    // featureId the first time this item is stamped (compile or inheritance), never rewritten again -
    // same reasoning as TaskEntity.originFeatureId. Lets Layer 2 delivery history (including dismissed/
    // duplicate/superseded wishlist items, e.g. philosophical falsification critiques) trace back to
    // where they came from even after featureId itself gets repointed.
    @Column(name = "origin_feature_id")
    private UUID originFeatureId;

    // 2026-08-07 (Gricean quantity-optimal grounding, ACP-101): immutable lineage back to the original
    // client-authored wishlist this row was compiler-sliced from - null for the root wishlist itself (and
    // for non-sliced sources). Lets TechnicalLeadCompiler.buildTaskDescription retrieve only the excerpt
    // of the root brief relevant to THIS slice's own JTBD, instead of either duplicating the whole root
    // brief into every slice's own content or truncating it at an arbitrary character count.
    @Column(name = "origin_wishlist_id")
    private UUID originWishlistId;

    /**
     * How many times decomposition has been ATTEMPTED for this brief (F42, 2026-08-16).
     *
     * The cooldown beside it bounds how often an attempt may be made; only this bounds how many. With a
     * declared budget B, mu = B - compileAttempts is a natural number that strictly decreases on every
     * attempt that fails to produce a slice, so the loop cannot run forever. Before this column there was
     * no decreasing quantity anywhere in the compile loop, which is why one brief was decomposed 16 times.
     */
    @Column(name = "compile_attempts", nullable = false)
    private int compileAttempts = 0;

    // 2026-08-13 (live incident, test-forty-fourth): a per-wishlist dispatch cooldown, same pattern as
    // ProjectFlowService.recordOrchestrationStartOrThrow/ORCHESTRATION_COOLDOWN_SECONDS but scoped to this
    // one wishlist instead of the whole project. Without it, a wishlist bounced back to `pending` by ANY
    // means (manual claim release, a retry, a bug) gets a brand-new real Jules session opened for it on the
    // very next orchestration cycle, with nothing remembering that the same content was just dispatched
    // moments ago - confirmed live: releasing the same 3-wishlist batch repeatedly while orchestration kept
    // running opened several real duplicate "Compile 3 Wishlist" sessions against the daily quota. Recorded
    // at dispatch ATTEMPT time (not just success), so even a failed/blocked attempt still closes the window.
    @Column(name = "last_compile_dispatched_at")
    private Instant lastCompileDispatchedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public WishlistSource getSource() {
        return source;
    }

    public void setSource(WishlistSource source) {
        this.source = source;
    }

    public String getSourceRoleTag() {
        return sourceRoleTag;
    }

    public void setSourceRoleTag(String sourceRoleTag) {
        this.sourceRoleTag = sourceRoleTag;
    }

    /**
     * How many compile attempts one brief gets before decomposition is declared failed for it (F42).
     * Declared here rather than in ProjectFlowService because three separate places must agree on what
     * "this brief can still move" means, and Charter invariant 10 says a shared invariant gets one point
     * of application.
     */
    public static final int COMPILE_ATTEMPT_BUDGET = 3;

    /**
     * True when this brief can never again produce slices: its budget is spent and, by F42,
     * ProjectFlowService will dispatch no further compile for it.
     *
     * <p>Charter invariant 8 - "an element that can structurally never reach done must be excluded from
     * the denominator, or the metric sticks below 100% forever and any code deciding on that metric
     * blocks silently with no explanation". That is exactly what happened here, measured 2026-08-28 on
     * test-fiftieth: six briefs had spent their budget, F42 deliberately left their status at `pending`
     * on the reasoning that "no downstream consumer changes behaviour", and two consumers did -
     * FlowSpineService counts `pending` into `pendingWishlist`, and ClientDeliverableReadinessService
     * requires every root to be `converted_to_task` or `dismissed`. Either alone pins the project in
     * DECOMPOSING permanently, which denies RECOVER_FAILED_FRONTIER and holds 27 queued tasks behind a
     * phase that can never end.
     *
     * <p>Charter invariant 3 also applies: budget exhaustion is an absorbing state - nothing can leave it
     * - and it was written as a non-absorbing status. This predicate makes the absorbing fact readable
     * without changing the status, so F42's own reason for leaving it alone is preserved.
     */
    public boolean decompositionExhausted() {
        return compileAttempts >= COMPILE_ATTEMPT_BUDGET;
    }

    /**
     * When a compile message for this brief actually reached the compiler - not when one was attempted.
     *
     * <p>The distinction is the whole point (Charter invariant 12, independent verification rather than
     * self-report). {@code compileAttempts} is the dispatcher's own testimony that it tried; this column
     * is the channel's record that the brief was genuinely put to the compiler. Measured 2026-08-28: six
     * briefs on test-fiftieth spent their entire budget while the single persistent compiler worker was
     * busy, so nothing was ever sent, and the factory nonetheless concluded "the brief needs a human
     * reading" - a claim about the brief drawn from evidence about the factory.
     */
    /**
     * The factory task this brief was filed about, when it was filed about one (V116, §9).
     *
     * <p>Exists so that the identifier can stop travelling inside the brief's prose. It is the factory's
     * own bookkeeping and is never rendered into text an agent in the client's zone reads - that leak is
     * what turned one missing delivery into seven review sessions on 2026-08-28.
     */
    @Column(name = "source_task_id")
    private UUID sourceTaskId;

    public UUID getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(UUID sourceTaskId) { this.sourceTaskId = sourceTaskId; }

    @Column(name = "last_compile_reached_at")
    private Instant lastCompileReachedAt;

    public Instant getLastCompileReachedAt() { return lastCompileReachedAt; }
    public void setLastCompileReachedAt(Instant lastCompileReachedAt) { this.lastCompileReachedAt = lastCompileReachedAt; }

    /**
     * The budget is spent AND the compiler was reached at least once: asked and gave nothing back.
     *
     * <p>This is a statement about the BRIEF, and it is absorbing - nothing further will be dispatched.
     * Charter invariant 8 therefore applies: it must leave the denominators that decide flow state, or
     * the project stays in DECOMPOSING forever with no explanation.
     */
    public boolean decompositionRefused() {
        return decompositionExhausted() && lastCompileReachedAt != null;
    }

    /**
     * The budget is spent and the compiler was NEVER reached: nothing whatever is established about the
     * brief, only about the factory's own availability.
     *
     * <p>Deliberately NOT absorbing, and deliberately NOT excluded from any denominator: a brief that was
     * never read can still reach done, so invariant 8's exclusion would be a lie in the other direction.
     * It is restored instead - see ProjectFlowService.restoreUnreachedBriefs and the monotone watermark
     * it stands on (invariant 7).
     */
    /**
     * Can this brief still move toward a task graph? One definition, asked by every consumer
     * (Charter invariant 10).
     *
     * <p>Before this existed, ten sites independently counted `pending`/`compiling`, and a brief the
     * compiler had answered nothing for stayed in every one of those counts forever - blocking feature
     * completion, feature dismissal, and housekeeping dispatch with no explanation (invariant 8).
     */
    public boolean movable() {
        return (status == WishlistStatus.pending
                || status == WishlistStatus.compiling
                || status == WishlistStatus.finalizing)
                && !decompositionRefused();
    }

    public boolean decompositionUnreached() {
        return decompositionExhausted() && lastCompileReachedAt == null;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getGroundedContent() {
        return groundedContent;
    }

    public void setGroundedContent(String groundedContent) {
        this.groundedContent = groundedContent;
    }

    public WishlistStatus getStatus() {
        return status;
    }

    public void setStatus(WishlistStatus status) {
        this.status = status;
    }

    public String getJtbd() {
        return jtbd;
    }

    public void setJtbd(String jtbd) {
        this.jtbd = jtbd;
    }

    public LeanValue getLeanValue() {
        return leanValue;
    }

    public void setLeanValue(LeanValue leanValue) {
        this.leanValue = leanValue;
    }

    public String getTocConstraintRef() {
        return tocConstraintRef;
    }

    public void setTocConstraintRef(String tocConstraintRef) {
        this.tocConstraintRef = tocConstraintRef;
    }

    public String getSixSigmaMetric() {
        return sixSigmaMetric;
    }

    public void setSixSigmaMetric(String sixSigmaMetric) {
        this.sixSigmaMetric = sixSigmaMetric;
    }

    public String getDod() {
        return dod;
    }

    public void setDod(String dod) {
        this.dod = dod;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(String acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public String getCompiledByRole() {
        return compiledByRole;
    }

    public void setCompiledByRole(String compiledByRole) {
        this.compiledByRole = compiledByRole;
    }

    public String getCynefinDomain() {
        return cynefinDomain;
    }

    public void setCynefinDomain(String cynefinDomain) {
        this.cynefinDomain = cynefinDomain;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getFeatureId() {
        return featureId;
    }

    public void setFeatureId(UUID featureId) {
        this.featureId = featureId;
    }

    public UUID getOriginFeatureId() {
        return originFeatureId;
    }

    public void setOriginFeatureId(UUID originFeatureId) {
        this.originFeatureId = originFeatureId;
    }

    public int getCompileAttempts() { return compileAttempts; }
    public void setCompileAttempts(int compileAttempts) { this.compileAttempts = compileAttempts; }

    public UUID getOriginWishlistId() {
        return originWishlistId;
    }

    public void setOriginWishlistId(UUID originWishlistId) {
        this.originWishlistId = originWishlistId;
    }

    public TargetContext getTargetContext() {
        return targetContext == null ? TargetContext.PRODUCT_CODEBASE : targetContext;
    }

    public void setTargetContext(TargetContext targetContext) {
        this.targetContext = targetContext;
    }

    public Instant getLastCompileDispatchedAt() {
        return lastCompileDispatchedAt;
    }

    public void setLastCompileDispatchedAt(Instant lastCompileDispatchedAt) {
        this.lastCompileDispatchedAt = lastCompileDispatchedAt;
    }
}
