package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project_final_reports")
public class ProjectFinalReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "total_tasks_completed")
    private Integer totalTasksCompleted;

    @Column(name = "total_wishlist_items")
    private Integer totalWishlistItems;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_content")
    private JsonNode reportContent;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public Integer getTotalTasksCompleted() { return totalTasksCompleted; }
    public void setTotalTasksCompleted(Integer totalTasksCompleted) { this.totalTasksCompleted = totalTasksCompleted; }
    public Integer getTotalWishlistItems() { return totalWishlistItems; }
    public void setTotalWishlistItems(Integer totalWishlistItems) { this.totalWishlistItems = totalWishlistItems; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public JsonNode getReportContent() { return reportContent; }
    public void setReportContent(JsonNode reportContent) { this.reportContent = reportContent; }
}
