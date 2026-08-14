package com.eneik.production.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.eneik.production.models.persistence.LeanValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.eneik.production.services.settings.SystemSettingsService;

@Service
public class MLPredictionServiceClient {
    private static final Logger LOGGER = Logger.getLogger(MLPredictionServiceClient.class.getName());

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;
    private final SystemSettingsService settingsService;
    private final com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker;

    public MLPredictionServiceClient(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${ml.service.url}") String mlServiceUrl,
                                     SystemSettingsService settingsService,
                                     com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.mlServiceUrl = mlServiceUrl;
        this.settingsService = settingsService;
        this.aiHealthTracker = aiHealthTracker;
    }

    private String getGeminiApiKey() {
        if (settingsService != null) {
            try {
                return settingsService.effectiveValue("gemini_api_key");
            } catch (Exception e) {
                // Ignore
            }
        }
        return "";
    }

    private boolean geminiEnabled() {
        if (settingsService == null) {
            return true;
        }
        try {
            return settingsService.effectiveBoolean("gemini_enabled");
        } catch (Exception e) {
            return true;
        }
    }

    private String modelOverrideForTier(String modelTier) {
        if (settingsService == null) {
            return "";
        }
        try {
            boolean pro = modelTier != null && "pro".equalsIgnoreCase(modelTier.trim());
            String primary = settingsService.effectiveValue(pro ? "gemini_pro_model" : "gemini_model");
            String fallback = settingsService.effectiveValue(pro ? "gemini_pro_fallback_models" : "gemini_fallback_models");
            StringBuilder candidates = new StringBuilder();
            if (primary != null && !primary.isBlank()) {
                candidates.append(primary.trim());
            }
            if (fallback != null && !fallback.isBlank()) {
                if (!candidates.isEmpty()) {
                    candidates.append(',');
                }
                candidates.append(fallback.trim());
            }
            return candidates.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public boolean checkSystemRisk(int activeTasks, double currentCycleTime) {
        String endpoint = mlServiceUrl + "/api/v1/predict/bottleneck";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("wip_count", activeTasks);
            request.put("avg_cycle_time", currentCycleTime);

            MLResponse response = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), MLResponse.class);
            return response != null && response.isBottleneckPredicted();
        } catch (Exception e) {
            LOGGER.warning("ML service call failed: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Object> predictBottleneck(int wipCount, double avgCycleTime) {
        boolean bottleneckPredicted = checkSystemRisk(wipCount, avgCycleTime);
        return Map.of("is_bottleneck_predicted", bottleneckPredicted);
    }


    /**
     * Embeds free text via the ML service's Gemini embedding passthrough - the retrieval half of
     * GeminiContextService's RAG pipeline (2026-07-25). Returns null (never a zero-length/garbage vector)
     * on any failure - Gemini disabled, no API key, network error, malformed response - so callers can
     * treat "no vector" as an unambiguous, safe no-op signal rather than guessing from an empty array.
     */
    public float[] embed(String text) {
        if (!geminiEnabled()) {
            aiHealthTracker.recordFailure("embed", "gemini disabled by setting");
            return null;
        }
        String endpoint = mlServiceUrl + "/api/v1/embed";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("text", text);
            request.put("apiKey", getGeminiApiKey());

            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
            Object rawEmbedding = response == null ? null : response.get("embedding");
            if (!(rawEmbedding instanceof List<?> values) || values.isEmpty()) {
                aiHealthTracker.recordFailure("embed", "invalid or empty embedding response");
                return null;
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = ((Number) values.get(i)).floatValue();
            }
            aiHealthTracker.recordSuccess("embed");
            return vector;
        } catch (Exception e) {
            LOGGER.warning("ML service embed call failed: " + e.getMessage());
            aiHealthTracker.recordFailure("embed", e.getMessage());
            return null;
        }
    }

    public String chat(String prompt, String systemInstruction) {
        return chatWithTier(prompt, systemInstruction, "", "");
    }

    /**
     * Explicit Gemini context caching (2026-07-25) - for a caller whose systemInstruction is static/
     * repeated across many calls under the same cacheKey (e.g. GeminiProjectObserverService's instruction
     * text, identical every 30-minute cycle for every project). Only the systemInstruction is cached -
     * anything call-specific (RAG-retrieved context, evidence snapshots) must stay in prompt, never here,
     * or it would be cached stale. Same fail-open contract as everywhere else this pattern is used: any
     * caching problem falls back to the plain uncached call, never becomes a new failure mode.
     */
    public String chat(String prompt, String systemInstruction, String cacheKey) {
        return chatWithTier(prompt, systemInstruction, "", cacheKey);
    }

    /**
     * "pro" tier requested here is silently downgraded to the regular model list server-side (2026-07-25,
     * operator directive - emergency cost incident: "никогда не вызывать про версию"). Enforced at the
     * single choke point (PredictionService.py's gemini_candidate_models), not here, so it holds even for
     * any other caller of the ML service's chat endpoint, present or future.
     */
    public String chatCritical(String prompt, String systemInstruction) {
        return chatWithTier(prompt, systemInstruction, "pro", "");
    }

