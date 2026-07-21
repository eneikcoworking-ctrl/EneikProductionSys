package com.eneik.production.dto.dashboard;

import java.util.List;

// Operator directive (2026-07-21): the raw GitHub PR list (GitHubPullRequestService.pullRequestSnapshot)
// mixes real feature work with system/carrier PRs (compiler, falsification-audit, review-fallback, design
// review, coverage-audit) one-for-one - confirmed live, e.g. "#22 Design review verdict for mockup
// 21c4c900" sitting next to real feature PRs with no way to tell them apart. This is the frontend-facing
// shape: only PRs traced back to a real (non-carrier) task, each carrying a clear featureName (the эпик's
// own title, or a fallback) alongside whatever raw title Jules gave the PR - see
// ProjectFlowService.featurePullRequests.
public record FeaturePullRequestSnapshotDto(
        boolean available,
        List<FeaturePullRequestDto> open,
        List<FeaturePullRequestDto> closed,
        String error
) {
    public record FeaturePullRequestDto(
            String url,
            int number,
            String title,
            String featureName,
            String author,
            boolean merged
    ) {}
}
