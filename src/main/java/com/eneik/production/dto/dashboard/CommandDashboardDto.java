package com.eneik.production.dto.dashboard;

import java.util.List;
import java.util.Map;

public record CommandDashboardDto(
    List<Map<String, Object>> wishlist,
    List<Map<String, Object>> tasks,
    List<Map<String, Object>> julesSessions,
    List<Map<String, Object>> prReviews,
    List<Map<String, Object>> githubAccessStatus,
    List<Map<String, Object>> linearIssueMetadata,
    AcceptanceReadinessDto acceptanceReadiness,
    Map<String, String> dataSourcesStatus
) {}
