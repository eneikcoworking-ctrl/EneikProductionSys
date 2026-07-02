package com.eneik.production.dto.monitor;

import java.util.List;

public class PrDataDto {
    private int linesChanged;
    private int filesChanged;
    private boolean hasTestChanges;
    private String ciStatus;
    private List<String> changedFiles;
    private String diffSummary;

    public int getLinesChanged() { return linesChanged; }
    public void setLinesChanged(int linesChanged) { this.linesChanged = linesChanged; }
    public int getFilesChanged() { return filesChanged; }
    public void setFilesChanged(int filesChanged) { this.filesChanged = filesChanged; }
    public boolean isHasTestChanges() { return hasTestChanges; }
    public void setHasTestChanges(boolean hasTestChanges) { this.hasTestChanges = hasTestChanges; }
    public String getCiStatus() { return ciStatus; }
    public void setCiStatus(String ciStatus) { this.ciStatus = ciStatus; }
    public List<String> getChangedFiles() { return changedFiles; }
    public void setChangedFiles(List<String> changedFiles) { this.changedFiles = changedFiles; }
    public String getDiffSummary() { return diffSummary; }
    public void setDiffSummary(String diffSummary) { this.diffSummary = diffSummary; }
}
