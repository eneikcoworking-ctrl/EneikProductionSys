package com.eneik.production.services.worker.agy;

import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.deepseek.LazyTruncationDetectedException;
import com.eneik.production.services.worker.deepseek.ScopeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalAgyOutputParserTest {

    private final LocalAgyOutputParser parser = new LocalAgyOutputParser();

    @Test
    @DisplayName("Parses valid markdown-wrapped JSON response from agy CLI")
    void testParseValidMarkdownWrappedJson() {
        String rawOutput = """
                Loaded cached credentials for profile remotecli3
                Thinking...
                ```json
                {
                  "summary": "Added UserValidator",
                  "rationale": "Enforces domain validation",
                  "scope_status": "SUFFICIENT",
                  "missing_files": [],
                  "files": [
                    {
                      "path": "src/main/UserValidator.java",
                      "operation": "CREATE",
                      "mode": "FULL_CONTENT",
                      "content": "public class UserValidator { public boolean isValid() { return true; } }",
                      "patch_blocks": []
                    }
                  ]
                }
                ```
                Finished task in 2.4s
                """;

        DeepSeekResponse response = parser.parse(rawOutput, "gemini-3.8-flash-medium");

        assertNotNull(response);
        assertEquals("Added UserValidator", response.summary());
        assertEquals("Enforces domain validation", response.rationale());
        assertEquals(ScopeStatus.SUFFICIENT, response.scopeStatus());
        assertEquals(1, response.files().size());
        assertEquals("src/main/UserValidator.java", response.files().get(0).path());
        assertEquals("CREATE", response.files().get(0).operation());
        assertTrue(response.files().get(0).content().contains("class UserValidator"));
    }

    @Test
    @DisplayName("Anti-Truncation Gate (D008): rejects lazy truncation stop-marker in content")
    void testAntiTruncationGateRejection() {
        String rawOutput = """
                {
                  "summary": "Truncated update",
                  "rationale": "Lazy update",
                  "scope_status": "SUFFICIENT",
                  "missing_files": [],
                  "files": [
                    {
                      "path": "src/main/Service.java",
                      "operation": "MODIFY",
                      "mode": "FULL_CONTENT",
                      "content": "public class Service {\\n// ... rest of implementation\\n}",
                      "patch_blocks": []
                    }
                  ]
                }
                """;

        assertThrows(LazyTruncationDetectedException.class, () ->
                parser.parse(rawOutput, "gemini-3.8-flash-medium")
        );
    }

    @Test
    @DisplayName("Parses SCOPE_INSUFFICIENT signal from agy CLI for Duhem-Quine escalation")
    void testParseScopeInsufficient() {
        String rawOutput = """
                {
                  "summary": "Need additional interface file",
                  "rationale": "Missing repository contract",
                  "scope_status": "SCOPE_INSUFFICIENT",
                  "missing_files": ["src/main/IRepository.java"],
                  "files": []
                }
                """;

        DeepSeekResponse response = parser.parse(rawOutput, "gemini-3.8-pro");

        assertNotNull(response);
        assertEquals(ScopeStatus.SCOPE_INSUFFICIENT, response.scopeStatus());
        assertEquals(1, response.missingFiles().size());
        assertEquals("src/main/IRepository.java", response.missingFiles().get(0));
    }
}
