package com.eneik.production.services.worker.agy;

import com.eneik.production.services.worker.deepseek.DeepSeekPatchParser;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses raw stdout output from the local agy CLI process.
 * Extracts the payload and delegates to DeepSeekPatchParser to enforce the Popperian Anti-Truncation Gate (D008).
 */
@Component
public class LocalAgyOutputParser {

    private static final Logger log = LoggerFactory.getLogger(LocalAgyOutputParser.class);

    private final DeepSeekPatchParser patchParser;

    public LocalAgyOutputParser(DeepSeekPatchParser patchParser) {
        this.patchParser = patchParser != null ? patchParser : new DeepSeekPatchParser();
    }

    public LocalAgyOutputParser() {
        this(new DeepSeekPatchParser());
    }

    /**
     * Parses raw output from agy CLI and constructs a sovereign DeepSeekResponse.
     * Enforces Anti-Truncation Gate and validates file operations.
     */
    public DeepSeekResponse parse(String rawOutput, String model) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new IllegalArgumentException("agy CLI output is empty or null");
        }

        String jsonCandidate = sanitizeOutput(rawOutput);
        // Estimate token counts from character lengths for accounting
        int approxPromptTokens = 0;
        int approxCompletionTokens = jsonCandidate.length() / 4;

        return patchParser.parse(jsonCandidate, 0, approxPromptTokens, approxCompletionTokens, model);
    }

    /**
     * Cleans up CLI headers, spinners, ANSI escape codes, or wrapper noise before JSON parsing.
     */
    public String sanitizeOutput(String output) {
        if (output == null) {
            return "";
        }
        // Strip ANSI escape codes
        String stripped = output.replaceAll("\u001B\\[[;\\d]*m", "").trim();

        // If enclosed in markdown code fences, locate the fence
        if (stripped.contains("```json")) {
            int start = stripped.indexOf("```json");
            int end = stripped.lastIndexOf("```");
            if (end > start + 7) {
                return stripped.substring(start + 7, end).trim();
            }
        } else if (stripped.contains("```")) {
            int start = stripped.indexOf("```");
            int end = stripped.lastIndexOf("```");
            if (end > start + 3) {
                return stripped.substring(start + 3, end).trim();
            }
        }

        // If no fence, isolate first '{' and matching '}'
        int firstBrace = stripped.indexOf('{');
        int lastBrace = stripped.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return stripped.substring(firstBrace, lastBrace + 1).trim();
        }

        return stripped;
    }
}
