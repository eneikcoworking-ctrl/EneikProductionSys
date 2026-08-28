package com.eneik.production.services.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GitHubPullRequestService {
    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestService.class);

    private final GithubConfig githubConfig;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final GitHubApiBudgetService githubApiBudgetService;
    private final HttpClient httpClient;

    public GitHubPullRequestService(GithubConfig githubConfig,
                                    SystemSettingsService settingsService,
                                    ObjectMapper objectMapper,
                                    GitHubApiBudgetService githubApiBudgetService) {
        this.githubConfig = githubConfig;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.githubApiBudgetService = githubApiBudgetService;
        // Bounded connect timeout (2026-07-24/25 incident) - see JulesApiClient for the full incident note;
        // same fix applied uniformly across every outbound HTTP client in the codebase.
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /**
     * Phase follow-up (2026-07-21, operator directive): generalizes the persistent-worker "principle of
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
            HttpResponse<String> response = sendGitHub(request);
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

    /**
     * 2026-08-04 (live incident, test-forty-first task 1fbb3086): this used to fall back to matching ANY
     * open PR whose branch/title merely looked like a compiler/task-plan pattern once the exact-token
     * match failed - added 2026-07-22 (a5c5b96) on the premise that compiler PR branches don't reliably
     * embed their own session's token. Confirmed live that premise doesn't hold: PR#52
     * ("task-plan-500b9d0c-9092139308873395481") does embed a real session token, just not the one asking
     * - the fallback silently attributed an unrelated compiler session's PR to task 1fbb3086's long-dead
     * session, which had no PR of its own, marking it falsely "done" with zero real deliverable.
     * Soundness (a match is only ever correct) and completeness (a session's real PR is eventually found)
     * are not equally costly to get wrong here: a false negative just means waiting one more poll cycle -
     * cheap, and now bounded by the sessionCompleted/Davidson-trust-window closure added the same day this
     * comment was written. A false positive silently corrupts task status, the dependency graph, and every
     * readiness metric downstream of it - there is no cheap recovery from that. Only the exact-token match
     * is sound; keeping it as the only branch is a deliberate choice, not an oversight. If a genuine case
     * ever surfaces where a session's own PR really doesn't carry its token, the correct fix is parsing
     * Jules's own "for task [id]" self-reference out of the PR body (a fact Jules states about itself,
     * same evidentiary tier as an activity's own sessionCompleted field) and matching against the
     * session's own recorded Jules task id - not guessing from keywords in someone else's branch name.
     */
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
            HttpResponse<String> response = sendGitHub(request);
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
            HttpResponse<String> response = sendGitHub(request);
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

    /**
     * Merged counterpart of findClosedUnmergedPullRequestBySession (testimony-vs-evidence Phase 3,
     * 2026-07-30): a session's own locally-tracked status can miss the RUNNING -> pr_opened transition
     * entirely - that mapping only fires when a poll happens to land on Jules reporting SUCCEEDED - even
     * though the PR it opened was found, reviewed, and merged through the completely normal pipeline.
     * Without this, evidence checks only ever look for an OPEN PR, see none (because it's already merged
     * and closed), and wrongly conclude no PR was ever opened - retrying a doomed "open a new PR" call
     * against a branch already fully contained in main forever. No new HTTP call needed - pullRequestSnapshot
     * already fetches the closed-PR list, which includes merged ones; this just searches it.
     */
    public Optional<GitHubPullRequest> findMergedPullRequestBySession(ProjectEntity project, String externalSessionId) {
        if (project == null) {
            return Optional.empty();
        }
        PullRequestSnapshot snapshot = pullRequestSnapshot(project);
        if (!snapshot.available()) {
            return Optional.empty();
        }
        for (GitHubPullRequest pr : snapshot.closed()) {
            if (pr.merged() && matchesSessionToken(pr, externalSessionId)) {
                return Optional.of(pr);
            }
        }
        return Optional.empty();
    }

    public PullRequestSnapshot pullRequestSnapshot(ProjectEntity project) {
        if (project == null) {
            return PullRequestSnapshot.unavailable("", "", "Project is not selected");
        }
        // GitHub budget guard (2026-07-31): a single choke point, not one check per caller - every current
        // and future caller of this method (findOpenPullRequestBySession, findMergedPullRequestBySession,
        // findClosedUnmergedPullRequestBySession, BranchGarbageCollectorService, ...) is protected from
        // spending real GitHub calls on a project that isn't active, without needing to remember the check
        // itself. Confirmed live (2026-07-30/31): frozen/accepted projects' leftover work was consuming the
        // large majority of the shared rate-limit budget every hour.
        if (project.getStatus() != ProjectStatus.active) {
            return PullRequestSnapshot.unavailable("", "", "Project is not active");
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
    /**
     * Every file path under a directory, recursively. Uses the git trees API rather than walking
     * `/contents` one directory at a time: one request instead of N, and the recursive form already
     * returns the whole tree flattened.
     *
     * Returns an empty list rather than failing when the directory does not exist - a project with no
     * frontend is not an error, it is a project with no frontend, and callers decide what that means.
     */
    public java.util.List<String> listFilePaths(ProjectEntity project, String ref, String directoryPrefix) {
        if (project == null || ref == null || ref.isBlank()) {
            return java.util.List.of();
        }
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return java.util.List.of();
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return java.util.List.of();
        }
        RepoRef repoRef = repoRef(project);
        if (repoRef.owner().isBlank() || repoRef.repo().isBlank()) {
            return java.util.List.of();
        }
        try {
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/git/trees/" + encode(ref) + "?recursive=1";
            HttpResponse<String> response = sendGitHub(baseRequest(urlPath, token).GET().build());
            if (response.statusCode() != 200) {
                log.warn("GitHub tree listing failed for {}/{} ref={}: status={}",
                        repoRef.owner(), repoRef.repo(), ref, response.statusCode());
                return java.util.List.of();
            }
            com.fasterxml.jackson.databind.JsonNode tree =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).path("tree");
            java.util.List<String> paths = new java.util.ArrayList<>();
            String prefix = directoryPrefix == null ? "" : directoryPrefix;
            for (com.fasterxml.jackson.databind.JsonNode node : tree) {
                if (!"blob".equals(node.path("type").asText(""))) {
                    continue;
                }
                String p = node.path("path").asText("");
                if (!p.isBlank() && p.startsWith(prefix)) {
                    paths.add(p);
                }
            }
            return paths;
        } catch (Exception e) {
            log.warn("GitHub tree listing failed for project {}: {}", project.getId(), e.getMessage());
            return java.util.List.of();
        }
    }

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
            HttpResponse<String> response = sendGitHub(request);
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
            HttpResponse<String> response = sendGitHub(request);
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
     * Parses the PR number out of a "https://github.com/{owner}/{repo}/pull/{number}" URL. Returns null
     * for a blank/malformed URL or the placeholder "/mock-..." URLs some callers substitute when no real
     * PR exists yet - never throws.
     */
    public Integer parsePullNumber(String prUrl) {
        if (prUrl == null || prUrl.isBlank() || prUrl.contains("/mock-")) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(prUrl);
            String[] parts = uri.getPath().replaceAll("^/+", "").split("/");
            if (parts.length >= 4 && "pull".equals(parts[2]) && parts[3].matches("\\d+")) {
                return Integer.parseInt(parts[3]);
            }
        } catch (Exception e) {
            log.warn("Could not parse PR number from URL {}: {}", prUrl, e.getMessage());
        }
        return null;
    }

    /**
     * Extracts the list of changed file paths from a unified diff's own "+++ b/<path>" header lines -
     * real evidence of what a PR actually touched, as opposed to a self-reported file list.
     */
    public static List<String> changedFilePathsFromDiff(String diffText) {
        List<String> paths = new java.util.ArrayList<>();
        if (diffText == null || diffText.isBlank()) {
            return paths;
        }
        for (String line : diffText.split("\n")) {
            if (line.startsWith("+++ b/")) {
                paths.add(line.substring("+++ b/".length()).trim());
            }
        }
        return paths;
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
            HttpResponse<String> response = sendGitHub(request);
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
            HttpResponse<String> response = sendGitHub(request);
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
     * Writes a file to the default branch, creating it or updating it in place.
     *
     * Deliberately a SEPARATE method rather than a change to {@link #commitFile} above (2026-08-15):
     * fourteen call sites depend on that method's create-only semantics, and at least one depends on the
     * failure - promoting a design draft to the approved folder must NOT silently overwrite an already
     * approved mockup, so a second write there has to fail. Widening commitFile would have fixed the
     * bootstrap and broken design-asset promotion in the same commit.
     *
     * Why it is needed: GitHub's contents API requires the current blob `sha` when updating an existing
     * path and answers 422 without it. Bootstrap reused the create-only method for FIXED paths, one of
     * which (`.gitignore`) the project factory has already written seconds earlier, so the scaffold's own
     * `.gitignore` - the one carrying `target/` and `data/` - never landed on any project. Confirmed live
     * on test-forty-sixth: `.gitignore` has exactly one commit, the factory's, and the repository
     * consequently accumulated build artifacts that made every pair of compiling tasks conflict.
     */
    public boolean upsertFile(ProjectEntity project, String path, byte[] content, String commitMessage) {
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
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/contents/" + encodePath(path);
            var body = objectMapper.createObjectNode();
            body.put("message", commitMessage);
            body.put("content", java.util.Base64.getEncoder().encodeToString(content));
            // An absent sha means "create"; a present one means "update THIS version" and lets GitHub
            // reject the write if someone else changed the file meanwhile, rather than clobbering them.
            existingFileSha(repoRef, path, token).ifPresent(sha -> body.put("sha", sha));
            HttpRequest request = baseRequest(urlPath, token)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = sendGitHub(request);
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return true;
            }
            log.warn("GitHub upsert-file failed for {}/{} path={}: status={} body={}",
                    repoRef.owner(), repoRef.repo(), path, response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not upsert file {} for project {}: {}", path, project.getId(), e.getMessage());
        }
        return false;
    }

    /** Current blob sha of a path on the default branch, or empty when the path does not exist yet. */
    private Optional<String> existingFileSha(RepoRef repoRef, String path, String token) {
        try {
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/contents/" + encodePath(path);
            HttpResponse<String> response = sendGitHub(baseRequest(urlPath, token).GET().build());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(response.body());
            String sha = node.path("sha").asText("");
            return sha.isBlank() ? Optional.empty() : Optional.of(sha);
        } catch (Exception e) {
            // Treated as "does not exist": the write then attempts a create and fails loudly if the path
            // really was there, which is strictly better than silently skipping the write.
            return Optional.empty();
        }
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
                HttpResponse<String> response = sendGitHub(request);
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
            HttpResponse<String> response = sendGitHub(request);
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

    public boolean resolveProductCodeConflictWithMain(ProjectEntity project, String branch, String path) {
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
            Optional<byte[]> mainBytes = fetchFileBytes(project, "main", path);
            Optional<byte[]> branchBytes = fetchFileBytes(project, branch, path);

            if (mainBytes.isEmpty() || branchBytes.isEmpty()) {
                return resolveFileConflictWithMain(project, branch, path);
            }

            String mainContent = new String(mainBytes.get(), java.nio.charset.StandardCharsets.UTF_8);
            String branchContent = new String(branchBytes.get(), java.nio.charset.StandardCharsets.UTF_8);

            String mergedContent = smartMergeCodeContents(path, mainContent, branchContent);
            byte[] mergedBytes = mergedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/contents/" + encodePath(path);
            var body = objectMapper.createObjectNode();
            body.put("message", "AutoMerge: 3-way smart code conflict resolution for " + path);
            body.put("content", java.util.Base64.getEncoder().encodeToString(mergedBytes));
            body.put("branch", branch);
            if (branchSha != null) {
                body.put("sha", branchSha);
            }
            HttpRequest request = baseRequest(urlPath, token)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = sendGitHub(request);
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("resolveProductCodeConflictWithMain: Successfully merged product code for {} on branch {}", path, branch);
                return true;
            }
            log.warn("resolveProductCodeConflictWithMain: update failed for {} on branch {}: status={} body={}",
                    path, branch, response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("Could not resolve product code conflict for {} on branch {} for project {}: {}",
                    path, branch, project.getId(), e.getMessage());
        }
        return false;
    }

    private String smartMergeCodeContents(String path, String mainContent, String branchContent) {
        if (mainContent == null || mainContent.equals(branchContent)) {
            return branchContent != null ? branchContent : "";
        }
        if (branchContent == null || branchContent.isBlank()) {
            return mainContent;
        }

        String[] mainLines = mainContent.split("\r?\n");
        String[] branchLines = branchContent.split("\r?\n");

        java.util.LinkedHashSet<String> lineSet = new java.util.LinkedHashSet<>();
        for (String line : mainLines) {
            lineSet.add(line);
        }

        java.util.List<String> result = new java.util.ArrayList<>();
        for (String line : mainLines) {
            result.add(line);
        }

        for (String line : branchLines) {
            if (!lineSet.contains(line)) {
                if (line.trim().startsWith("import ") || line.trim().startsWith("use ")) {
                    int importIdx = 0;
                    for (int i = 0; i < result.size(); i++) {
                        if (result.get(i).trim().startsWith("import ") || result.get(i).trim().startsWith("package ")) {
                            importIdx = i + 1;
                        }
                    }
                    result.add(importIdx, line);
                } else {
                    int endIdx = result.size();
                    if (endIdx > 0 && (result.get(endIdx - 1).trim().equals("}") || result.get(endIdx - 1).trim().equals("</script>"))) {
                        result.add(endIdx - 1, line);
                    } else {
                        result.add(line);
                    }
                }
                lineSet.add(line);
            }
        }
        return String.join("\n", result);
    }

    /**
     * Deletes one path on a specific branch (not on main - {@link #deleteFile} does that). Added
     * 2026-08-27 for the AutoMerge poka-yoke, which strips the factory's own `.eneik/*` record files off a
     * product branch before that branch merges, so the invariant Code(t) INTERSECT L_factory = EMPTY holds
     * in the merged artifact and not only in the agent's prompt.
     *
     * <p>Fail-open by contract: returns false and logs on any failure, and the caller must treat that as
     * "cleanup did not happen" - never as grounds to block a merge that is otherwise legitimate.
     * A missing path counts as success, so calling this twice is safe.
     */
    public boolean deleteFileOnBranch(ProjectEntity project, String branch, String path, String commitMessage) {
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
            String sha = fetchFileSha(project, branch, path).orElse(null);
            if (sha == null) {
                return true; // already absent on this branch
            }
            var body = objectMapper.createObjectNode();
            body.put("message", commitMessage);
            body.put("sha", sha);
            body.put("branch", branch);
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/contents/" + encodePath(path);
            HttpRequest request = baseRequest(urlPath, token)
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = sendGitHub(request);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            log.warn("deleteFileOnBranch: delete failed for {} on branch {}: status={} body={}",
                    path, branch, response.statusCode(), preview(response.body()));
        } catch (Exception e) {
            log.warn("deleteFileOnBranch: could not delete {} on branch {} for project {}: {}",
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
            HttpResponse<String> response = sendGitHub(request);
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
        if (refusedByFactoryPokaYoke(project, pullRequest.number(), pullRequest.title())) {
            return new PullRequestCloseResult(pullRequest.number(), pullRequest.url(), "rejected", 0,
                    "Refused by the factory poka-yoke (blocker title or L_factory contamination)");
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullRequest.number() + "/merge";
            HttpRequest request = baseRequest(path, token)
                    .PUT(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = sendGitHub(request);
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
            HttpResponse<String> response = sendGitHub(request);
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
        return fetchPullRequestByNumber(repoRef, token, pullNumber, "project " + project.getId());
    }

    public Optional<GitHubPullRequest> fetchPullRequestByNumber(String owner, String repo, int pullNumber) {
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()
                || !settingsService.effectiveBoolean("github_enabled")) {
            return Optional.empty();
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return fetchPullRequestByNumber(new RepoRef(owner, repo), token, pullNumber, owner + "/" + repo);
    }

    private Optional<GitHubPullRequest> fetchPullRequestByNumber(RepoRef repoRef, String token, int pullNumber, String context) {
        if (repoRef == null) {
            return Optional.empty();
        }
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullNumber;
            HttpRequest request = baseRequest(path, token).GET().build();
            HttpResponse<String> response = sendGitHub(request);
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
                    "closed".equals(pr.path("state").asText("")),
                    parsePrCreatedAt(pr)
            ));
        } catch (Exception e) {
            log.warn("Could not fetch PR #{} for {}: {}", pullNumber, context, e.getMessage());
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
            HttpResponse<String> pullResponse = sendGitHub(baseRequest(pullPath, token).GET().build());
            if (pullResponse.statusCode() != 200) {
                return PullRequestChecks.unavailable("GitHub PR fetch returned HTTP " + pullResponse.statusCode());
            }
            String headSha = objectMapper.readTree(pullResponse.body()).path("head").path("sha").asText("");
            if (headSha.isBlank()) {
                return PullRequestChecks.unavailable("GitHub PR head SHA is missing");
            }

            String checksPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo())
                    + "/commits/" + encode(headSha) + "/check-runs?per_page=100";
            HttpResponse<String> checksResponse = sendGitHub(baseRequest(checksPath, token).GET().build());
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

    // Engineering invariant #14 (2026-08-08, docs/ENGINEERING_INVARIANTS_CHARTER.md, Kripke rigid
    // designation): a task's real merge-evidence PR, once established, must not be silently redesignated
    // by a structurally different event that happens to reuse the same branch/session token. AutoMergeService's
    // own closeout mechanism (progressCloseout) deliberately opens its PR FROM the same continuation branch
    // a task's real implementer session used - by construction that branch name still carries the original
    // session's token, so matchesSessionToken alone cannot distinguish "this task's own implementation PR"
    // from "the unrelated event of folding this feature's accumulated branch into main". Confirmed live
    // incident, test-forty-third: a Closeout PR, empty because the real work had already merged directly,
    // got matched by token and overwrote a task's correct hasCode=true evidence with hasCode=false. This is
    // the single, canonical definition every branch-token match against a TASK's own evidence must exclude
    // - not a per-call-site guess, so the exclusion can never again silently miss one of the several places
    // this matching happens (BranchGarbageCollectorService's own inline check now delegates here too).
    public static boolean isCloseoutPr(GitHubPullRequest pullRequest) {
        return pullRequest != null && pullRequest.title() != null && pullRequest.title().startsWith("Closeout");
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

    public java.util.List<GitHubPullRequest> fetchOpenPullRequests(ProjectEntity project) {
        if (project == null || project.getStatus() != ProjectStatus.active
                || !settingsService.effectiveBoolean("github_enabled")) return java.util.List.of();
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) return java.util.List.of();
        RepoRef repoRef = repoRef(project);
        if (repoRef == null) return java.util.List.of();

        try {
            return fetchPullRequests(repoRef, "open", token);
        } catch (Exception e) {
            log.warn("Could not fetch open PRs for project {}: {}", project.getId(), e.getMessage());
            return java.util.List.of();
        }
    }

    // Pagination (2026-08-08, engineering invariant #14 follow-up): GitHub's default sort for this endpoint
    // is created-desc, so a single page=1/per_page=100 call only ever sees the 100 MOST RECENTLY CREATED
    // PRs. Confirmed live, test-forty-third: task 010af204's real merge evidence (PR#11, created 08:45 UTC)
    // silently aged off page 1 once the project passed ~100 PRs, so reconcileMergedGitHubPullRequests's
    // branch-token fallback could never find it - not the Closeout-PR corruption bug fixed alongside this,
    // a separate, structural blind spot in "closed" state affecting every long-running project. Walks pages
    // until GitHub returns a short page (< 100 = last page). If the budget guard denies a later page mid-walk,
    // returns what was already fetched instead of discarding it - losing page 1's data over a budget cap on
    // page 3 would be a worse regression than the flat truncation this replaces.
    private java.util.List<GitHubPullRequest> fetchPullRequests(RepoRef repoRef, String state, String token) throws Exception {
        java.util.List<GitHubPullRequest> result = new java.util.ArrayList<>();
        int page = 1;
        int maxPages = 10;
        while (page <= maxPages) {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls?state="
                    + encode(state) + "&per_page=100&page=" + page;
            HttpRequest request = baseRequest(path, token).GET().build();
            HttpResponse<String> response;
            try {
                response = sendGitHub(request);
            } catch (IllegalStateException budgetDenied) {
                if (page == 1) {
                    throw budgetDenied;
                }
                log.warn("GitHub PR pagination stopped early for {}/{} state={} page={}: {}",
                        repoRef.owner(), repoRef.repo(), state, page, budgetDenied.getMessage());
                break;
            }
            if (response.statusCode() != 200) {
                log.warn("GitHub PR lookup failed for {}/{} state={} page={}: status={} body={}",
                        repoRef.owner(), repoRef.repo(), state, page, response.statusCode(), preview(response.body()));
                if (page == 1) {
                    throw new IllegalStateException("GitHub returned HTTP " + response.statusCode() + " for pull request list");
                }
                break;
            }

            JsonNode prs = objectMapper.readTree(response.body());
            if (!prs.isArray() || prs.isEmpty()) {
                break;
            }

            for (JsonNode pr : prs) {
                result.add(new GitHubPullRequest(
                        pr.path("html_url").asText(""),
                        pr.path("number").asInt(),
                        pr.path("title").asText(""),
                        pr.path("head").path("ref").asText(""),
                        pr.path("user").path("login").asText(""),
                        pr.hasNonNull("merged_at"),
                        pr.path("base").path("ref").asText(""),
                        "closed".equals(pr.path("state").asText("")),
                        parsePrCreatedAt(pr)
                ));
            }
            if (prs.size() < 100) {
                break;
            }
            page++;
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
        HttpResponse<String> response = sendGitHub(request);
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

    /**
     * Automatically syncs .github/workflows/ci.yml to JDK 17 Temurin matching pom.xml and Docker.
     */
    public boolean syncCiWorkflow(ProjectEntity project) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) return false;
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) return false;
        RepoRef repoRef = repoRef(project);
        if (repoRef == null) return false;

        String javaVersion = detectJavaVersionFromPom(repoRef, token);
        String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/contents/.github/workflows/ci.yml";
        String ciYaml = """
                name: CI

                on:
                  pull_request:
                    types: [opened, synchronize, reopened]
                  push:
                    branches:
                      - main

                jobs:
                  backend-verification:
                    name: Backend Verification
                    runs-on: ubuntu-latest
                    steps:
                      - name: Checkout code
                        uses: actions/checkout@v4

                      - name: Set up JDK %s
                        uses: actions/setup-java@v4
                        with:
                          distribution: 'temurin'
                          java-version: '%s'

                      - name: Run Maven tests
                        run: mvn clean test
                """.formatted(javaVersion, javaVersion);

        try {
            HttpRequest getReq = baseRequest(path, token).GET().build();
            HttpResponse<String> getRes = sendGitHub(getReq);
            String sha = null;
            if (getRes.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(getRes.body());
                sha = json.path("sha").asText(null);
                String existingContent = new String(
                        java.util.Base64.getMimeDecoder().decode(json.path("content").asText("")),
                        StandardCharsets.UTF_8);
                if (ciYaml.equals(existingContent)) {
                    log.debug("[CI-SYNC] .github/workflows/ci.yml already aligned to Java {} Temurin for {}/{}",
                            javaVersion, repoRef.owner(), repoRef.repo());
                    return true;
                }
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("message", "fix(ci): align GitHub Actions Java version to " + javaVersion + " Temurin matching pom.xml");
            body.put("content", java.util.Base64.getEncoder().encodeToString(ciYaml.getBytes(StandardCharsets.UTF_8)));
            if (sha != null) {
                body.put("sha", sha);
            }

            HttpRequest putReq = baseRequest(path, token)
                    .method("PUT", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> putRes = sendGitHub(putReq);
            if (putRes.statusCode() >= 200 && putRes.statusCode() < 300) {
                log.info("[CI-SYNC] Successfully synced .github/workflows/ci.yml to Java {} Temurin for {}/{}", javaVersion, repoRef.owner(), repoRef.repo());
                return true;
            }
        } catch (Exception e) {
            log.warn("[CI-SYNC] Could not sync ci.yml for project {}: {}", project.getId(), e.getMessage());
        }
        return false;
    }

    /**
     * Deletes a single file at `path` from the repo's default branch (main), if it exists. Reads the
     * current blob sha first (GitHub's contents-delete API requires it), then deletes. A 404 on the
     * initial read (file already gone) is treated as success. Used to clean up rejected design draft
     * commits - a plain forward commit, never a history rewrite.
     */
    public boolean deleteFile(ProjectEntity project, String path, String commitMessage) {
        if (project == null || path == null || path.isBlank()) {
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
            String urlPath = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/contents/" + encodePath(path);
            HttpRequest getRequest = baseRequest(urlPath, token).GET().build();
            HttpResponse<String> getResponse = sendGitHub(getRequest);
            if (getResponse.statusCode() == 404) {
                return true;
            }
            if (getResponse.statusCode() != 200) {
                log.warn("GitHub delete-file: could not read sha for {}/{} path={}: status={} body={}",
                        repoRef.owner(), repoRef.repo(), path, getResponse.statusCode(), preview(getResponse.body()));
                return false;
            }
            String sha = objectMapper.readTree(getResponse.body()).path("sha").asText("");
            if (sha.isBlank()) {
                return false;
            }
            var body = objectMapper.createObjectNode();
            body.put("message", commitMessage);
            body.put("sha", sha);
            HttpRequest deleteRequest = baseRequest(urlPath, token)
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> deleteResponse = sendGitHub(deleteRequest);
            if (deleteResponse.statusCode() == 200 || deleteResponse.statusCode() == 201) {
                return true;
            }
            log.warn("GitHub delete-file failed for {}/{} path={}: status={} body={}",
                    repoRef.owner(), repoRef.repo(), path, deleteResponse.statusCode(), preview(deleteResponse.body()));
        } catch (Exception e) {
            log.warn("Could not delete file {} for project {}: {}", path, project.getId(), e.getMessage());
        }
        return false;
    }

    private HttpRequest.Builder baseRequest(String path, String token) {
        return HttpRequest.newBuilder(URI.create(githubConfig.getApiBaseUrl().replaceAll("/+$", "") + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private HttpResponse<String> sendGitHub(HttpRequest request) throws java.io.IOException, InterruptedException {
        String operation = request.method() + " " + request.uri().getRawPath();
        if (request.uri().getRawQuery() != null) {
            operation += "?" + request.uri().getRawQuery();
        }
        GitHubApiBudgetService.GuardDecision guard = githubApiBudgetService.guard(operation);
        if (!guard.allowed()) {
            throw new IllegalStateException(guard.reason());
        }
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        githubApiBudgetService.recordResponse(operation, response);
        return response;
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

    private String detectJavaVersionFromPom(RepoRef repoRef, String token) {
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/contents/pom.xml";
            HttpRequest getReq = baseRequest(path, token).GET().build();
            HttpResponse<String> getRes = sendGitHub(getReq);
            if (getRes.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(getRes.body());
                String base64Content = json.path("content").asText("");
                String pomXml = new String(java.util.Base64.getMimeDecoder().decode(base64Content), StandardCharsets.UTF_8);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(?:java\\.version|maven\\.compiler\\.release|maven\\.compiler\\.target)>\\s*(\\d+)\\s*</").matcher(pomXml);
                if (m.find()) {
                    String ver = m.group(1);
                    log.info("[CI-SYNC] Detected Java version {} from pom.xml for {}/{}", ver, repoRef.owner(), repoRef.repo());
                    return ver;
                }
            }
        } catch (Exception e) {
            log.warn("[CI-SYNC] Failed to detect JDK version from pom.xml for {}/{}, defaulting to 17", repoRef.owner(), repoRef.repo(), e);
        }
        return "17";
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
    public record GitHubPullRequest(String url, int number, String title, String headRef, String author, boolean merged, String baseRef, boolean closed, Instant createdAt) {}

    /**
     * Parses GitHub's ISO-8601 created_at timestamp, tolerant of a missing/malformed value - real evidence
     * of a PR's age when present, never a reason to fail the whole PR fetch when absent (2026-07-31).
     */
    private static Instant parsePrCreatedAt(JsonNode pr) {
        String raw = pr.path("created_at").asText("");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

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
            HttpResponse<String> response = sendGitHub(request);
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
            HttpResponse<String> response = sendGitHub(request);
            if (response.statusCode() == 201) {
                JsonNode pr = objectMapper.readTree(response.body());
                return Optional.of(new GitHubPullRequest(
                        pr.path("html_url").asText(""), pr.path("number").asInt(), pr.path("title").asText(""),
                        pr.path("head").path("ref").asText(""), pr.path("user").path("login").asText(""),
                        pr.hasNonNull("merged_at"), pr.path("base").path("ref").asText(""),
                        "closed".equals(pr.path("state").asText("")), parsePrCreatedAt(pr)));
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
    /**
     * Titles an agent uses when it is reporting that it will NOT do the work rather than describing a
     * change. Same expression as AutoMergeService.BLOCKER_PR_TITLE, deliberately duplicated here rather
     * than shared: this class must not depend on AutoMergeService (that would be a cycle), and a merge
     * guard that can be disabled by a refactor somewhere else is not a guard.
     */
    private static final java.util.regex.Pattern BLOCKER_PR_TITLE = java.util.regex.Pattern.compile(
            "(?i)(^|\\W)(blocker|halt|contradiction|blocked by|cannot proceed)(\\W|$)");

    // Optional so every existing constructor call (including the many hand-built ones in tests) keeps
    // compiling; a null classifier degrades this guard to "no guard", never to a crash.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.services.CodeChangeClassifier codeChangeClassifier;

    /**
     * The merge-time poka-yoke, applied where a merge actually happens rather than at one of its callers.
     *
     * <p>Live incident, 2026-08-28. The same check already existed in AutoMergeService.executeMerge and
     * still failed to stop PR #319/#320 ("Blocker: Architectural contradiction in Brief 2", one changed
     * file: {@code _temp_submit.sh}) from reaching eneikdru/test-fiftieth's main branch - because those
     * merged through mergeRecordPullRequest, one of nine merge paths, none of which passed through
     * executeMerge. Guarding one caller closes one path; guarding the two methods that issue the actual
     * PUT .../merge closes all of them, and stays closed when a tenth caller appears.
     *
     * <p>It also verifies an assumption this class previously only asserted in a comment - "record PRs
     * carry exactly one .eneik/*.json file by construction, never product code". PR #320 is the
     * counterexample: nothing enforced it.
     *
     * <p>Fail-open by design: no token, no readable diff, or no classifier means this declines to judge
     * and the merge proceeds. A guard that refuses when it cannot see would strand legitimate work, which
     * is the more expensive error - see CodeChangeClassifier's own doc on that trade-off.
     */
    private boolean refusedByFactoryPokaYoke(ProjectEntity project, int pullNumber, String prTitle) {
        if (codeChangeClassifier == null || project == null) {
            return false;
        }
        String title = prTitle == null ? "" : prTitle;
        boolean blockerTitle = BLOCKER_PR_TITLE.matcher(title).find();

        java.util.List<String> contamination = new java.util.ArrayList<>();
        try {
            String diff = fetchDiffText(project, pullNumber).orElse(null);
            if (diff != null) {
                for (String path : changedFilePathsFromDiff(diff)) {
                    if (codeChangeClassifier.isFactoryArtifact(path)) {
                        contamination.add(path);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Poka-yoke could not read the diff of PR #{} for project {}: {}; allowing the merge "
                    + "rather than blocking on ignorance", pullNumber, project.getId(), e.getMessage());
        }

        if (contamination.isEmpty() && !blockerTitle) {
            return false;
        }
        String reason = contamination.isEmpty()
                ? "Blocker PR refused at the merge point: the title announces a refusal, not a change: " + title
                : "REJECTED_METADATA_CONTAMINATION: PR carries factory metalanguage (L_factory) files: " + contamination;
        log.warn("POKA-YOKE REFUSE: PR #{} in project {} will NOT be merged - {}",
                pullNumber, project.getId(), reason);
        try {
            fetchPullRequestByNumber(project, pullNumber)
                    .ifPresent(pr -> closeSinglePullRequest(project, pr, reason));
        } catch (Exception e) {
            log.warn("Poka-yoke refused PR #{} but could not close it: {}", pullNumber, e.getMessage());
        }
        return true;
    }

    public boolean mergePullRequest(ProjectEntity project, int pullNumber) {
        if (project == null || !settingsService.effectiveBoolean("github_enabled")) {
            return false;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return false;
        }
        if (refusedByFactoryPokaYoke(project, pullNumber,
                fetchPullRequestByNumber(project, pullNumber).map(GitHubPullRequest::title).orElse(""))) {
            return false;
        }
        RepoRef repoRef = repoRef(project);
        try {
            String path = "/repos/" + encode(repoRef.owner()) + "/" + encode(repoRef.repo()) + "/pulls/" + pullNumber + "/merge";
            HttpRequest request = baseRequest(path, token).PUT(HttpRequest.BodyPublishers.ofString("{}")).build();
            HttpResponse<String> response = sendGitHub(request);
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
            HttpResponse<String> response = sendGitHub(request);
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
