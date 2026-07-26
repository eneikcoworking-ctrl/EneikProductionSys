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
import java.time.Duration;
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
        // Bounded connect timeout (2026-07-24/25 incident) - see JulesApiClient for the full incident note;
        // same fix applied uniformly across every outbound HTTP client in the codebase.
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /**
     * Ф-followup (2026-07-21, operator directive): generalizes the persistent-worker "principle of
     * charity" check (already-parseable result file = real progress) to real implementer sessions, which
     * have no single result file to check - the closest analogous ground truth is "did a new commit land
     * on this branch since our own tracking last saw progress". Confirmed live: a session can push a real,
     * complete, working commit while Jules's own external API keeps reporting RUNNING indefinitely - our
     * lastProgressAt only advances on a status transition, so genuine progress like this is otherwise
     * invisible before a force-unblock circuit breaker closes the session as "stalled".
     */
    public Optional<java.time.Instant> latestCommitTime(ProjectEntity project, String branch) {
        if (project == null || branch == null || branch.isBlank()) {
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
                    + "/commits?sha=" + encode(branch) + "&per_page=1";
            HttpRequest request = baseRequest(urlPath, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub latest-commit lookup failed for {}/{} branch={}: status={}",
                        repoRef.owner(), repoRef.repo(), branch, response.statusCode());
                return Optional.empty();
            }
            JsonNode commits = objectMapper.readTree(response.body());
            if (!commits.isArray() || commits.isEmpty()) {
                return Optional.empty();
            }
            String dateText = commits.get(0).path("commit").path("committer").path("date").asText("");
            if (dateText.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(java.time.Instant.parse(dateText));
        } catch (Exception e) {
            log.warn("Could not fetch latest commit time for branch {} in project {}: {}", branch, project.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<GitHubPullRequest> findOpenPullRequestBySession(ProjectEntity project, String externalSessionId) {
        String sessionToken = sessionToken(externalSessionId);
        if (project == null) {
            return Optional.empty();
        }
        PullRequestSnapshot snapshot = pullRequestSnapshot(project);
        if (!snapshot.available()) {
            return Optional.empty();
        }

        if (sessionToken != null && !sessionToken.isBlank()) {
            for (GitHubPullRequest pr : snapshot.open()) {
                if (pr.headRef() != null && pr.headRef().contains(sessionToken)) {
                    return Optional.of(pr);
                }
            }
        }

        // Fallback matching for compiler PRs or PRs where branch/title follows jules-task-plan/compiler pattern
        for (GitHubPullRequest pr : snapshot.open()) {
            String head = pr.headRef() != null ? pr.headRef().toLowerCase(java.util.Locale.ROOT) : "";
            String title = pr.title() != null ? pr.title().toLowerCase(java.util.Locale.ROOT) : "";
            if (head.contains("task-plan") || head.contains("compiler") || title.contains("compiler") || title.contains("decomposition")) {
                return Optional.of(pr);
            }
        }
        return Optional.empty();
    }

    /**
     * Live incident, 2026-07-25 (feature-thread closeout): a feature's last task's own PR can merge
     * directly into main and get its branch deleted (standard --delete-branch convention), leaving the
     * feature thread's own accumulation branch equally gone. `AutoMergeService.progressCloseout` kept
     * retrying `createPullRequest(thread.branchName, "main", ...)` forever, HTTP 422 "head invalid" every
     * cycle, because it had no way to tell "genuinely transient failure" from "this branch will never come
     * back, there is nothing left to close out". This lets the caller ask directly instead of inferring it
     * from a failed PR-create call.
     */
    public boolean branchExists(ProjectEntity project, String branch) {
        if (project == null || branch == null || branch.isBlank() || !settingsService.effectiveBoolean("github_enabled")) {
            return true; // unknown - default to "assume it exists" so callers don't treat a config/flag gap as evidence of deletion
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return true;
        }
        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return true;
        }
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/branches/" + encode(branch);
            HttpRequest request = baseRequest(path, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() == 404) {
                return false;
            }
            // Any other status (rate limit, transient 5xx, etc.) is inconclusive - do not treat it as proof
            // of deletion, that would risk closing out a feature whose branch is actually still there.
            log.warn("GitHub branch-existence check inconclusive for {}/{} branch={}: status={} body={}",
                    repoRef.owner(), repoRef.repo(), branch, response.statusCode(), preview(response.body()));
            return true;
        } catch (Exception e) {
            log.warn("Could not check branch existence for {} in project {}: {}", branch, project.getId(), e.getMessage());
            return true;
        }
    }

    /**
     * Branch-level counterpart of findOpenPullRequestBySession (testimony-vs-evidence Phase 1, 2026-07-25):
     * a session can push real, complete work to its own branch and never open a PR for it (confirmed live
     * twice now - PR#72 and PR#77 incidents, see feedback_jules_status_not_source_of_truth memory) - neither
     * findOpenPullRequestBySession nor the PR-based evidence checks that call it can see that work, since
     * they only ever look at PRs. Same sessionToken/substring-match convention as findOpenPullRequestBySession,
     * applied to raw branch names instead of PR headRefs.
     */
    public Optional<String> findBranchBySession(ProjectEntity project, String externalSessionId) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
            return Optional.empty();
        }
        String sessionToken = sessionToken(externalSessionId);
        if (sessionToken == null || sessionToken.isBlank()) {
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
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/branches?per_page=100";
            HttpRequest request = baseRequest(path, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub branch lookup failed for {}/{}: status={} body={}",
                        repoRef.owner(), repoRef.repo(), response.statusCode(), preview(response.body()));
                return Optional.empty();
            }
            JsonNode branches = objectMapper.readTree(response.body());
            if (!branches.isArray()) {
                return Optional.empty();
            }
            for (JsonNode branch : branches) {
                String name = branch.path("name").asText("");
                if (!name.isBlank() && name.contains(sessionToken)) {
                    return Optional.of(name);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Could not fetch branches for project {} while looking for session {}: {}",
                    project.getId(), externalSessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Closed-and-NOT-merged counterpart of findOpenPullRequestBySession (testimony-vs-evidence Phase 2,
     * 2026-07-25): a task can be left orphaned in a non-terminal status when its PR gets closed without
     * merging through a path the orchestrator never observes (e.g. an operator closing it directly on
     * GitHub - confirmed live, task ca41509f/PR#78). No new HTTP call needed - pullRequestSnapshot already
     * fetches the closed-PR list; this just searches it with the same matching convention.
     */
    public Optional<GitHubPullRequest> findClosedUnmergedPullRequestBySession(ProjectEntity project, String externalSessionId) {
        if (project == null) {
            return Optional.empty();
        }
        PullRequestSnapshot snapshot = pullRequestSnapshot(project);
        if (!snapshot.available()) {
            return Optional.empty();
        }
        for (GitHubPullRequest pr : snapshot.closed()) {
            if (!pr.merged() && matchesSessionToken(pr, externalSessionId)) {
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
        return fetchFileBytes(project, ref, path).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Binary-safe counterpart of {@link #fetchFileContent} - decoding through a UTF-8 String (as
     * fetchFileContent does) silently corrupts non-text content such as PNG screenshots. Used by
     * {@link #copyFile} to promote a design draft (which includes a PNG) to the approved folder without
     * mangling it.
     */
    public Optional<byte[]> fetchFileBytes(ProjectEntity project, String ref, String path) {
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
            return Optional.of(java.util.Base64.getMimeDecoder().decode(rawContent));
        } catch (Exception e) {
            log.warn("Could not fetch file {} at ref {} for project {}: {}", path, ref, project.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lists a directory's entries via the GitHub contents API and returns the highest Flyway `V<N>__`
     * version number found - used to atomically reserve the next migration number at decomposition time
     * instead of letting each Jules session independently scan the directory and guess (see
     * TechnicalLeadCompiler.buildTaskDescription's BARCAN-TAG-08 branch). Optional.empty() means "could not
     * determine" (missing repo, directory doesn't exist yet, API error) - callers must NOT treat that as
     * "zero migrations exist", since asserting a wrong reservation is worse than making none at all.
     */
    public Optional<Integer> highestFlywayVersion(ProjectEntity project, String ref, String directoryPath) {
        if (project == null || ref == null || ref.isBlank() || directoryPath == null || directoryPath.isBlank()) {
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
                    + "/contents/" + encodePath(directoryPath) + "?ref=" + encode(ref);
            HttpRequest request = baseRequest(urlPath, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                // Directory doesn't exist yet on this branch - genuinely zero migrations, not an error.
                return Optional.of(0);
            }
            if (response.statusCode() != 200) {
                log.warn("GitHub directory listing failed for {}/{} path={} ref={}: status={} body={}",
                        repoRef.owner(), repoRef.repo(), directoryPath, ref, response.statusCode(), preview(response.body()));
                return Optional.empty();
            }
            JsonNode entries = objectMapper.readTree(response.body());
            if (!entries.isArray()) {
                return Optional.empty();
            }
            java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("^V(\\d+)__");
            int highest = 0;
            for (JsonNode entry : entries) {
                String name = entry.path("name").asText("");
                java.util.regex.Matcher matcher = versionPattern.matcher(name);
                if (matcher.find()) {
                    highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
                }
            }
            return Optional.of(highest);
        } catch (Exception e) {
            log.warn("Could not list migration directory {} at ref {} for project {}: {}",
                    directoryPath, ref, project.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches the unified diff text for a PR - used by the Jules-reviewer fallback
     * (JulesDispatchService.dispatchReviewerFallback) to embed the real code change directly in that
     * session's prompt, since Jules sessions always start from main and have no way to check out an
     * arbitrary PR branch themselves.
     */
    public Optional<String> fetchDiffText(ProjectEntity project, int pullNumber) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
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
            String url = githubConfig.getApiBaseUrl().replaceAll("/+$", "") + "/repos/"
                    + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullNumber;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3.diff")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            }
            log.warn("GitHub diff fetch failed for PR #{} in project {}: status={}", pullNumber, project.getId(), response.statusCode());
        } catch (Exception e) {
            log.warn("Could not fetch diff for PR #{} in project {}: {}", pullNumber, project.getId(), e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Commits a single new file directly to the project's default branch via the GitHub "create file
     * contents" API - used to put generated design assets (Stitch mockups) inside the actual repository
     * so a Jules session (which only ever sees the checked-out repo, never the Eneik backend's own disk)
     * can read them. Only handles brand-new files (no `sha`, so this is a create, not an update) - every
     * caller here uses a fresh timestamped path, so collisions are not expected.
     */
    public boolean commitFile(ProjectEntity project, String path, byte[] content, String commitMessage) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
            return false;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return false;
        }
        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return false;
        }
        try {
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/contents/" + encodePath(path);
            var body = objectMapper.createObjectNode();
            body.put("message", commitMessage);
            body.put("content", java.util.Base64.getEncoder().encodeToString(content));
            HttpRequest request = baseRequest(urlPath, token)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return true;
            }
            log.warn("GitHub commit-file failed for {}/{} path={}: status={} body={}",
                    repoRef.owner(), repoRef.repo(), path, response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not commit file {} for project {}: {}", path, project.getId(), e.getMessage());
        }
        return false;
    }

    /**
     * Overwrites (or removes) one path on a PR branch so it matches `main`'s current content for that
     * path - a plain git commit, never a Jules session. Built for resolving conflicts on our own
     * transient `.eneik/*.json` record files (task-plan/review-verdict/design-review-verdict): once
     * another PR has already updated one of those paths on main, an older branch that also touched it
     * shows as conflicting even though the real product code has no overlap at all - syncing this one
     * path to main's version clears the conflict without touching anything the branch actually authored.
     * Returns true if the branch now matches main for this path (including the no-op case where it
     * already did).
     */
    public boolean resolveFileConflictWithMain(ProjectEntity project, String branch, String path) {
        if (project == null || branch == null || branch.isBlank() || path == null || path.isBlank()) {
            return false;
        }
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return false;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return false;
        }
        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return false;
        }
        try {
            String branchSha = fetchFileSha(project, branch, path).orElse(null);
            Optional<byte[]> mainContent = fetchFileBytes(project, "main", path);
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/contents/" + encodePath(path);

            if (mainContent.isEmpty()) {
                if (branchSha == null) {
                    return true;
                }
                var body = objectMapper.createObjectNode();
                body.put("message", "Resolve conflict: remove " + path + " (record file no longer present on main)");
                body.put("sha", branchSha);
                body.put("branch", branch);
                HttpRequest request = baseRequest(urlPath, token)
                        .method("DELETE", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return true;
                }
                log.warn("resolveFileConflictWithMain: delete failed for {} on branch {}: status={} body={}",
                        path, branch, response.statusCode(), preview(response.body()));
                return false;
            }

            var body = objectMapper.createObjectNode();
            body.put("message", "Resolve conflict: sync " + path + " with main (system record file, not product code)");
            body.put("content", java.util.Base64.getEncoder().encodeToString(mainContent.get()));
            body.put("branch", branch);
            if (branchSha != null) {
                body.put("sha", branchSha);
            }
            HttpRequest request = baseRequest(urlPath, token)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return true;
            }
            log.warn("resolveFileConflictWithMain: update failed for {} on branch {}: status={} body={}",
                    path, branch, response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not resolve file conflict for {} on branch {} for project {}: {}",
                    path, branch, project.getId(), e.getMessage());
        }
        return false;
    }

    private Optional<String> fetchFileSha(ProjectEntity project, String ref, String path) {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return Optional.empty();
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        RepoRef repoRef = repoRef(project);
        try {
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/contents/" + encodePath(path) + "?ref=" + encode(ref);
            HttpRequest request = baseRequest(urlPath, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            String sha = body.path("sha").asText("");
            return sha.isBlank() ? Optional.empty() : Optional.of(sha);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Reads an existing committed file and re-commits its exact bytes at a new path - used to promote a
     * design draft to the approved folder once it passes review, without touching the draft (kept as a
     * permanent record).
     */
    public boolean copyFile(ProjectEntity project, String fromPath, String toPath, String commitMessage) {
        Optional<byte[]> content = fetchFileBytes(project, "main", fromPath);
        if (content.isEmpty()) {
            log.warn("GitHub copy-file: source {} not found for project {}", fromPath, project == null ? "unknown" : project.getId());
            return false;
        }
        return commitFile(project, toPath, content.get(), commitMessage);
    }

    /**
     * Merges a record PR (a compiler plan, a review verdict, a design-review verdict, a falsification
     * report) instead of discarding it. These files are real production documentation once parsed and
     * acted on - "it did its job" is a reason to keep it in history under a real name, not to throw it
     * away. Falls back to {@link #closeSinglePullRequest} if the merge itself fails (e.g. a real
     * conflict), so a failed merge never leaves the PR silently open forever.
     */
    public PullRequestCloseResult mergeRecordPullRequest(ProjectEntity project, GitHubPullRequest pullRequest, String reason) {
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
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullRequest.number() + "/merge";
            HttpRequest request = baseRequest(path, token)
                    .PUT(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Merged record PR {} for {}/{}. reason={}", pullRequest.url(), repoRef.owner(), repoRef.repo(), reason);
                // Record PRs carry exactly one .eneik/*.json file by construction - never product code -
                // so the branch is disposable the moment its verdict/report/plan has been merged.
                deleteBranch(project, pullRequest.headRef());
                return new PullRequestCloseResult(pullRequest.number(), pullRequest.url(), "merged", response.statusCode(), reason);
            }
            log.warn("Record PR merge failed for {}: status={} body={}; falling back to close",
                    pullRequest.url(), response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not merge record PR {} for project {}: {}; falling back to close",
                    pullRequest.url(), project.getId(), e.getMessage());
        }
        return closeSinglePullRequest(project, pullRequest, reason + " (merge failed, closed instead)");
    }

    /**
     * Deletes a branch by name. Used both unconditionally after a record-PR merge (see above - those
     * never contain code) and conditionally by AutoMergeService after a real implementer PR merges with
     * no actual code in its diff. A 404 (branch already gone - e.g. GitHub's own "delete head branches on
     * merge" repo setting beat us to it) is treated as success, not an error.
     */
    public boolean deleteBranch(ProjectEntity project, String branchName) {
        if (project == null || branchName == null || branchName.isBlank()) {
            return false;
        }
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return false;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return false;
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/git/refs/heads/" + encodePath(branchName);
            HttpRequest request = baseRequest(path, token).DELETE().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                log.info("Deleted branch {} for {}/{} (status={})", branchName, repoRef.owner(), repoRef.repo(), response.statusCode());
                return true;
            }
            log.warn("Failed to delete branch {} for {}/{}: status={} body={}",
                    branchName, repoRef.owner(), repoRef.repo(), response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not delete branch {} for project {}: {}", branchName, project.getId(), e.getMessage());
        }
        return false;
    }

    /**
     * Fetches a single PR by number - used where a caller (AutoMergeService) already knows a PR merged
     * but only has owner/repo/number, not its head ref (branch name), which is needed to delete the
     * branch after a no-code classification.
     */
    public Optional<GitHubPullRequest> fetchPullRequestByNumber(ProjectEntity project, int pullNumber) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
            return Optional.empty();
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullNumber;
            HttpRequest request = baseRequest(path, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub PR fetch failed for #{} in {}/{}: status={}", pullNumber, repoRef.owner(), repoRef.repo(), response.statusCode());
                return Optional.empty();
            }
            JsonNode pr = objectMapper.readTree(response.body());
            return Optional.of(new GitHubPullRequest(
                    pr.path("html_url").asText(""),
                    pr.path("number").asInt(),
                    pr.path("title").asText(""),
                    pr.path("head").path("ref").asText(""),
                    pr.path("user").path("login").asText(""),
                    pr.hasNonNull("merged_at"),
                    pr.path("base").path("ref").asText(""),
                    "closed".equals(pr.path("state").asText(""))
            ));
        } catch (Exception e) {
            log.warn("Could not fetch PR #{} for project {}: {}", pullNumber, project.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads the real GitHub check-runs for a PR head. Auto-merge must fail closed here: a locally
     * generated review verdict is not evidence that GitHub CI ran, and treating every discovered PR as
     * green previously merged a sequence of red branches into main.
     */
    public PullRequestChecks pullRequestChecks(ProjectEntity project, int pullNumber) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
            return PullRequestChecks.unavailable("GitHub integration is disabled");
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return PullRequestChecks.unavailable("GitHub token is missing");
        }
        RepoRef repoRef = repoRef(project);
        try {
            String pullPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/pulls/" + pullNumber;
            HttpResponse<String> pullResponse = httpClient.send(
                    baseRequest(pullPath, token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (pullResponse.statusCode() != 200) {
                return PullRequestChecks.unavailable("GitHub PR fetch returned HTTP " + pullResponse.statusCode());
            }
            String headSha = objectMapper.readTree(pullResponse.body()).path("head").path("sha").asText("");
            if (headSha.isBlank()) {
                return PullRequestChecks.unavailable("GitHub PR head SHA is missing");
            }

            String checksPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/commits/" + encode(headSha) + "/check-runs?per_page=100";
            HttpResponse<String> checksResponse = httpClient.send(
                    baseRequest(checksPath, token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (checksResponse.statusCode() != 200) {
                return PullRequestChecks.unavailable("GitHub check-runs returned HTTP " + checksResponse.statusCode());
            }

            return evaluateCheckRuns(objectMapper.readTree(checksResponse.body()).path("check_runs"));
        } catch (Exception e) {
            return PullRequestChecks.unavailable(e.getMessage());
        }
    }

    static PullRequestChecks evaluateCheckRuns(JsonNode checkRuns) {
        if (checkRuns == null || !checkRuns.isArray() || checkRuns.isEmpty()) {
            return new PullRequestChecks(true, false, "pending", "No GitHub check-runs exist for the PR head");
        }

        java.util.List<String> failures = new java.util.ArrayList<>();
        boolean pending = false;
        for (JsonNode checkRun : checkRuns) {
            String name = checkRun.path("name").asText("unnamed-check");
            String status = checkRun.path("status").asText("");
            String conclusion = checkRun.path("conclusion").asText("");
            if (!"completed".equalsIgnoreCase(status)) {
                pending = true;
                continue;
            }
            if (!java.util.Set.of("success", "neutral", "skipped").contains(conclusion.toLowerCase(java.util.Locale.ROOT))) {
                failures.add(name + "=" + (conclusion.isBlank() ? "unknown" : conclusion));
            }
        }
        if (pending) {
            return new PullRequestChecks(true, false, "pending", "One or more GitHub checks are still running");
        }
        if (!failures.isEmpty()) {
            return new PullRequestChecks(true, false, "failure", String.join(", ", failures));
        }
        return new PullRequestChecks(true, true, "success", "All GitHub check-runs completed successfully");
    }

    public static boolean matchesSessionToken(GitHubPullRequest pullRequest, String externalSessionId) {
        if (pullRequest == null || pullRequest.headRef() == null || externalSessionId == null
                || externalSessionId.isBlank() || "skipped".equals(externalSessionId)) {
            return false;
        }
        String token = externalSessionId.startsWith("sessions/")
                ? externalSessionId.substring("sessions/".length())
                : externalSessionId;
        return !token.isBlank() && pullRequest.headRef().contains(token);
    }

    /**
     * Closes exactly one PR (unlike {@link #closeOpenPullRequests}, which closes every open PR for the
     * project). Used only when a record PR could not be validly parsed (e.g. an invalid compiler plan) -
     * there is nothing worth keeping in history in that case.
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
                    pr.hasNonNull("merged_at"),
                    pr.path("base").path("ref").asText(""),
                    "closed".equals(pr.path("state").asText(""))
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

    // closed (2026-07-24): distinguishes "closed without merging" (state=closed, merged=false) from "still
    // open, being worked on" - previously indistinguishable, causing AutoMergeService to retry merging a
    // manually-closed PR forever (confirmed live: PR#57, closed by the operator, kept getting a 405 "not
    // mergeable" retry every ~60s since nothing ever told the review polling loop the PR was dead).
    public record GitHubPullRequest(String url, int number, String title, String headRef, String author, boolean merged, String baseRef, boolean closed) {}

    /**
     * `mergeable`: null while GitHub is still computing it asynchronously (this is the normal state for a
     * few seconds right after any push to the branch), true/false once computed. `mergeStateStatus`: GitHub's
     * own richer status ("clean", "dirty", "unknown", "blocked", ...).
     */
    public record MergeableState(Boolean mergeable, String mergeStateStatus) {}

    /**
     * Operator directive 2026-07-24 (live incident): right after this service's own trivial-conflict fast
     * path pushes a commit resolving a `.gitignore`/`.eneik/*` conflict, GitHub has not yet finished
     * recomputing `mergeable` for that PR - it stays null/"unknown" for some seconds. The next scheduled
     * tick's merge attempt used to fire blind into this window, get a stale 405, and count it as a real
     * failed resolution attempt - three of those in the ~2 minutes it took GitHub to catch up burned the
     * whole 3-attempt budget and re-escalated a PR that had ZERO real conflicts left (confirmed via local
     * `git merge-tree` against the same commit). This lets a caller check the real, current state first and
     * skip an attempt entirely while GitHub is still computing, instead of spending one of the 3 tries on
     * a race it can never win.
     */
    public Optional<MergeableState> mergeableState(ProjectEntity project, int pullNumber) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
            return Optional.empty();
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullNumber;
            HttpRequest request = baseRequest(path, token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode pr = objectMapper.readTree(response.body());
            Boolean mergeable = pr.hasNonNull("mergeable") ? pr.path("mergeable").asBoolean() : null;
            String mergeStateStatus = pr.path("mergeable_state").asText(null);
            return Optional.of(new MergeableState(mergeable, mergeStateStatus));
        } catch (Exception e) {
            log.warn("Could not fetch mergeable state for PR #{} for project {}: {}", pullNumber, project.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Opens a real PR head-&gt;base with no Jules session involved - used for feature-thread closeout
     * (AutoMergeService.closeOutReadyFeatureThreads), where the "task" is folding an already-reviewed
     * accumulation branch back into main, not a fresh piece of work.
     */
    public Optional<GitHubPullRequest> createPullRequest(ProjectEntity project, String head, String base,
            String title, String body) {
        if (project == null || head == null || head.isBlank() || base == null || base.isBlank()
                || !settingsService.effectiveBoolean("github_enabled")) {
            return Optional.empty();
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls";
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("title", title);
            bodyNode.put("head", head);
            bodyNode.put("base", base);
            bodyNode.put("body", body == null ? "" : body);
            HttpRequest request = baseRequest(path, token)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyNode)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                JsonNode pr = objectMapper.readTree(response.body());
                return Optional.of(new GitHubPullRequest(
                        pr.path("html_url").asText(""), pr.path("number").asInt(), pr.path("title").asText(""),
                        pr.path("head").path("ref").asText(""), pr.path("user").path("login").asText(""),
                        pr.hasNonNull("merged_at"), pr.path("base").path("ref").asText(""),
                        "closed".equals(pr.path("state").asText(""))));
            }
            // 422 "No commits between base and head" is a real, expected outcome when the thread branch has
            // already fully landed elsewhere or has nothing new relative to base - not an error to retry loudly.
            log.warn("GitHub create-PR failed for {}/{} head={} base={}: status={} body={}",
                    repoRef.owner(), repoRef.repo(), head, base, response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not create PR {}->{} for project {}: {}", head, base, project.getId(), e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Raw "click the merge button" call, extracted so both AutoMergeService.executeMerge's own inline PUT
     * and the feature-thread closeout job (which has no PrReviewEntity/session to hang side effects off)
     * can share it. Deliberately has no side effects of its own (no branch cleanup, no conflict handling,
     * no status writes) - callers own all of that, matching how executeMerge's inline version already works.
     */
    public boolean mergePullRequest(ProjectEntity project, int pullNumber) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
            return false;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return false;
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullNumber + "/merge";
            HttpRequest request = baseRequest(path, token).PUT(HttpRequest.BodyPublishers.ofString("{}")).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            log.warn("GitHub merge failed for PR #{} in {}/{}: status={} body={}",
                    pullNumber, repoRef.owner(), repoRef.repo(), response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not merge PR #{} for project {}: {}", pullNumber, project.getId(), e.getMessage());
        }
        return false;
    }

    public enum MergeBranchResult { MERGED, UP_TO_DATE, CONFLICT, ERROR, SKIPPED }

    /**
     * `POST /repos/{owner}/{repo}/merges` - GitHub's plain "merge a branch" endpoint (distinct from the
     * Pulls "merge a pull request" one above): creates a real merge commit combining `head` into `base` and
     * moves `base`'s ref forward, with no PR involved at all. Used to keep an still-open feature-thread
     * branch from drifting far from main while work on it is ongoing (AutoMergeService.
     * closeOutReadyFeatureThreads calls this every tick with base=thread branch, head="main") - continuous
     * drift-prevention, not the eventual thread-&gt;main closeout itself (that goes through a real
     * reviewable PR via createPullRequest/mergePullRequest above).
     */
    public MergeBranchResult mergeBranch(ProjectEntity project, String base, String head, String commitMessage) {
        if (project == null || base == null || base.isBlank() || head == null || head.isBlank()
                || !settingsService.effectiveBoolean("github_enabled")) {
            return MergeBranchResult.SKIPPED;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return MergeBranchResult.SKIPPED;
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/merges";
            var body = objectMapper.createObjectNode();
            body.put("base", base);
            body.put("head", head);
            body.put("commit_message", commitMessage == null ? "" : commitMessage);
            HttpRequest request = baseRequest(path, token)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return switch (response.statusCode()) {
                case 201 -> MergeBranchResult.MERGED;
                case 204 -> MergeBranchResult.UP_TO_DATE;
                case 409 -> MergeBranchResult.CONFLICT;
                default -> {
                    log.warn("GitHub branch-merge failed base={} head={} for {}/{}: status={} body={}",
                            base, head, repoRef.owner(), repoRef.repo(), response.statusCode(), preview(response.body()));
                    yield MergeBranchResult.ERROR;
                }
            };
        } catch (Exception e) {
            log.warn("Could not merge branch {} into {} for project {}: {}", head, base, project.getId(), e.getMessage());
            return MergeBranchResult.ERROR;
        }
    }

    public record PullRequestChecks(boolean available, boolean successful, String status, String detail) {
        static PullRequestChecks unavailable(String detail) {
            return new PullRequestChecks(false, false, "unavailable", detail == null ? "Unknown GitHub error" : detail);
        }
    }

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
