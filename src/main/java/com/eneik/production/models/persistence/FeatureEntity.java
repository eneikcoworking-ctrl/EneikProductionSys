package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "features")
public class FeatureEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "root_wishlist_id")
    private UUID rootWishlistId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Ф8 (2026-07-21, operator directive): an эпик used to be a bare grouping row - JTBD/Kano/Cynefin all
    // lived on the task/slice level instead, with no way to identify or match against an epic's own
    // content. A wishlist now splits into as many epics as the product needs (by narrative/theme, decided
    // semantically by the compiler against this content on every later compile cycle too), each carrying
    // its own customer-facing JTBD/Kano/Cynefin - the task's own jtbd is scoped to the epic instead
    // (see TaskSliceMetadata). sixSigmaMetric/tocConstraintRef live at BOTH levels (operator decision):
    // this is the epic's aggregate business metric, tasks keep their own technical one.
    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String jtbd;

    @Column(name = "kano_class", length = 50)
    private String kanoClass;

    @Column(name = "cynefin_domain", length = 32)
    private String cynefinDomain;

    @Column(name = "six_sigma_metric", columnDefinition = "TEXT")
    private String sixSigmaMetric;

    @Column(name = "toc_constraint_ref", columnDefinition = "TEXT")
    private String tocConstraintRef;

    // 2026-08-04 (3-layer Factory/Delivery/Product model, operator directive): the immutable lineage id
    // this feature was originally created under - set once at creation (defaults to this row's own id,
    // i.e. a brand-new feature is its own origin) and never rewritten afterward by anything, unlike
    // featureId on Task/Wishlist which is freely reassigned during dedup/merge/repair. Layer 2 "Delivery"
    // history is grouped by this field so a feature that gets dismissed/superseded still traces back to
    // where it came from, instead of vanishing the way deleteValuelessEpicsForProject used to make rows
    // disappear entirely.
    @Column(name = "origin_feature_id")
    private UUID originFeatureId;

    // 2026-08-04: soft-delete marker replacing the old hard featureRepository.deleteById call in
    // deleteValuelessEpicsForProject - a dismissed feature's row (and its originFeatureId lineage) now
    // survives; only its "counts toward active readiness" status changes. Null = still active.
    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    // Union-find canonical-reference pointer (2026-08-07, engineering invariant #13: rigid designation +
    // substitutivity salva veritate - see docs/ENGINEERING_INVARIANTS_CHARTER.md). NULL means this row is
    // its own canonical representative. Set exactly once, by ClientDeliverableReadinessService.
    // unionDuplicateFeature, when this row is recognized as a duplicate of another - and never re-pointed
    // afterward, unlike originFeatureId (immutable lineage from creation) or the dismissedAt-driven
    // deduplicateFeaturesByTitle tie-break this field replaces, which recomputed "the winner" fresh on
    // every call and could silently disagree with itself between consumers.
    @Column(name = "canonical_feature_id")
    private UUID canonicalFeatureId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getRootWishlistId() { return rootWishlistId; }
    public void setRootWishlistId(UUID rootWishlistId) { this.rootWishlistId = rootWishlistId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getJtbd() { return jtbd; }
    public void setJtbd(String jtbd) { this.jtbd = jtbd; }
    public String getKanoClass() { return kanoClass; }
    public void setKanoClass(String kanoClass) { this.kanoClass = kanoClass; }
    public String getCynefinDomain() { return cynefinDomain; }
    public void setCynefinDomain(String cynefinDomain) { this.cynefinDomain = cynefinDomain; }
    public String getSixSigmaMetric() { return sixSigmaMetric; }
    public void setSixSigmaMetric(String sixSigmaMetric) { this.sixSigmaMetric = sixSigmaMetric; }
    public String getTocConstraintRef() { return tocConstraintRef; }
    public void setTocConstraintRef(String tocConstraintRef) { this.tocConstraintRef = tocConstraintRef; }
    public UUID getOriginFeatureId() { return originFeatureId; }
    public void setOriginFeatureId(UUID originFeatureId) { this.originFeatureId = originFeatureId; }
    public Instant getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(Instant dismissedAt) { this.dismissedAt = dismissedAt; }
    public UUID getCanonicalFeatureId() { return canonicalFeatureId; }
    public void setCanonicalFeatureId(UUID canonicalFeatureId) { this.canonicalFeatureId = canonicalFeatureId; }
}
