package com.eneik.production.services.worker.deepseek;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.*;

/**
 * Sovereign DeepSeek API Client with Context Caching and Fallback-Clutch (GEMINI.md §0).
 * Implements Dennett's 'Cranes, not Skyhooks' resilience against transient network/provider failures.
 */
@Component
public class DeepSeekApiClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekApiClient.class);

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String CHAT_MODEL = "deepseek-chat";
    public static final String REASONER_MODEL = "deepseek-reasoner";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DeepSeekPatchParser patchParser;
    private final SystemSettingsService settingsService;

    // Fixed BARCAN-TAG Ontological prefix to guarantee server-side Context Caching (>1024 tokens)
    private static final String BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX = buildBarcanTagOntologyPrefix();

    public DeepSeekApiClient() {
        this(new ObjectMapper(), new DeepSeekPatchParser(), null, null);
    }

    @Autowired
    public DeepSeekApiClient(ObjectMapper objectMapper,
                             DeepSeekPatchParser patchParser,
                             @Autowired(required = false) SystemSettingsService settingsService) {
        this(objectMapper, patchParser, settingsService, null);
    }

    public DeepSeekApiClient(ObjectMapper objectMapper,
                             DeepSeekPatchParser patchParser,
                             SystemSettingsService settingsService,
                             HttpClient httpClient) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.patchParser = patchParser != null ? patchParser : new DeepSeekPatchParser(this.objectMapper);
        this.settingsService = settingsService;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Resolves target model by Cynefin domain (Dave Snowden framework).
     * 'complex' -> deepseek-reasoner (R1)
     * 'simple', 'clear', 'complicated' -> deepseek-chat (V3)
     */
    public String resolveModel(String cynefinDomain) {
        if ("complex".equalsIgnoreCase(cynefinDomain)) {
            String customReasoner = settingsService != null ? settingsService.effectiveValue("deepseek_reasoner_model") : null;
            return (customReasoner != null && !customReasoner.isBlank()) ? customReasoner : REASONER_MODEL;
        }
        String customChat = settingsService != null ? settingsService.effectiveValue("deepseek_chat_model") : null;
        return (customChat != null && !customChat.isBlank()) ? customChat : CHAT_MODEL;
    }

    /**
     * Generates code patch for a task with Context Caching and Fallback-Clutch.
     */
    public DeepSeekResponse generateSolution(TaskEntity task,
                                             String fileContentsContext,
                                             AccountEntity account,
                                             String previousReflectionFeedback) throws Exception {
        String apiKey = resolveApiKey(account);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API key is missing for account " + (account != null ? account.getName() : "default"));
        }

        String cynefin = task.getCynefinDomain();
        String model = resolveModel(cynefin);
        Duration readTimeout = REASONER_MODEL.equalsIgnoreCase(model) ? Duration.ofSeconds(180) : Duration.ofSeconds(90);

        String systemPrompt = buildSystemPrompt(task);
        String userPrompt = buildUserPrompt(task, fileContentsContext, previousReflectionFeedback);

        String baseUrl = resolveBaseUrl();
        String fallbackUrl = resolveFallbackUrl();

        try {
            return executeCall(baseUrl, apiKey, model, systemPrompt, userPrompt, readTimeout);
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackUrl != null && !fallbackUrl.isBlank()) {
                log.warn("DeepSeek primary endpoint [{}] failed ({}); activating Fallback-Clutch to [{}]",
                        baseUrl, e.getMessage(), fallbackUrl);
                String fallbackKey = resolveFallbackApiKey(apiKey);
                return executeCall(fallbackUrl, fallbackKey, model, systemPrompt, userPrompt, readTimeout);
            }
            throw e;
        }
    }

    private DeepSeekResponse executeCall(String endpointBaseUrl,
                                         String apiKey,
                                         String model,
                                         String systemPrompt,
                                         String userPrompt,
                                         Duration readTimeout) throws Exception {
        String url = endpointBaseUrl.endsWith("/chat/completions")
                ? endpointBaseUrl
                : (endpointBaseUrl.endsWith("/") ? endpointBaseUrl + "chat/completions" : endpointBaseUrl + "/chat/completions");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", REASONER_MODEL.equalsIgnoreCase(model) ? 0.6 : 0.2);
        payload.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        payload.put("messages", messages);

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(readTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("DeepSeek network call failed to {}: {}", url, e.getMessage());
            throw e;
        }

        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode == 401 || statusCode == 402 || statusCode == 429) {
            log.error("DeepSeek API auth/quota error {}: {}", statusCode, responseBody);
            throw new DeepSeekQuotaExhaustedException("DeepSeek API returned HTTP " + statusCode + ": " + responseBody, statusCode);
        }

        if (statusCode >= 500) {
            log.error("DeepSeek API server error {}: {}", statusCode, responseBody);
            throw new IOException("DeepSeek API returned HTTP " + statusCode + ": " + responseBody);
        }

        if (statusCode != 200) {
            throw new IOException("DeepSeek API call failed with status " + statusCode + ": " + responseBody);
        }

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choiceNode = root.path("choices").path(0);
        String content = choiceNode.path("message").path("content").asText();

        JsonNode usageNode = root.path("usage");
        int promptTokens = usageNode.path("prompt_tokens").asInt(0);
        int completionTokens = usageNode.path("completion_tokens").asInt(0);

        int cachedTokens = 0;
        if (usageNode.has("prompt_cache_hit_tokens")) {
            cachedTokens = usageNode.path("prompt_cache_hit_tokens").asInt(0);
        } else if (usageNode.path("prompt_tokens_details").has("cached_tokens")) {
            cachedTokens = usageNode.path("prompt_tokens_details").path("cached_tokens").asInt(0);
        }

        return patchParser.parse(content, cachedTokens, promptTokens, completionTokens, model);
    }

    private boolean isFallbackEligible(Throwable t) {
        if (t instanceof HttpTimeoutException || t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException) {
            return true;
        }
        if (t instanceof IOException && t.getMessage() != null && t.getMessage().contains("HTTP 5")) {
            return true;
        }
        return false;
    }

    public String buildSystemPrompt(TaskEntity task) {
        StringBuilder sb = new StringBuilder();
        // Constant prefix ensuring server-side Context Caching hit (>1024 tokens)
        sb.append(BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX);

        sb.append("\n## Active Task Execution Context\n");
        if (task != null) {
            if (task.getRole() != null) {
                sb.append("Role Tag: ").append(task.getRole().getTag()).append("\n");
                sb.append("Role Description: ").append(task.getRole().getDescription()).append("\n");
            }
            if (task.getCynefinDomain() != null) {
                sb.append("Cynefin Domain: ").append(task.getCynefinDomain()).append("\n");
            }
            if (task.getFileScope() != null) {
                sb.append("DEONTIC FILE SCOPE (RAZ D006 - STRICT PROHIBITION): ").append(task.getFileScope()).append("\n");
                sb.append("You are strictly prohibited from modifying or creating ANY file outside this scope.\n");
            }
        }
        return sb.toString();
    }

    public String buildUserPrompt(TaskEntity task, String fileContentsContext, String reflectionFeedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("### TASK DEFINITION\n");
        if (task != null) {
            sb.append("Title: ").append(task.getTitle()).append("\n");
            sb.append("Description / Definition of Done:\n").append(task.getDescription()).append("\n");
            sb.append("Declared File Scope: ").append(task.getFileScope()).append("\n\n");
        }

        if (fileContentsContext != null && !fileContentsContext.isBlank()) {
            sb.append("### EXISTING FILE CONTENTS IN SCOPE:\n");
            sb.append(fileContentsContext).append("\n\n");
        }

        if (reflectionFeedback != null && !reflectionFeedback.isBlank()) {
            sb.append("### PREVIOUS ATTEMPT FALSIFICATION FEEDBACK (POPPER D008 / RAZ D006):\n");
            sb.append(reflectionFeedback).append("\n");
            sb.append("Analyze the error and provide an updated, corrected patch.\n\n");
        }

        sb.append("Provide your response strictly in the required JSON format.\n");
        return sb.toString();
    }

    private String resolveBaseUrl() {
        if (settingsService != null) {
            String url = settingsService.effectiveValue("deepseek_base_url");
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        String envUrl = System.getenv("DEEPSEEK_BASE_URL");
        return (envUrl != null && !envUrl.isBlank()) ? envUrl : DEFAULT_BASE_URL;
    }

    private String resolveFallbackUrl() {
        if (settingsService != null) {
            String url = settingsService.effectiveValue("deepseek_fallback_url");
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return System.getenv("DEEPSEEK_FALLBACK_URL");
    }

    private String resolveApiKey(AccountEntity account) {
        if (account != null && account.getApiKey() != null && !account.getApiKey().isBlank()) {
            return account.getApiKey();
        }
        if (settingsService != null) {
            String key = settingsService.effectiveValue("deepseek_api_key");
            if (key != null && !key.isBlank()) {
                return key;
            }
        }
        return System.getenv("DEEPSEEK_API_KEY");
    }

    private String resolveFallbackApiKey(String primaryKey) {
        if (settingsService != null) {
            String key = settingsService.effectiveValue("deepseek_fallback_api_key");
            if (key != null && !key.isBlank()) {
                return key;
            }
        }
        String envKey = System.getenv("DEEPSEEK_FALLBACK_API_KEY");
        return (envKey != null && !envKey.isBlank()) ? envKey : primaryKey;
    }

    public static String getOntologicalCachePrefix() {
        return BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX;
    }

    private static String buildBarcanTagOntologyPrefix() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ENEIK PRODUCTION SYSTEM: SOVEREIGN AUTONOMOUS WORKER ONTOLOGY\n");
        sb.append("## INVARIANTS AND CORE CHARTERS (BARCAN-TAG-00..12)\n\n");
        sb.append("You are an autonomous engineering worker in the sovereign EneikProductionSys ecosystem.\n");
        sb.append("1. SOVEREIGN BOUNDARY INVARIANT (GEMINI.md §0): The Factory (EneikProductionSys) and the Product are separated by an impassable barrier.\n");
        sb.append("   - When writing code for the product, NEVER mention factory orchestrator names ('AutoMergeService', 'SixSigmaAudit', 'Jules', 'TaskPlan', 'EneikSys').\n");
        sb.append("   - NEVER commit factory tracking files (.eneik/, task-plan-*, qa-verification-*).\n\n");

        sb.append("2. RAZ DEONTIC EXCLUSIONARY REASON (D006: PROHIBITION_AS_CODE):\n");
        sb.append("   - The declared fileScope is an absolute second-order exclusionary reason.\n");
        sb.append("   - Any change to any file outside task.fileScope causes instantaneous deterministic rollback.\n");
        sb.append("   - If the task genuinely cannot be completed without files outside the scope, return 'scope_status': 'SCOPE_INSUFFICIENT' with 'missing_files'.\n\n");

        sb.append("3. ANTI-TRUNCATION GATE (POPPER SEVERE TEST D008):\n");
        sb.append("   - NEVER use lazy stop-markers such as '// ... existing code', '// ... rest of implementation', '/* ... */', '// TODO'.\n");
        sb.append("   - Any lazy marker causes IMMEDIATE DISMISSAL and REFLECTION RETRY.\n");
        sb.append("   - For large files (>150 lines), use mode 'PATCH_BLOCKS' with exact 'search' and 'replace' blocks.\n\n");

        sb.append("4. SURGICAL JSON OUTPUT CONTRACT:\n");
        sb.append("You MUST reply with a single valid JSON object strictly matching this schema:\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"<Concise summary of changes>\",\n");
        sb.append("  \"rationale\": \"<Architectural rationale referencing relevant BARCAN-TAG>\",\n");
        sb.append("  \"scope_status\": \"SUFFICIENT\", // or \"SCOPE_INSUFFICIENT\"\n");
        sb.append("  \"missing_files\": [], // list of missing paths if SCOPE_INSUFFICIENT\n");
        sb.append("  \"files\": [\n");
        sb.append("    {\n");
        sb.append("      \"path\": \"src/main/java/com/example/MyClass.java\",\n");
        sb.append("      \"operation\": \"MODIFY\", // CREATE, MODIFY, DELETE\n");
        sb.append("      \"mode\": \"PATCH_BLOCKS\", // or FULL_CONTENT\n");
        sb.append("      \"content\": null, // string with full file content if mode == FULL_CONTENT\n");
        sb.append("      \"patch_blocks\": [\n");
        sb.append("        {\n");
        sb.append("          \"search\": \"<exact original lines to be replaced>\",\n");
        sb.append("          \"replace\": \"<exact new lines to replace with>\"\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("## BARCAN-TAG CHARTERS TAXONOMY:\n");
        sb.append("- BARCAN-TAG-00 (CODE-GUARDIAN): protect architecture, code quality, and merge safety for the smallest slice.\n");
        sb.append("- BARCAN-TAG-01 (SYSTEM-ARCHITECT): define or adjust structure only where the current slice strictly requires it.\n");
        sb.append("- BARCAN-TAG-02 (BACKEND-DEV): implement backend API/data behavior with focused tests and zero frontend creep.\n");
        sb.append("- BARCAN-TAG-03 (UIUX-DESIGNER): produce only the UI/UX design decision, interaction state, or design-system adjustment required.\n");
        sb.append("- BARCAN-TAG-04 (DATA-ENGINEER): implement/verify ML/data logic with reproducible checks.\n");
        sb.append("- BARCAN-TAG-05 (DEVOPS-ENGINEER): change only deployment, runtime, CI, or observability items required to run the slice.\n");
        sb.append("- BARCAN-TAG-06 (QA-VERIFIER): verify acceptance criteria with the smallest useful unit/integration test coverage.\n");
        sb.append("- BARCAN-TAG-07 (SECURITY-AUDITOR): check and fix concrete security risks without broad compliance rewrites.\n");
        sb.append("- BARCAN-TAG-08 (DATABASE-ADMIN): change only the database schema or data pipeline behavior required by this slice.\n");
        sb.append("- BARCAN-TAG-09 (TECHNICAL-LEAD): decompose wishlist context into short, role-owned, dependency-aware work only.\n");
        sb.append("- BARCAN-TAG-10 (COMPLIANCE-LEGAL): verify explicit legal, fiscal, privacy, or policy constraints.\n");
        sb.append("- BARCAN-TAG-11 (FRONTEND-SVELTE): implement the smallest browser UI interaction following clean minimal Bauhaus standards.\n");
        sb.append("- BARCAN-TAG-12 (API-CONTRACTOR): define only the shared API contract (endpoints, request/response shape, DTOs).\n");

        return sb.toString();
    }
}
