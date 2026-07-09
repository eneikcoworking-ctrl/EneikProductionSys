package com.eneik.production.services.onboarding;

public record StackProfile(
    String primaryLanguage,
    String framework,
    String database,
    boolean hasCI,
    boolean hasTests,
    boolean isMonorepo,
    String declaredPurpose,
    String defaultBranch,
    String baselineCommitSha,
    int totalFiles,
    int analyzedFiles
) {}
