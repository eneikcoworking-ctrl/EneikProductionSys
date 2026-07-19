package com.eneik.production.dto.dashboard;

import java.util.List;

public record EmsDashboardMetricsDto(
        String generatedAt,
        FlowChart flowChart,
        RoleDoctrineReadiness roleDoctrineReadiness,
        List<RoleKpi> roleKpis,
        DefectWork defectWork,
        GraphHealth graphHealth,
        List<String> rules
) {
    public record FlowChart(
            List<FlowStage> stages,
            long totalTasks,
            double completionRate,
            double weightedProgress
    ) {}

    public record FlowStage(
            String stage,
            String label,
            long total,
            long queued,
            long active,
            long done,
            long blocked,
            double completionRate,
            double weightedScore
    ) {}

    public record RoleDoctrineReadiness(
            List<RoleDoctrineVerdict> roles,
            long rolesEvaluated,
            long satisfied,
            long almostSatisfied,
            long objects,
            long refuses,
            long unknown,
            double readinessScore,
            String statusLabel,
            String interpretation
    ) {}

    public record RoleDoctrineVerdict(
            String roleTag,
            String doctrineName,
            String doctrineFocus,
            String stance,
            double satisfactionScore,
            double confidence,
            String kanoPressure,
            String cynefinBias,
            String topObjection,
            long sourceWishlistPending,
            long sourceWishlistTotal,
            long ownerTasksTotal,
            long ownerTasksOpen,
            long ownerTasksBlocked,
            long ownerTasksDone,
            long defectWork,
            List<String> evidence
    ) {}

    public record RoleKpi(
            String roleTag,
            long total,
            long queued,
            long active,
            long done,
            long blocked,
            long failed,
            long defectWork,
            long retryLoad,
            double completionRate,
            double gatePassRate,
            double defectPressure,
            double flowEfficiency,
            double kpiScore,
            double kpiTarget,
            String statusLabel
    ) {}

    public record DefectWork(
            long totalDefectWork,
            long openDefectWork,
            long blockedTasks,
            long failedTasks,
            long retryLoad,
            double defectPressure,
            double dpmo,
            double dpmoCodeTasksOnly,
            String interpretation
    ) {}

    public record GraphHealth(
            long graphTasks,
            long uniqueGraphs,
            long linkedEdges,
            long blockedByDependency,
            long duplicateSemanticKeys,
            double graphCoverage,
            double dependencyCoverage,
            int criticalPathLength,
            String interpretation
    ) {}
}
