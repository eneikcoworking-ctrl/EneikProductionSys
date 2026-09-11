package com.eneik.production.services.onboarding;

public record StackProfile(
    String primaryLanguage,
    String framework,
    String database,
    InspectionStatus hasCI,
    InspectionStatus hasTests,
    InspectionStatus isMonorepo,
    String declaredPurpose,
    String defaultBranch,
    String baselineCommitSha,
    int totalFiles,
    int analyzedFiles
) {
    public static StackProfile unchecked(String declaredPurpose, String defaultBranch, String baselineCommitSha) {
        return new StackProfile(
                "Unknown",
                "не проверено",
                "не проверено",
                InspectionStatus.UNCHECKED,
                InspectionStatus.UNCHECKED,
                InspectionStatus.UNCHECKED,
                declaredPurpose,
                defaultBranch,
                baselineCommitSha,
                0,
                0
        );
    }

    public boolean isUnchecked() {
        return hasCI == InspectionStatus.UNCHECKED
                || hasTests == InspectionStatus.UNCHECKED
                || isMonorepo == InspectionStatus.UNCHECKED;
    }
}
