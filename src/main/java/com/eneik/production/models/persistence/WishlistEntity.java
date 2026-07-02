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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
