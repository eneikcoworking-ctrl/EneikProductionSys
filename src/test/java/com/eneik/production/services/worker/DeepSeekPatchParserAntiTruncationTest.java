package com.eneik.production.services.worker;

import com.eneik.production.services.worker.deepseek.DeepSeekPatchParser;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.deepseek.LazyTruncationDetectedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekPatchParserAntiTruncationTest {

    private final DeepSeekPatchParser parser = new DeepSeekPatchParser(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {
            "// ... existing code ...",
            "// ... rest of implementation",
            "/* ... existing code ... */",
            "// TODO: rest of implementation",
            "/* ... */",
            "// ... remainder",
            "// ... остальной код"
    })
    @DisplayName("Anti-Truncation Gate: rejects lazy stop-markers in FULL_CONTENT")
    void testAntiTruncationInFullContent(String stopMarker) {
        String json = String.format("""
                {
                  "summary": "Implement feature",
                  "rationale": "BARCAN-02",
                  "scope_status": "SUFFICIENT",
                  "files": [
                    {
                      "path": "src/main/java/Service.java",
                      "operation": "CREATE",
                      "mode": "FULL_CONTENT",
                      "content": "package com.example;\\n\\npublic class Service {\\n  %s\\n}"
                    }
                  ]
                }
                """, stopMarker.replace("/", "\\/"));

        LazyTruncationDetectedException ex = assertThrows(
                LazyTruncationDetectedException.class,
                () -> parser.parse(json, 0, 100, 50, "deepseek-chat")
        );
        assertTrue(ex.getMessage().contains("Anti-Truncation Gate"));
    }

    @Test
    @DisplayName("Anti-Truncation Gate: rejects lazy stop-markers in PATCH_BLOCKS replace block")
    void testAntiTruncationInPatchBlocks() {
        String json = """
                {
                  "summary": "Modify existing service",
                  "rationale": "BARCAN-02",
                  "scope_status": "SUFFICIENT",
                  "files": [
                    {
                      "path": "src/main/java/Service.java",
                      "operation": "MODIFY",
                      "mode": "PATCH_BLOCKS",
                      "patch_blocks": [
                        {
                          "search": "public void run() {}",
                          "replace": "public void run() {\\n  // ... existing code ...\\n}"
                        }
                      ]
                    }
                  ]
                }
                """;

        assertThrows(LazyTruncationDetectedException.class, () -> parser.parse(json, 0, 100, 50, "deepseek-chat"));
    }

    @Test
    @DisplayName("Clean patch without lazy stop-markers parses successfully")
    void testCleanPatchParsesSuccessfully() {
        String json = """
                {
                  "summary": "Valid implementation",
                  "rationale": "BARCAN-02",
                  "scope_status": "SUFFICIENT",
                  "files": [
                    {
                      "path": "src/main/java/Service.java",
                      "operation": "CREATE",
                      "mode": "FULL_CONTENT",
                      "content": "package com.example;\\n\\npublic class Service {\\n  public int add(int a, int b) { return a + b; }\\n}"
                    }
                  ]
                }
                """;

        DeepSeekResponse res = parser.parse(json, 1000, 200, 80, "deepseek-chat");
        assertNotNull(res);
        assertEquals(1, res.files().size());
        assertEquals("src/main/java/Service.java", res.files().get(0).path());
    }
}
