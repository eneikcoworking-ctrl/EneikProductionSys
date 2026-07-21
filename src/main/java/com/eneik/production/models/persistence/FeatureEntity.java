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
}
