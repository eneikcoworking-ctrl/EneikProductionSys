package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GitHubPullRequestService {
    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestService.class);

    private final GithubConfig githubConfig;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubPullRequestService(GithubConfig githubConfig,
                                    SystemSettingsService settingsService,
                                    ObjectMapper objectMapper) {
        this.githubConfig = githubConfig;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public Optional<GitHubPullRequest> findOpenPullRequestBySession(ProjectEntity project, String externalSessionId) {
        String sessionToken = sessionToken(externalSessionId);
        if (project == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        PullRequestSnapshot snapshot = pullRequestSnapshot(project);
        if (!snapshot.available()) {
            return Optional.empty();
        }

        for (GitHubPullRequest pr : snapshot.open()) {
            if (pr.headRef().contains(sessionToken)) {
                return Optional.of(pr);
            }
        }
        return Optional.empty();
    }

    public PullRequestSnapshot pullRequestSnapshot(ProjectEntity project) {
        if (project == null) {
            return PullRequestSnapshot.unavailable("", "", "Project is not selected");
        }
        if (!settingsService.effectiveBoolean("github_enabled")) {
            RepoRef repoRef = repoRef(project);
            return PullRequestSnapshot.unavailable(repoRef.owner(), repoRef.repo(), "GitHub integration is disabled");
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            RepoRef repoRef = repoRef(project);
            return PullRequestSnapshot.unavailable(repoRef.owner(), repoRef.repo(), "GitHub token is missing");
        }

        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return PullRequestSnapshot.unavailable(repoRef.owner(), repoRef.repo(), "Repository owner/name is missing");
        }

        try {
            return new PullRequestSnapshot(
                    true,
                    repoRef.owner(),
                    repoRef.repo(),
                    fetchPullRequests(repoRef, "open", token),
                    fetchPullRequests(repoRef, "closed", token),
                    ""
            );
        } catch (Exception e) {
            log.warn("Could not build GitHub PR snapshot for {}/{}: {}", repoRef.owner(), repoRef.repo(), e.getMessage());
            return PullRequestSnapshot.unavailable(repoRef.owner(), repoRef.repo(), e.getMessage());
        }
    }

    public PullRequestCloseReport closeOpenPullRequests(ProjectEntity project, String reason) {
        if (project == null) {
            return PullRequestCloseReport.unavailable("", "", "Project is not selected");
        }
        if (!settingsService.effectiveBoolean("github_enabled")) {
            RepoRef repoRef = repoRef(project);
            return PullRequestCloseReport.unavailable(repoRef.owner(), repoRef.repo(), "GitHub integration is disabled");
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            RepoRef repoRef = repoRef(project);
            return PullRequestCloseReport.unavailable(repoRef.owner(), repoRef.repo(), "GitHub token is missing");
        }

        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return PullRequestCloseReport.unavailable(repoRef.owner(), repoRef.repo(), "Repository owner/name is missing");
        }

        try {
            List<GitHubPullRequest> openPullRequests = fetchPullRequests(repoRef, "open", token);
            List<PullRequestCloseResult> results = new ArrayList<>();
            for (GitHubPullRequest pullRequest : openPullRequests) {
                results.add(closePullRequest(repoRef, pullRequest, token, reason));
            }
            long closed = results.stream().filter(result -> "closed".equals(result.status())).count();
            return new PullRequestCloseReport(
                    true,
                    repoRef.owner(),
                    repoRef.repo(),
                    openPullRequests.size(),
                    closed,
                    results,
                    ""
            );
        } catch (Exception e) {
            log.warn("Could not close GitHub PRs for {}/{}: {}", repoRef.owner(), repoRef.repo(), e.getMessage());
            return PullRequestCloseReport.unavailable(repoRef.owner(), repoRef.repo(), e.getMessage());
        }
    }

    /**
     * Fetches a single file's raw text content from a branch/ref via the GitHub Contents API. Used to
     * read back the JSON task-plan file a Jules wishlist-compiler session writes into its PR branch,
     * since Jules sessions communicate their structured result as a committed file, not a direct reply.
     */
    public Optional<String> fetchFileContent(ProjectEntity project, String ref, String path) {
        if (project == null || ref == null || ref.isBlank() || path == null || path.isBlank()) {
            return Optional.empty();
        }
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return Optional.empty();
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return Optional.empty();
        }

        try {
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/contents/" + encodePath(path) + "?ref=" + encode(ref);
            HttpRequest request = baseRequest(urlPath, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub file fetch failed for {}/{} path={} ref={}: status={} body={}",
                        repoRef.owner(), repoRef.repo(), path, ref, response.statusCode(), preview(response.body()));
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            String encoding = body.path("encoding").asText("");
            String rawContent = body.path("content").asText("");
            if (!"base64".equals(encoding) || rawContent.isBlank()) {
                return Optional.empty();
            }
            byte[] decoded = java.util.Base64.getMimeDecoder().decode(rawContent);
            return Optional.of(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Could not fetch file {} at ref {} for project {}: {}", path, ref, project.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Closes exactly one PR (unlike {@link #closeOpenPullRequests}, which closes every open PR for the
     * project). Used to discard a wishlist-compiler PR once its JSON plan has been parsed - it only ever
     * carries the plan file, never product code, so it must never be merged.
     */
    public PullRequestCloseResult closeSinglePullRequest(ProjectEntity project, GitHubPullRequest pullRequest, String reason) {
        if (project == null || pullRequest == null) {
            return new PullRequestCloseResult(0, "", "failed", 0, "Project or pull request missing");
        }
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return new PullRequestCloseResult(pullRequest.number(), pullRequest.url(), "failed", 0, "GitHub integration is disabled");
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return new PullRequestCloseResult(pullRequest.number(), pullRequest.url(), "failed", 0, "GitHub token is missing");
        }
        try {
            return closePullRequest(repoRef(project), pullRequest, token, reason);
        } catch (Exception e) {
            log.warn("Could not close GitHub PR {} for project {}: {}", pullRequest.url(), project.getId(), e.getMessage());
            return new PullRequestCloseResult(pullRequest.number(), pullRequest.url(), "failed", 0, e.getMessage());
        }
    }

    private java.util.List<GitHubPullRequest> fetchPullRequests(RepoRef repoRef, String state, String token) throws Exception {
        String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls?state=" + encode(state) + "&per_page=100";
        HttpRequest request = baseRequest(path, token).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("GitHub PR lookup failed for {}/{} state={}: status={} body={}", repoRef.owner(), repoRef.repo(), state, response.statusCode(), preview(response.body()));
            throw new IllegalStateException("GitHub returned HTTP " + response.statusCode() + " for pull request list");
        }

        JsonNode prs = objectMapper.readTree(response.body());
        if (!prs.isArray()) {
            return java.util.List.of();
        }

        java.util.List<GitHubPullRequest> result = new java.util.ArrayList<>();
        for (JsonNode pr : prs) {
            result.add(new GitHubPullRequest(
                    pr.path("html_url").asText(""),
                    pr.path("number").asInt(),
                    pr.path("title").asText(""),
                    pr.path("head").path("ref").asText(""),
                    pr.path("user").path("login").asText(""),
                    pr.hasNonNull("merged_at")
            ));
        }
        return result;
    }

    private PullRequestCloseResult closePullRequest(RepoRef repoRef,
                                                   GitHubPullRequest pullRequest,
                                                   String token,
                                                   String reason) throws Exception {
        String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullRequest.number();
        String body = "{\"state\":\"closed\"}";
        HttpRequest request = baseRequest(path, token)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Closed GitHub PR {} for {}/{} as WIP cleanup. reason={}",
                    pullRequest.url(), repoRef.owner(), repoRef.repo(), reason);
            return new PullRequestCloseResult(
                    pullRequest.number(),
                    pullRequest.url(),
                    "closed",
                    response.statusCode(),
                    "Closed as explicit operator WIP cleanup."
            );
        }
        log.warn("GitHub PR close failed for {}: status={} body={}",
                pullRequest.url(), response.statusCode(), preview(response.body()));
        return new PullRequestCloseResult(
                pullRequest.number(),
                pullRequest.url(),
                "failed",
                response.statusCode(),
                preview(response.body())
        );
    }

    private HttpRequest.Builder baseRequest(String path, String token) {
        return HttpRequest.newBuilder(URI.create(githubConfig.getApiBaseUrl().replaceAll("/+$", "") + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private RepoRef repoRef(ProjectEntity project) {
        String repositoryUrl = project.getRepositoryUrl();
        if (repositoryUrl != null && repositoryUrl.startsWith("https://github.com/")) {
            String clean = repositoryUrl.replace("https://github.com/", "").replaceAll("/+$", "");
            String[] parts = clean.split("/");
            if (parts.length >= 2) {
                return new RepoRef(parts[0], parts[1]);
            }
        }
        return new RepoRef(githubConfig.getOrganization(), project.getRepositoryName());
    }

    private String sessionToken(String externalSessionId) {
        if (externalSessionId == null || externalSessionId.isBlank()) {
            return "";
        }
        return externalSessionId.startsWith("sessions/")
                ? externalSessionId.substring("sessions/".length())
                : externalSessionId;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(this::encode)
                .reduce((a, b) -> a + "/" + b)
                .orElse("");
    }

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private record RepoRef(String owner, String repo) {}

    public record GitHubPullRequest(String url, int number, String title, String headRef, String author, boolean merged) {}

    public record PullRequestCloseResult(
            int number,
            String url,
            String status,
            int statusCode,
            String message
    ) {}

    public record PullRequestCloseReport(
            boolean available,
            String owner,
            String repo,
            int requested,
            long closed,
            List<PullRequestCloseResult> results,
            String error
    ) {
        static PullRequestCloseReport unavailable(String owner, String repo, String error) {
            return new PullRequestCloseReport(false, owner, repo, 0, 0, List.of(), error);
        }
    }

    public record PullRequestSnapshot(
            boolean available,
            String owner,
            String repo,
            java.util.List<GitHubPullRequest> open,
            java.util.List<GitHubPullRequest> closed,
            String error
    ) {
        public long closedUnmergedCount() {
            return closed == null ? 0 : closed.stream().filter(pr -> !pr.merged()).count();
        }

        static PullRequestSnapshot unavailable(String owner, String repo, String error) {
            return new PullRequestSnapshot(false, owner, repo, java.util.List.of(), java.util.List.of(), error);
        }
    }
}
