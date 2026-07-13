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
                    pr.path("user").path("login").asText("")
            ));
        }
        return result;
    }

    private HttpRequest.Builder baseRequest(String path, String token) {
        return HttpRequest.newBuilder(URI.create(githubConfig.getApiBaseUrl().replaceAll("/+$", "") + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
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

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private record RepoRef(String owner, String repo) {}

    public record GitHubPullRequest(String url, int number, String title, String headRef, String author) {}

    public record PullRequestSnapshot(
            boolean available,
            String owner,
            String repo,
            java.util.List<GitHubPullRequest> open,
            java.util.List<GitHubPullRequest> closed,
            String error
    ) {
        static PullRequestSnapshot unavailable(String owner, String repo, String error) {
            return new PullRequestSnapshot(false, owner, repo, java.util.List.of(), java.util.List.of(), error);
        }
    }
}
