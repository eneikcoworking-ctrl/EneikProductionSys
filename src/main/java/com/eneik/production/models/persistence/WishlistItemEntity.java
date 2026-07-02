package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wishlist_items")
public class WishlistItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(nullable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WishlistItemType type = WishlistItemType.client_wish;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WishlistItemStatus status = WishlistItemStatus.open;

    private String sourceRoleTag;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) { this.project = project; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public WishlistItemType getType() { return type; }
    public void setType(WishlistItemType type) { this.type = type; }
    public WishlistItemStatus getStatus() { return status; }
    public void setStatus(WishlistItemStatus status) { this.status = status; }
    public String getSourceRoleTag() { return sourceRoleTag; }
    public void setSourceRoleTag(String sourceRoleTag) { this.sourceRoleTag = sourceRoleTag; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