    private String chatWithTier(String prompt, String systemInstruction, String modelTier, String cacheKey) {
        if (!geminiEnabled()) {
            aiHealthTracker.recordFailure("chat", "gemini disabled by setting");
            return "The assistant is temporarily unavailable. Gemini disabled by incident-control setting.";
        }
        String endpoint = mlServiceUrl + "/api/v1/assistant/chat";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("prompt", prompt);
            request.put("systemInstruction", systemInstruction);
            request.put("apiKey", getGeminiApiKey());
            if (modelTier != null && !modelTier.isBlank()) {
                request.put("modelTier", modelTier);
            }
            request.put("modelOverride", modelOverrideForTier(modelTier));
            if (cacheKey != null && !cacheKey.isBlank()) {
                request.put("cacheKey", cacheKey);
            }

            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
            if (response != null && response.containsKey("text")) {
                aiHealthTracker.recordSuccess("chat");
                return (String) response.get("text");
            }
            aiHealthTracker.recordFailure("chat", "invalid response format");
            return "ERROR: Invalid AI assistant response format.";
        } catch (Exception e) {
            LOGGER.severe("ML service chat call failed: " + e.getMessage());
            aiHealthTracker.recordFailure("chat", e.getMessage());
            return "The assistant is temporarily unavailable. ML service connection error: " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Phase 5 (2026-08-05): a real multi-round tool-use loop, replacing the single-shot text-in/text-out
    // contract above for callers that need it (first caller: GeminiProjectObserverService). Java owns the
    // loop deliberately (confirmed via investigation before building this): PredictionService.py's
    // ask_gemini already owns useful, proven infrastructure (model-tier candidate fallback, transient-error
    // retry, mock-response short-circuiting with no API key) that a second Python-side Gemini client would
    // have forked; the Python endpoint here stays a thin one-round-per-call passthrough, this class owns
    // round-to-round state and termination.
    // ---------------------------------------------------------------------------------------------------

    public record ToolDeclaration(String name, String description, Map<String, Object> parameters) {}

    @FunctionalInterface
    public interface ToolExecutor {
        Map<String, Object> execute(String toolName, Map<String, Object> args);
    }

    /**
     * Called after each tool round to decide whether the loop keeps going. This is deliberately NOT left to
     * the model's own judgment alone - a pure "does the model feel it learned something new" signal is
     * exactly the self-referential "isolation" failure mode coherentism is criticized for (an LLM can always
     * generate a plausible-sounding "yes, one more look" even when nothing real changed - this system has
     * lived incidents of exactly that: duplicate decomposition, the self-perpetuating falsification-refusal
     * loop). The caller supplies a real, external signal instead (e.g. did this round's tool result contain
     * any evidence id not already seen this cycle; has the objective coherence_runs.coherence_score
     * stabilized).
     */
    @FunctionalInterface
    public interface ToolLoopContinuation {
        boolean shouldContinue(int roundNumber, String toolName, Map<String, Object> toolResult);
    }

    public record ToolLoopResult(String finalText, int roundsUsed, boolean hitRoundCap) {}

    /**
     * @param initialPrompt   the first-turn user message.
     * @param systemInstruction static instruction (not cached here - a growing multi-turn conversation isn't
     *                        the "static, repeated" shape context caching was built for).
     * @param tools           the read-only functions the model may call this conversation.
     * @param executor        dispatches an actual tool call to real backend data.
     * @param continuation    the caller's own external termination signal (see doc above).
     * @param maxRounds       hard backstop against a runaway loop - a safety cap, not a claim about how many
     *                        rounds of "why" are correct.
     */
    public ToolLoopResult chatWithTools(String initialPrompt, String systemInstruction,
                                         List<ToolDeclaration> tools, ToolExecutor executor,
                                         ToolLoopContinuation continuation, int maxRounds) {
        if (!geminiEnabled()) {
            aiHealthTracker.recordFailure("chatWithTools", "gemini disabled by setting");
            return new ToolLoopResult("The assistant is temporarily unavailable. Gemini disabled by incident-control setting.", 0, false);
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", initialPrompt))));

        int round = 0;
        while (round < maxRounds) {
            round++;
            Map<String, Object> response = callChatEndpoint(systemInstruction, tools, contents);
            if (response == null) {
                return new ToolLoopResult("The assistant is temporarily unavailable. ML service connection error.", round, false);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> updatedContents = (List<Map<String, Object>>) response.get("contents");
            contents = new ArrayList<>(updatedContents);

            @SuppressWarnings("unchecked")
            Map<String, Object> functionCall = (Map<String, Object>) response.get("functionCall");
            if (functionCall == null) {
                aiHealthTracker.recordSuccess("chatWithTools");
                return new ToolLoopResult((String) response.getOrDefault("text", ""), round, false);
            }

            String toolName = (String) functionCall.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) functionCall.getOrDefault("args", Map.of());
            Map<String, Object> toolResult = executor.execute(toolName, args);
            contents.add(Map.of("role", "user", "parts", List.of(
                    Map.of("functionResponse", Map.of("name", toolName, "response", toolResult)))));

            boolean keepGoing = continuation == null || continuation.shouldContinue(round, toolName, toolResult);
            if (!keepGoing) {
                // One final, tools-free round so the model can synthesize a real closing answer from
                // everything gathered, instead of the loop just stopping mid-tool-call with no conclusion.
                round++;
                Map<String, Object> finalResponse = callChatEndpoint(systemInstruction, List.of(), contents);
                aiHealthTracker.recordSuccess("chatWithTools");
                String finalText = finalResponse == null ? "" : (String) finalResponse.getOrDefault("text", "");
                return new ToolLoopResult(finalText, round, false);
            }
        }
        aiHealthTracker.recordFailure("chatWithTools", "hit max rounds (" + maxRounds + ") without a final answer");
        return new ToolLoopResult("", round, true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callChatEndpoint(String systemInstruction, List<ToolDeclaration> tools,
                                                   List<Map<String, Object>> contents) {
        String endpoint = mlServiceUrl + "/api/v1/assistant/chat";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("systemInstruction", systemInstruction);
            request.put("apiKey", getGeminiApiKey());
            request.put("contents", contents);
            List<Map<String, Object>> toolDtos = new ArrayList<>();
            for (ToolDeclaration tool : tools) {
                toolDtos.add(Map.of("name", tool.name(), "description", tool.description(), "parameters", tool.parameters()));
            }
            request.put("tools", toolDtos);

            return restTemplate.postForObject(endpoint, new HttpEntity<>(request, headers), Map.class);
        } catch (Exception e) {
            LOGGER.severe("ML service tool-chat call failed: " + e.getMessage());
            aiHealthTracker.recordFailure("chatWithTools", e.getMessage());
            return null;
        }
    }


    /**
     * One atomic task-slice within an эпик (see {@link EpicPlan}). Ф8 (2026-07-21, operator directive):
     * kanoClass moved OFF this record entirely - Kano is a customer-value classification, meaningful at
     * the эпик level only, never per-task. A task's own {@code jtbd} is scoped to the эпик it belongs to
     * ("when implementing X for this эпик, I want Y so the эпик's Z works"), NOT the end customer - the
     * customer-facing JTBD lives on {@link EpicPlan#jtbd()}. sixSigmaMetric/tocConstraintRef stay here too
     * (operator decision: both levels carry their own - the эпик's is an aggregate business metric, the
     * task's is its own technical one).
     */
    public record TaskSliceMetadata(
            String title,
            String jtbd,
            String acceptanceCriteria,
            String roleTag,
            LeanValue leanValue,
            String cynefinDomain,
            String tocConstraintRef,
            String sixSigmaMetric,
            boolean hasUi,
            List<String> requirementRefs
    ) {
        public TaskSliceMetadata(String title, String jtbd, String acceptanceCriteria, String roleTag,
                LeanValue leanValue, String cynefinDomain, String tocConstraintRef,
                String sixSigmaMetric, boolean hasUi) {
            this(title, jtbd, acceptanceCriteria, roleTag, leanValue, cynefinDomain,
                    tocConstraintRef, sixSigmaMetric, hasUi, List.of());
        }
    }

    /**
     * Ф8 (2026-07-21, operator directive): a wishlist splits into as many эпики (epics) as the product
     * actually needs, by narrative/theme - never assumed to be exactly one. Every compile cycle (not just
     * the first) must decide per эпик, semantically, whether it matches an ALREADY-EXISTING эпик in the
     * project (existingEpicId non-null, echoed back from the candidate list handed to the compiler prompt
     * - see ProjectFlowService.existingEpicsPromptContext) or is genuinely new (existingEpicId null).
     */
    public record EpicPlan(
            String existingEpicId,
            String title,
            String jtbd,
            String kanoClass,
            String cynefinDomain,
            String sixSigmaMetric,
            String tocConstraintRef,
            // Which "Brief #N" (0-indexed, matching the numbered briefs sent in a batched compiler prompt -
            // see ProjectFlowService.wishlistCompilerPromptBatch) this эпик was derived from. A solo
            // (non-batched) compile always uses 0. Every slice inside one эпик shares this same value -
            // an эпик is never split across two different source briefs.
            int sourceIndex,
            List<String> requirements,
            boolean coverageComplete,
            List<TaskSliceMetadata> slices
    ) {
        public EpicPlan(String existingEpicId, String title, String jtbd, String kanoClass,
                String cynefinDomain, String sixSigmaMetric, String tocConstraintRef,
                int sourceIndex, List<TaskSliceMetadata> slices) {
            this(existingEpicId, title, jtbd, kanoClass, cynefinDomain, sixSigmaMetric,
                    tocConstraintRef, sourceIndex, List.of(), false, slices);
        }
    }

    private static class MLResponse {
        @JsonProperty("risk_score")
        private double riskScore;

        @JsonProperty("is_bottleneck_predicted")
        private boolean bottleneckPredicted;

        public double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(double riskScore) {
            this.riskScore = riskScore;
        }

        public boolean isBottleneckPredicted() {
            return bottleneckPredicted;
        }

        public void setBottleneckPredicted(boolean bottleneckPredicted) {
            this.bottleneckPredicted = bottleneckPredicted;
        }
    }
}
