package com.eneik.production.services.worker.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Surgical patch parser with Anti-Truncation Gate (GEMINI.md §0, D008 Popper Severe Test).
 * Deterministically rejects lazy stop-markers and supports both FULL_CONTENT and SEARCH_REPLACE patch blocks.
 */
@Component
public class DeepSeekPatchParser {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekPatchParser.class);

    private static final List<String> STOP_MARKERS = List.of(
            "// ... rest of",
            "// ... existing code",
            "/* ... existing code ... */",
            "// TODO: rest of implementation",
            "/* ... */",
            "// ... remainder",
            "// ... rest of implementation",
            "// ... остальной код",
            "// ... existing implementation"
    );

    private final ObjectMapper objectMapper;

    public DeepSeekPatchParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public DeepSeekPatchParser() {
        this(new ObjectMapper());
    }

    /**
     * Parses the LLM JSON response and enforces Anti-Truncation invariants.
     */
    public DeepSeekResponse parse(String jsonContent, int cachedTokens, int promptTokens, int completionTokens, String modelName) {
        if (jsonContent == null || jsonContent.isBlank()) {
            throw new IllegalArgumentException("DeepSeek JSON response is null or empty");
        }

        try {
            // Unquote or extract JSON if LLM wrapped in markdown fences ```json ... ```
            String extractedJson = extractJson(jsonContent);
            // Sanitize unescaped control chars (LF/CR/TAB/<0x20) inside JSON string literals (D003)
            String cleanJson = sanitizeJsonControlChars(extractedJson);
            JsonNode root = objectMapper.readTree(cleanJson);

            String summary = root.path("summary").asText("");
            String rationale = root.path("rationale").asText("");

            String scopeStatusRaw = root.path("scope_status").asText("SUFFICIENT");
            ScopeStatus scopeStatus = "SCOPE_INSUFFICIENT".equalsIgnoreCase(scopeStatusRaw)
                    ? ScopeStatus.SCOPE_INSUFFICIENT
                    : ScopeStatus.SUFFICIENT;

            List<String> missingFiles = new ArrayList<>();
            JsonNode missingNode = root.path("missing_files");
            if (missingNode.isArray()) {
                for (JsonNode m : missingNode) {
                    missingFiles.add(m.asText());
                }
            }

            List<PatchOperation> files = new ArrayList<>();
            JsonNode filesNode = root.path("files");
            if (filesNode.isArray()) {
                for (JsonNode f : filesNode) {
                    String path = f.path("path").asText("");
                    String operation = f.path("operation").asText("MODIFY");
                    String mode = f.path("mode").asText("FULL_CONTENT");
                    String content = f.has("content") && !f.path("content").isNull() ? f.path("content").asText() : null;

                    List<PatchBlock> patchBlocks = new ArrayList<>();
                    JsonNode blocksNode = f.has("patch_blocks") ? f.path("patch_blocks") : f.path("search_replace");
                    if (blocksNode != null && blocksNode.isArray()) {
                        for (JsonNode b : blocksNode) {
                            String search = b.path("search").asText("");
                            String replace = b.path("replace").asText("");
                            checkAntiTruncation(replace, path);
                            patchBlocks.add(new PatchBlock(search, replace));
                        }
                    }

                    if (content != null) {
                        checkAntiTruncation(content, path);
                    }

                    files.add(new PatchOperation(path, operation, mode, content, patchBlocks));
                }
            }

            return new DeepSeekResponse(
                    summary,
                    rationale,
                    scopeStatus,
                    missingFiles,
                    files,
                    cachedTokens,
                    promptTokens,
                    completionTokens,
                    modelName,
                    cleanJson
            );
        } catch (LazyTruncationDetectedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse DeepSeek response JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to parse DeepSeek JSON response: " + e.getMessage(), e);
        }
    }

    /**
     * Anti-Truncation Gate: scans text for lazy abbreviation stop-markers.
     */
    void checkAntiTruncation(String text, String path) {
        if (text == null || text.isBlank()) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : STOP_MARKERS) {
            if (lower.contains(marker.toLowerCase(Locale.ROOT))) {
                log.warn("Anti-Truncation Gate triggered! Found marker '{}' in {}", marker, path);
                throw new LazyTruncationDetectedException(marker, path);
            }
        }
    }

    /**
     * Deterministic state-machine sanitizer (D003 Contract Drift):
     * Escapes raw control characters (< 0x20: LF, CR, TAB, BS, FF, etc.) strictly inside
     * JSON string literals while leaving structural whitespace outside strings and already
     * valid escape sequences (quotes, backslashes, newline/tab, unicode escapes) untouched
     * (strictly idempotent).
     */
    public static String sanitizeJsonControlChars(String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) {
            return rawJson;
        }
        StringBuilder sb = new StringBuilder(rawJson.length() + 32);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < rawJson.length(); i++) {
            char c = rawJson.charAt(i);
            if (!inString) {
                sb.append(c);
                if (c == '"') {
                    inString = true;
                }
            } else if (escaped) {
                if (c < 0x20) {
                    // Escape the preceding backslash so it does not dangle before a control char
                    sb.append('\\');
                    appendEscapedControlChar(sb, c);
                } else {
                    sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                sb.append(c);
                escaped = true;
            } else if (c == '"') {
                sb.append(c);
                inString = false;
            } else if (c < 0x20) {
                appendEscapedControlChar(sb, c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void appendEscapedControlChar(StringBuilder sb, char c) {
        switch (c) {
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            case '\b' -> sb.append("\\b");
            case '\f' -> sb.append("\\f");
            default -> sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
        }
    }

    private String extractJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            int end = trimmed.lastIndexOf("```");
            if (end > 7) {
                return trimmed.substring(7, end).trim();
            }
        } else if (trimmed.startsWith("```")) {
            int end = trimmed.lastIndexOf("```");
            if (end > 3) {
                return trimmed.substring(3, end).trim();
            }
        }
        // If not in code fence, find first '{' and last '}'
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }
}
