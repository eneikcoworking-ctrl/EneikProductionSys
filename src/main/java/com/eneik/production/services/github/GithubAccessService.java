package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class GithubAccessService {

    private final GithubConfig githubConfig;
    private final SystemSettingsService settingsService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GithubAccessService(GithubConfig githubConfig,
                               SystemSettingsService settingsService,
                               JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper) {
        this.githubConfig = githubConfig;
        this.settingsService = settingsService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public record GithubAccessResult(
            UUID id,
            UUID projectId,
            @JsonProperty("has_repo_access") boolean hasRepoAccess,
            @JsonProperty("branch_protection_ok") boolean branchProtectionOk,
            @JsonProperty("pr_permissions_ok") boolean prPermissionsOk,
            @JsonProperty("webhooks_ok") boolean webhooksOk,
            @JsonProperty("ci_status") String ciStatus,
            Instant checkedAt,
            String rawError
    ) {}

    public record GithubSixSigmaDto(
            @JsonProperty("totalChecks") long totalChecks,
            @JsonProperty("totalOpportunities") long totalOpportunities,
            long defects,
            double dpmo
    ) {}

    public GithubAccessResult checkAccess(UUID projectId) {
        String token = settingsService.effectiveValue("github_token");
        if (!settingsService.effectiveBoolean("github_enabled") || token.isBlank()) {
            GithubAccessResult result = new GithubAccessResult(
                    UUID.randomUUID(),
                    projectId,
                    false,
                    false,
                    false,
                    false,
                    "skipped",
                    Instant.now(),
                    "GitHub integration disabled or token missing"
            );
            saveResult(result);
            return result;
        }

        try {
            String repoName = getRepoName(projectId);
            boolean hasRepoAccess = checkRepoAccess(repoName, token);
            boolean branchProtectionOk = hasRepoAccess && checkBranchProtection(repoName, token);
            boolean prPermissionsOk = hasRepoAccess && checkPrPermissions(repoName, token);
            boolean webhooksOk = hasRepoAccess && checkWebhooks(repoName, token);
            String ciStatus = hasRepoAccess ? checkCiStatus(repoName, token) : "no_ci";

            GithubAccessResult result = new GithubAccessResult(
                    UUID.randomUUID(),
                    projectId,
                    hasRepoAccess,
                    branchProtectionOk,
                    prPermissionsOk,
                    webhooksOk,
                    ciStatus,
                    Instant.now(),
                    null
            );
            saveResult(result);
            return result;
        } catch (Exception e) {
            GithubAccessResult errorResult = new GithubAccessResult(
                    UUID.randomUUID(),
                    projectId,
                    false,
                    false,
                    false,
                    false,
                    "failing",
                    Instant.now(),
                    e.getMessage()
            );
            saveResult(errorResult);
            return errorResult;
        }
    }

    private void saveResult(GithubAccessResult result) {
        jdbcTemplate.update(
                "INSERT INTO github_access_status (id, project_id, has_repo_access, branch_protection_ok, pr_permissions_ok, webhooks_ok, ci_status, checked_at, raw_error) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                result.id(), result.projectId(), result.hasRepoAccess(), result.branchProtectionOk(), result.prPermissionsOk(), result.webhooksOk(), result.ciStatus(), result.checkedAt(), result.rawError()
        );
    }

    public GithubAccessResult getLatestResult(UUID projectId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM github_access_status WHERE project_id = ? ORDER BY checked_at DESC LIMIT 1",
                    (rs, rowNum) -> new GithubAccessResult(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("project_id")),
                            rs.getBoolean("has_repo_access"),
                            rs.getBoolean("branch_protection_ok"),
                            rs.getBoolean("pr_permissions_ok"),
                            rs.getBoolean("webhooks_ok"),
                            rs.getString("ci_status"),
                            rs.getTimestamp("checked_at").toInstant(),
                            rs.getString("raw_error")
                    ),
                    projectId
            );
        } catch (Exception e) {
            return checkAccess(projectId);
        }
    }

    public GithubSixSigmaDto calculateDefectRate(Instant since) {
        String sql = "SELECT * FROM github_access_status WHERE checked_at >= ?";
        var results = jdbcTemplate.query(sql, (rs, rowNum) -> new GithubAccessResult(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("project_id")),
                rs.getBoolean("has_repo_access"),
                rs.getBoolean("branch_protection_ok"),
                rs.getBoolean("pr_permissions_ok"),
                rs.getBoolean("webhooks_ok"),
                rs.getString("ci_status"),
                rs.getTimestamp("checked_at").toInstant(),
                rs.getString("raw_error")
        ), since != null ? since : Instant.EPOCH);

        long totalChecks = results.size();
        long totalOpportunities = totalChecks * 5;
        long defects = 0;

        for (var res : results) {
            if (!res.hasRepoAccess()) defects++;
            if (!res.branchProtectionOk()) defects++;
            if (!res.prPermissionsOk()) defects++;
            if (!res.webhooksOk()) defects++;
            if (!"passing".equals(res.ciStatus())) defects++;
        }

        double dpmo = totalOpportunities == 0 ? 0 : (double) defects / totalOpportunities * 1_000_000;

        return new GithubSixSigmaDto(totalChecks, totalOpportunities, defects, dpmo);
    }

    private String getRepoName(UUID projectId) {
        return jdbcTemplate.queryForObject("SELECT repository_name FROM projects WHERE id = ?", String.class, projectId);
    }

    private boolean checkRepoAccess(String repoName, String token) {
        try {
            HttpRequest request = baseRequest("/repos/" + encode(githubConfig.getOrganization()) + "/" + encode(repoName), token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkBranchProtection(String repoName, String token) {
        try {
            HttpRequest request = baseRequest("/repos/" + encode(githubConfig.getOrganization()) + "/" + encode(repoName) + "/branches/main/protection", token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;

            JsonNode json = objectMapper.readTree(response.body());
            boolean reviewsOk = json.has("required_pull_request_reviews");
            boolean statusChecksOk = json.has("required_status_checks");

            return reviewsOk && statusChecksOk;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkPrPermissions(String repoName, String token) {
        try {
            HttpRequest request = baseRequest("/repos/" + encode(githubConfig.getOrganization()) + "/" + encode(repoName), token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;

            JsonNode permissions = objectMapper.readTree(response.body()).path("permissions");
            return permissions.path("push").asBoolean(false) || permissions.path("admin").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkWebhooks(String repoName, String token) {
        try {
            HttpRequest request = baseRequest("/repos/" + encode(githubConfig.getOrganization()) + "/" + encode(repoName) + "/hooks", token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;

            JsonNode hooks = objectMapper.readTree(response.body());
            String targetUrl = githubConfig.getWebhookUrl();
            if (targetUrl == null || targetUrl.isBlank()) return false;

            for (JsonNode hook : hooks) {
                String hookUrl = hook.path("config").path("url").asText();
                if (targetUrl.equals(hookUrl)) {
                    JsonNode events = hook.path("events");
                    boolean hasPush = false;
                    boolean hasPr = false;
                    for (JsonNode event : events) {
                        if ("push".equals(event.asText())) hasPush = true;
                        if ("pull_request".equals(event.asText())) hasPr = true;
                    }
                    if (hasPush && hasPr && hook.path("active").asBoolean()) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String checkCiStatus(String repoName, String token) {
        try {
            HttpRequest request = baseRequest("/repos/" + encode(githubConfig.getOrganization()) + "/" + encode(repoName) + "/commits/main/check-runs", token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return checkActionsWorkflowStatus(repoName, token);

            JsonNode json = objectMapper.readTree(response.body());
            int totalCount = json.path("total_count").asInt();
            if (totalCount == 0) return checkActionsWorkflowStatus(repoName, token);

            JsonNode checkRuns = json.path("check_runs");
            boolean allSuccess = true;
            boolean anyInProgress = false;

            for (JsonNode run : checkRuns) {
                String status = run.path("status").asText();
                String conclusion = run.path("conclusion").asText();

                if (!"completed".equals(status)) {
                    anyInProgress = true;
                    continue;
                }
                if (!"success".equals(conclusion) && !"neutral".equals(conclusion)) {
                    allSuccess = false;
                }
            }

            if (anyInProgress) return "skipped";
            return allSuccess ? "passing" : "failing";
        } catch (Exception e) {
            return checkActionsWorkflowStatus(repoName, token);
        }
    }

    private String checkActionsWorkflowStatus(String repoName, String token) {
        try {
            HttpRequest request = baseRequest("/repos/" + encode(githubConfig.getOrganization()) + "/" + encode(repoName) + "/actions/runs?branch=main&per_page=10", token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return "no_ci";

            JsonNode runs = objectMapper.readTree(response.body()).path("workflow_runs");
            if (!runs.isArray() || runs.isEmpty()) return "no_ci";

            boolean anyInProgress = false;
            for (JsonNode run : runs) {
                String status = run.path("status").asText();
                String conclusion = run.path("conclusion").asText();
                if (!"completed".equals(status)) {
                    anyInProgress = true;
                    continue;
                }
                return "success".equals(conclusion) || "neutral".equals(conclusion) ? "passing" : "failing";
            }
            return anyInProgress ? "skipped" : "no_ci";
        } catch (Exception e) {
            return "no_ci";
        }
    }

    private HttpRequest.Builder baseRequest(String path, String token) {
        return HttpRequest.newBuilder(URI.create(githubConfig.getApiBaseUrl().replaceAll("/+$", "") + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
