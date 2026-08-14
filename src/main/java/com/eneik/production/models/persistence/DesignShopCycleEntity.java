package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per project: tracks the design shop's edge-triggered readiness state (see
 * DesignShopOrchestrationService) so a project that has been "ready to assemble" for many ticks in a
 * row only starts one design cycle, not one per tick - while still allowing a brand new cycle once
 * readiness genuinely drops and rises again (e.g. after a falsification round adds new features).
 */
@Entity
@Table(name = "design_shop_cycles")
public class DesignShopCycleEntity {
    public static final String STAGE_IDLE = "IDLE";
    public static final String STAGE_AWAITING_REVIEW = "AWAITING_REVIEW";
    public static final String STAGE_DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "last_was_ready", nullable = false)
    private boolean lastWasReady;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage = STAGE_IDLE;

    @Column(name = "draft_path", length = 512)
    private String draftPath;

    // Bootstrap baseline (2026-08-11, closing the design shop's E(f) gap): captured from this
    // project's FIRST Stitch generation, never invented - see DesignShopOrchestrationService.startCycle.
    // Once set, every later generation for this project passes them back to generateAsset so the E(f)
    // audit compares against this project's own real brand, not a stand-in.
    @Column(name = "stitch_project_id", length = 64)
    private String stitchProjectId;

    @Column(name = "stitch_screen_id", length = 64)
    private String stitchScreenId;

    @Column(name = "declared_colors", length = 1024)
    private String declaredColors;

    @Column(name = "declared_fonts", length = 512)
    private String declaredFonts;

    // Concern-triage self-falsification loop (edit_screens) bound - never unlimited, see the
    // idle_generation removal precedent (WishlistSource's own comment: a system that keeps inventing
    // its own follow-up work without bound was judged dangerous by the operator).
    @Column(name = "edit_iteration_count", nullable = false)
    private int editIterationCount;

    // 2026-08-14 (bug-hunt sweep, V98 migration): atomic mutual-exclusion claim for
    // DesignShopOrchestrationService.startCycle - deliberately separate from lastWasReady itself, which is
    // only set true AFTER a successful Stitch generation (so a failed attempt correctly retries next
    // tick). NULL means no attempt is currently in flight for this project.
    @Column(name = "start_cycle_claimed_at")
    private Instant startCycleClaimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public boolean isLastWasReady() { return lastWasReady; }
    public void setLastWasReady(boolean lastWasReady) { this.lastWasReady = lastWasReady; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getDraftPath() { return draftPath; }
    public void setDraftPath(String draftPath) { this.draftPath = draftPath; }

    public String getStitchProjectId() { return stitchProjectId; }
    public void setStitchProjectId(String stitchProjectId) { this.stitchProjectId = stitchProjectId; }

    public String getStitchScreenId() { return stitchScreenId; }
    public void setStitchScreenId(String stitchScreenId) { this.stitchScreenId = stitchScreenId; }

    public String getDeclaredColors() { return declaredColors; }
    public void setDeclaredColors(String declaredColors) { this.declaredColors = declaredColors; }

    public String getDeclaredFonts() { return declaredFonts; }
    public void setDeclaredFonts(String declaredFonts) { this.declaredFonts = declaredFonts; }

    public java.util.List<String> declaredColorsList() {
        return declaredColors == null || declaredColors.isBlank() ? java.util.List.of() : java.util.List.of(declaredColors.split(","));
    }

    public java.util.List<String> declaredFontsList() {
        return declaredFonts == null || declaredFonts.isBlank() ? java.util.List.of() : java.util.List.of(declaredFonts.split(","));
    }

    public int getEditIterationCount() { return editIterationCount; }
    public void setEditIterationCount(int editIterationCount) { this.editIterationCount = editIterationCount; }

    public Instant getStartCycleClaimedAt() { return startCycleClaimedAt; }
    public void setStartCycleClaimedAt(Instant startCycleClaimedAt) { this.startCycleClaimedAt = startCycleClaimedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
