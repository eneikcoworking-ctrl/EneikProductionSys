package com.eneik.production.models.persistence;

import java.io.Serializable;
import java.util.UUID;

public class ProjectHotspotFileId implements Serializable {
    private UUID projectId;
    private String filePath;

    // default constructor
    public ProjectHotspotFileId() {}

    public ProjectHotspotFileId(UUID projectId, String filePath) {
        this.projectId = projectId;
        this.filePath = filePath;
    }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectHotspotFileId that = (ProjectHotspotFileId) o;
        return projectId.equals(that.projectId) && filePath.equals(that.filePath);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(projectId, filePath);
    }
}
