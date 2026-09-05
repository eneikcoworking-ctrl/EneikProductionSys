package com.eneik.production.services.jules;

import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.task.TaskTitleBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class JulesApiClient {
    private static final Logger log = LoggerFactory.getLogger(JulesApiClient.class);

    // Was a hardcoded 2MB - too aggressive for this system's real usage: role charters alone (the
    // philosophical/deontic/formal-logic sections baked into every session's context, see
    // BARCAN-*.md/RoleRulesController) run tens of KB each, and a session's activity log accumulates every
    // tool call and message on top of that. A healthy, verbose session routinely exceeded this and got
    // marked "blind" (question-scanning skipped) purely from legitimate volume, not from being stuck -
    // confirmed live on test-thirty-second, operator's explicit diagnosis. Configurable, not a silently
    // unbounded read: still protects backend memory, just at a size that matches real payloads.
    @Value("${jules.max-activities-response-bytes:10485760}")
    private int maxActivitiesResponseBytes;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final SystemSettingsService settingsService;

    public JulesApiClient(ObjectMapper objectMapper,
                          @Value("${jules.api-base-url:https://jules.googleapis.com/v1alpha}") String apiBaseUrl,
                          SystemSettingsService settingsService) {
        // Live incident, 2026-07-24/25: a transient network blip left several scheduling threads hung
        // indefinitely inside this client's HTTP calls (no timeout at all, connect or otherwise), starving
        // the shared 10-thread scheduling pool down to effectively one live thread and cascading into a
        // "SYSTEM STALLED" alarm project-wide. Same connectTimeout convention already proven safe elsewhere
        // in this codebase (StitchClient, GoogleAiResourceService) - a bounded external call on a shared,
        // finite thread pool, never unbounded.
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.objectMapper = objectMapper;
        this.apiBaseUrl = apiBaseUrl;
        this.settingsService = settingsService;
    }

    public String createSession(String repoUrl, String taskDescription, String roleContext) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return createSession(repoUrl, taskDescription, roleContext, apiKey);
    }

    public String createSession(String repoUrl, String taskDescription, String roleContext, String apiKey) {
        return createSessionDetailed(repoUrl, taskDescription, roleContext, apiKey).sessionName();
    }

    public CreateSessionResult createSessionDetailed(String repoUrl, String taskDescription, String roleContext, String apiKey) {
        return createSessionDetailed(repoUrl, taskDescription, roleContext, apiKey, TaskTitleBuilder.build("", taskDescription));
    }

    public CreateSessionResult createSessionDetailed(String repoUrl, String taskDescription, String roleContext, String apiKey, String title) {
        return createSessionDetailed(repoUrl, taskDescription, roleContext, apiKey, title, "main");
    }

    /**
     * startingBranch lets a session continue from an existing branch (its prior commits already present)
     * instead of always starting fresh from main - used for role-thread continuation, see
     * JulesDispatchService.dispatchInternal / FeatureThreadRepository.
     */
    public CreateSessionResult createSessionDetailed(String repoUrl, String taskDescription, String roleContext,
                                                     String apiKey, String title, String startingBranch) {
        if (!settingsService.effectiveBoolean("jules_enabled")) {
            log.info("Jules integration disabled (JULES_ENABLED != true). Returning 'skipped'.");
            return new CreateSessionResult("skipped", 0, "jules_disabled");
        }

        if (repoUrl == null || repoUrl.isBlank()) {
            return CreateSessionResult.failure(400, "repo_url_missing", 0, "", "");
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Jules API key is not configured. Returning 'skipped'.");
            return new CreateSessionResult("skipped", 0, "missing_api_key");
        }

        String effectiveBranch = startingBranch == null || startingBranch.isBlank() ? "main" : startingBranch;
        String prompt = taskDescription + "\n\nContext:\n" + roleContext;
        int promptLength = prompt.length();
        String julesSourceName = toJulesSourceName(repoUrl);

        try {
            SourceCheckResult checkResult = sourceAvailability(julesSourceName, apiKey);
            if (checkResult.availability() == SourceAvailability.MISSING) {
                return CreateSessionResult.failure(checkResult.statusCode(), checkResult.message(),
                        promptLength, julesSourceName, effectiveBranch);
            }
            if (checkResult.availability() == SourceAvailability.UNKNOWN) {
                return CreateSessionResult.failure(checkResult.statusCode(), checkResult.message(),
                        promptLength, julesSourceName, effectiveBranch);
            }

            ObjectNode githubRepoContext = objectMapper.createObjectNode();
            githubRepoContext.put("startingBranch", effectiveBranch);

            ObjectNode sourceContext = objectMapper.createObjectNode();
            sourceContext.put("source", julesSourceName);
            sourceContext.set("githubRepoContext", githubRepoContext);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("prompt", prompt);
            body.set("sourceContext", sourceContext);
            body.put("automationMode", "AUTO_CREATE_PR");
            body.put("title", TaskTitleBuilder.enforceTwoOrThreeWords(title));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/sessions"))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400 && startingBranch != null && !"main".equals(startingBranch)) {
                log.warn("Jules session creation failed with startingBranch={} (HTTP 400). Retrying automatically from main...", startingBranch);
                return createSessionDetailed(repoUrl, taskDescription, roleContext, apiKey, title, "main");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules session creation failed: status={} promptLength={} source={} startingBranch={} body={}",
                        response.statusCode(), promptLength, julesSourceName, effectiveBranch, response.body());
                return CreateSessionResult.failure(response.statusCode(), response.body(),
                        promptLength, julesSourceName, effectiveBranch);
            }

            JsonNode json = objectMapper.readTree(response.body());
            return new CreateSessionResult(json.path("name").asText(null), response.statusCode(), "",
                    promptLength, julesSourceName, effectiveBranch);
        } catch (IOException | InterruptedException e) {
            log.error("Error creating Jules session: promptLength={} source={} startingBranch={}",
                    promptLength, julesSourceName, effectiveBranch, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return CreateSessionResult.failure(0, e.getMessage(), promptLength, julesSourceName, effectiveBranch);
        }
    }

    private SourceCheckResult sourceAvailability(String sourceName, String apiKey) {
        if (sourceName == null || sourceName.isBlank() || !sourceName.startsWith("sources/github/")) {
            return SourceCheckResult.unverified(400, "sourceName is invalid or not a github source: " + sourceName);
        }
        try {
            String pageToken = "";
            for (int page = 0; page < 20; page++) {
                String path = "/sources";
                if (!pageToken.isBlank()) {
                    path += "?pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8);
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiBaseUrl + path))
                        .header("X-Goog-Api-Key", apiKey)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Jules source preflight failed: status={} body={}", response.statusCode(), response.body());
                    return SourceCheckResult.unverified(response.statusCode(),
                            "preflight listing returned HTTP " + response.statusCode() + ": " + response.body());
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode sources = root.path("sources");
                if (!sources.isArray()) {
                    return SourceCheckResult.unverified(502, "preflight response 'sources' is not an array");
                }
                for (JsonNode source : sources) {
                    if (sourceName.equals(source.path("name").asText(null))) {
                        return SourceCheckResult.visible();
                    }
                }
                pageToken = root.path("nextPageToken").asText("");
                if (pageToken.isBlank()) {
                    return SourceCheckResult.missing(sourceName);
                }
            }
            log.warn("Jules source preflight reached pagination safety limit while looking for {}", sourceName);
            return SourceCheckResult.unverified(504,
                    "preflight reached pagination safety limit (20 pages) while looking for " + sourceName);
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Jules source preflight unavailable for {}: {}", sourceName, e.getMessage());
            return SourceCheckResult.unverified(0, "preflight unavailable: " + e.getMessage());
        }
    }

    static String toJulesSourceName(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return repoUrl;
        }
        String source = repoUrl.trim();
        if (source.startsWith("sources/")) {
            return source;
        }
        if (source.startsWith("git@github.com:")) {
            return sourceFromOwnerRepo(source.substring("git@github.com:".length()));
        }
        try {
            URI uri = URI.create(source);
            String host = uri.getHost();
            if (host != null && host.equalsIgnoreCase("github.com")) {
                return sourceFromOwnerRepo(uri.getPath());
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through and return the original value; Jules will surface a precise API error.
        }
        return source;
    }

    private static String sourceFromOwnerRepo(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - ".git".length());
        }
        String[] parts = normalized.split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return path;
        }
        return "sources/github/" + parts[0] + "/" + parts[1];
    }

    private enum SourceAvailability {
        VISIBLE,
        MISSING,
        UNKNOWN
    }

    record SourceCheckResult(SourceAvailability availability, int statusCode, String message) {
        static SourceCheckResult visible() {
            return new SourceCheckResult(SourceAvailability.VISIBLE, 200, "visible");
        }

        static SourceCheckResult missing(String sourceName) {
            return new SourceCheckResult(SourceAvailability.MISSING, 404,
                    "jules_source_not_found: " + sourceName
                            + " is not visible in Jules sources. Configure Jules GitHub source access "
                            + "or create the repository under a Jules-visible owner.");
        }

        static SourceCheckResult unverified(int statusCode, String detail) {
            int code = statusCode > 0 ? statusCode : 502;
            return new SourceCheckResult(SourceAvailability.UNKNOWN, code,
                    "jules_source_unverified: " + detail);
        }
    }

    public record CreateSessionResult(
            String sessionName,
            int statusCode,
            String errorBody,
            Integer promptLength,
            String source,
            String startingBranch
    ) {
        public CreateSessionResult(String sessionName, int statusCode, String errorBody) {
            this(sessionName, statusCode, errorBody, null, null, null);
        }

        public static CreateSessionResult failure(int statusCode, String errorBody, int promptLength, String source, String startingBranch) {
            String prefix = String.format("[promptLength=%d, source=%s, startingBranch=%s]", promptLength, source, startingBranch);
            String fullError = (errorBody == null || errorBody.isBlank()) ? prefix : prefix + " " + errorBody;
            return new CreateSessionResult(null, statusCode, fullError, promptLength, source, startingBranch);
        }

        public boolean sourceNotFound() {
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("jules_source_not_found");
        }

        public boolean sourceUnverified() {
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("jules_source_unverified");
        }

        /**
         * Law 14 (Popper / Carnap / Gerdenfors): explicit concurrent-session capacity refusal from Jules API.
         * Must be distinct from daily limit and unclassified precondition errors.
         */
        public boolean concurrentCapacityExhausted() {
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("concurrent")
                    || lower.contains("too many open sessions")
                    || lower.contains("too many active sessions")
                    || lower.contains("active session limit")
                    || lower.contains("concurrent session limit")
                    || lower.contains("concurrency limit")
                    || lower.contains("maximum concurrent");
        }

        public boolean dailyLimitOrQuota() {
            if (concurrentCapacityExhausted()) {
                return false;
            }
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return statusCode == 429
                    || lower.contains("quota")
                    || lower.contains("daily")
                    || lower.contains("rate limit")
                    || lower.contains("resource_exhausted");
        }

        /**
         * The refusal is about OUR request, not about the account (§10, measured 2026-08-29).
         *
         * <p>400 INVALID_ARGUMENT used to be folded in with 401 and 403 below, and the three are not the
         * same fact: 401/403 say the account may not do this, 400 says we asked wrongly. Lumping them
         * meant a malformed request was recorded against whichever account happened to carry it, and with
         * an escalation threshold of two, one bad request burned three accounts in a single tick - the
         * factory destroying its own constraint through the mechanism that exists to use it.
         *
         * <p>An authorization word in the body still wins: a 400 that says "permission denied" is about
         * the account whatever its status code claims.
         */
        public boolean requestRejected() {
            if (concurrentCapacityExhausted()) {
                return false;
            }
            // §12: narrowed to the status Jules itself declares. The first version took any 400 without an
            // authorization word, which swallowed FAILED_PRECONDITION - a statement about the ACCOUNT being
            // at its concurrent-session limit, not about our request. Measured 2026-08-29: 45 refusals in
            // fifteen minutes, all FAILED_PRECONDITION, all misfiled as the factory's own defect.
            return statusCode == 400 && declaredStatusIs("INVALID_ARGUMENT");
        }

        /**
         * A precondition failed and Jules did not say which (§12).
         *
         * <p>Not a claim about anybody. Measured 2026-08-29: 41 refusals carrying exactly
         * {@code "Precondition check failed."} and no detail. The same status also arrives with
         * {@code "Repository access is not ready"} - a real, account-side condition recorded by an earlier
         * incident and asserted by JulesDispatchServiceTest - so the status alone distinguishes nothing.
         *
         * <p>The first version of §12 read these as the account's concurrent-session ceiling, on the
         * strength of the Jules UI saying "you can run up to 3 sessions at once" while they happened. That
         * is an inference from a different surface, not a measurement of this answer, and this plan's own
         * rule forbids it. Recorded ignorance instead - the shape §3 already uses for UNDECIDABLE and §4.1
         * for NEITHER.
         */
        public boolean preconditionUnspecified() {
            if (concurrentCapacityExhausted()) {
                return false;
            }
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return statusCode == 400
                    && lower.contains("failed_precondition")
                    && !apiPreconditionOrAuthorizationBlocked();
        }

        private boolean declaredStatusIs(String status) {
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            boolean authorizationFlavoured = lower.contains("permission_denied")
                    || lower.contains("unauthorized")
                    || lower.contains("forbidden")
                    || lower.contains("access denied");
            return !authorizationFlavoured && lower.contains(status.toLowerCase(java.util.Locale.ROOT));
        }

        /**
         * The account itself may not do this: credentials or permissions (§12).
         *
         * <p>The BARE "failed_precondition" is gone from here (§12): it names no condition, so it cannot
         * establish that the account is at fault, and charging it a cooldown of hours on that basis was a
         * claim without evidence. Named account-side preconditions stay - "Repository access is not ready"
         * is a real one, recorded by an earlier incident and asserted by JulesDispatchServiceTest.
         */
        public boolean apiPreconditionOrAuthorizationBlocked() {
            if (concurrentCapacityExhausted()) {
                return false;
            }
            String lower = errorBody == null ? "" : errorBody.toLowerCase(java.util.Locale.ROOT);
            return statusCode == 401
                    || statusCode == 403
                    || lower.contains("permission_denied")
                    || lower.contains("unauthorized")
                    || lower.contains("forbidden")
                    || lower.contains("access denied")
                    // Named account-side preconditions only. The bare status is not one of them (§12) -
                    // it is claimed by preconditionUnspecified above, which claims nothing.
                    || lower.contains("repository access is not ready")
                    || lower.contains("repository access");
        }

        /**
         * Law 14: Partitioning of external refusal into mutually exclusive outcome.
         * Unclassified falls back to UNCLASSIFIED instead of guessing into a wrong bucket.
         */
        public com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome classifyOutcome() {
            if (concurrentCapacityExhausted()) {
                return com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.CONCURRENT_CAPACITY_EXHAUSTED;
            }
            if (dailyLimitOrQuota()) {
                return com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.DAILY_LIMIT;
            }
            if (requestRejected()) {
                return com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.REQUEST_REJECTED;
            }
            if (apiPreconditionOrAuthorizationBlocked()) {
                return com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED;
            }
            if (preconditionUnspecified()) {
                return com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.PRECONDITION_UNSPECIFIED;
            }
            return com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.UNCLASSIFIED;
        }

        public String compactError() {
            if (errorBody == null || errorBody.isBlank()) {
                return "";
            }
            return errorBody.length() <= 500 ? errorBody : errorBody.substring(0, 500);
        }
    }

    public String getSessionStatus(String externalSessionId) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return getSessionStatus(externalSessionId, apiKey);
    }

    public String getSessionStatus(String externalSessionId, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/" + externalSessionId))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules status check failed: status={} body={}", response.statusCode(), response.body());
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            return json.path("state").asText(null);
        } catch (IOException | InterruptedException e) {
            log.error("Error getting Jules session status", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    public JsonNode getSessionActivities(String externalSessionId, String apiKey) {
        return getSessionActivities(externalSessionId, apiKey, 0, null);
    }

    /**
     * 2026-08-03 (diagnosing a live blind-cycle incident, test-forty-first): the unparameterized call above
     * always asked Jules for its default page instead of ever paging through a session's full activity
     * history - see JulesDispatchService.answerAgentQuestions's overflow branch, which had been treating
     * "this one page didn't fit under maxActivitiesResponseBytes" as reason to skip the whole session for a
     * cycle, when a single page can already be large purely from embedded artifacts (git diffs, base64
     * screenshots, bash output - confirmed via the real Activity schema) regardless of session length.
     * pageSize &lt;= 0 keeps the old default-page behavior; pageToken null requests the first page.
     */
    public JsonNode getSessionActivities(String externalSessionId, String apiKey, int pageSize, String pageToken) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return null;
        }

        try {
            StringBuilder path = new StringBuilder("/" + normalizeSessionPath(externalSessionId) + "/activities");
            java.util.List<String> params = new java.util.ArrayList<>();
            if (pageSize > 0) {
                params.add("pageSize=" + pageSize);
            }
            if (pageToken != null && !pageToken.isBlank()) {
                params.add("pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
            }
            if (!params.isEmpty()) {
                path.append("?").append(String.join("&", params));
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules activities fetch failed: status={}", response.statusCode());
                return null;
            }

            byte[] body = readLimited(response.body(), maxActivitiesResponseBytes);
            if (body == null) {
                log.warn("Jules activities response for session {} exceeded {} bytes; skipping activity scan to protect backend memory", externalSessionId, maxActivitiesResponseBytes);
                ObjectNode overflow = objectMapper.createObjectNode();
                overflow.put("activitiesOverflow", true);
                overflow.put("maxBytes", maxActivitiesResponseBytes);
                return overflow;
            }
            return objectMapper.readTree(body);
        } catch (IOException | InterruptedException e) {
            log.error("Error getting Jules session activities", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private byte[] readLimited(InputStream inputStream, int maxBytes) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    public String getSessionPrUrl(String externalSessionId) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return getSessionPrUrl(externalSessionId, apiKey);
    }

    public String getSessionPrUrl(String externalSessionId, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/" + externalSessionId))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            return findPrUrl(json);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean sendMessage(String externalSessionId, String message) {
        String apiKey = settingsService.effectiveValue("jules_api_key");
        return sendMessage(externalSessionId, message, apiKey);
    }

    public boolean sendMessage(String externalSessionId, String message, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank() || "skipped".equals(externalSessionId) || externalSessionId == null) {
            return false;
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("prompt", message);

            // Constructing URL for sessions/{session}:sendMessage
            // Note: externalSessionId usually starts with "sessions/"
            String url = apiBaseUrl + "/" + externalSessionId + ":sendMessage";
            if (!externalSessionId.startsWith("sessions/")) {
                url = apiBaseUrl + "/sessions/" + externalSessionId + ":sendMessage";
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jules send message failed: status={} body={}", response.statusCode(), response.body());
                return false;
            }

            return true;
        } catch (IOException | InterruptedException e) {
            log.error("Error sending message to Jules session", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private String findPrUrl(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) {
            String text = node.asText();
            if (text.contains("github.com") && text.contains("/pull/")) {
                return text;
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String found = findPrUrl(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * DELETE /v1alpha/sessions/{session} (2026-08-01, operator: "те сессии джулс, которые не продуктовые...
     * можно убирать в архив или удалять"). Confirmed against Jules's own public API reference
     * (https://jules.google/docs/api/reference/sessions/) - a real, documented endpoint our own
     * cancelSession/Branch-GC retirement never called (both are purely local bookkeeping; Jules itself was
     * never told a "cancelled" session is done). Whether this actually relieves any account-level quota is
     * NOT documented - this is a verification/manual-use capability, not yet wired into any automatic flow.
     */
    public record DeleteSessionResult(boolean success, int statusCode, String errorBody) {}

    /**
     * Raw GET on a session, returning the real HTTP status code - unlike {@link #getSessionStatus} (which
     * swallows the status code entirely, returning null for both "genuinely deleted" and "network hiccup"),
     * this exists specifically to distinguish a real 404 (proof of deletion) from any other failure.
     */
    public record RawSessionCheckResult(int statusCode, String body) {}

    public RawSessionCheckResult checkSessionRaw(String externalSessionId, String apiKey) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/" + normalizeSessionPath(externalSessionId)))
                    .header("X-Goog-Api-Key", apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new RawSessionCheckResult(response.statusCode(), response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new RawSessionCheckResult(-1, e.getMessage());
        }
    }

    public DeleteSessionResult deleteSession(String externalSessionId, String apiKey) {
        if (!settingsService.effectiveBoolean("jules_enabled") || apiKey == null || apiKey.isBlank()
                || externalSessionId == null || "skipped".equals(externalSessionId)) {
            return new DeleteSessionResult(false, 0, "jules disabled or missing api key/session id");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/" + normalizeSessionPath(externalSessionId)))
                    .header("X-Goog-Api-Key", apiKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok) {
                log.warn("Jules delete session failed: status={} body={}", response.statusCode(), response.body());
            }
            return new DeleteSessionResult(ok, response.statusCode(), response.body());
        } catch (IOException | InterruptedException e) {
            log.error("Error deleting Jules session", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new DeleteSessionResult(false, -1, e.getMessage());
        }
    }

    private String normalizeSessionPath(String externalSessionId) {
        return externalSessionId.startsWith("sessions/")
                ? externalSessionId
                : "sessions/" + externalSessionId;
    }

    public boolean isEnabled() {
        return settingsService.effectiveBoolean("jules_enabled");
    }
}
