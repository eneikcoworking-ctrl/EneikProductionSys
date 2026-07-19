package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "falsification_runs")
public class FalsificationRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "run_at", nullable = false, updatable = false)
    private Instant runAt = Instant.now();

    @Column(name = "roles_checked_count", nullable = false)
    private int rolesCheckedCount;

    @Column(name = "violations_found_count", nullable = false)
    private int violationsFoundCount;

    @Column(name = "tasks_created_count", nullable = false)
    private int tasksCreatedCount;

    // Lean: without this, every 4h cycle re-fetched and re-audited the same last-5-merged-PR diffs even
    // when nothing new had merged since the previous run - real GitHub API calls and a real Jules session
    // spent auditing code that was already audited. Null means "no prior run" (audit everything found).
    @Column(name = "highest_pr_number_audited")
    private Integer highestPrNumberAudited;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public Instant getRunAt() { return runAt; }
    public void setRunAt(Instant runAt) { this.runAt = runAt; }

    public int getRolesCheckedCount() { return rolesCheckedCount; }
    public void setRolesCheckedCount(int rolesCheckedCount) { this.rolesCheckedCount = rolesCheckedCount; }

    public int getViolationsFoundCount() { return violationsFoundCount; }
    public void setViolationsFoundCount(int violationsFoundCount) { this.violationsFoundCount = violationsFoundCount; }

    public int getTasksCreatedCount() { return tasksCreatedCount; }
    public void setTasksCreatedCount(int tasksCreatedCount) { this.tasksCreatedCount = tasksCreatedCount; }

    public Integer getHighestPrNumberAudited() { return highestPrNumberAudited; }
    public void setHighestPrNumberAudited(Integer highestPrNumberAudited) { this.highestPrNumberAudited = highestPrNumberAudited; }
}
