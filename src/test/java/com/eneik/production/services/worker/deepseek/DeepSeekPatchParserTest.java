package com.eneik.production.services.worker.deepseek;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Popperian falsification gate (D003 Contract Drift / D008 Popper) for DeepSeekPatchParser.
 * Verifies deterministic CTRL-CHAR sanitization inside JSON string literals,
 * strict idempotency on valid JSON, and preservation of Anti-Truncation Gate.
 */
class DeepSeekPatchParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeepSeekPatchParser parser = new DeepSeekPatchParser(objectMapper);

    @Test
    @DisplayName("D003 Gate: sanitizes raw LF/CR/TAB inside content, rationale, and patch_blocks without JsonParseException")
    void testSanitizesUnescapedControlCharsInContentAndPatchBlocks() {
        String rawFailingJson = "{\n"
                + "  \"summary\": \"Add multi-line service\",\n"
                + "  \"rationale\": \"Line 1 of rationale\n\tLine 2 with tab\r\nLine 3\",\n"
                + "  \"scope_status\": \"SUFFICIENT\",\n"
                + "  \"missing_files\": [],\n"
                + "  \"files\": [\n"
                + "    {\n"
                + "      \"path\": \"src/main/java/com/example/DemoService.java\",\n"
                + "      \"operation\": \"CREATE\",\n"
                + "      \"mode\": \"FULL_CONTENT\",\n"
                + "      \"content\": \"package com.example;\n\npublic class DemoService {\n\tpublic String greet() {\r\n\t\treturn \\\"ok\\\";\n\t}\n}\",\n"
                + "      \"patch_blocks\": [\n"
                + "        {\n"
                + "          \"search\": \"public void oldMethod() {\n\treturn;\n}\",\n"
                + "          \"replace\": \"public void newMethod() {\r\n\tSystem.out.println(\\\"hi\\\");\n}\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        // Falsification baseline: raw Jackson MUST reject this payload with CTRL-CHAR code 10
        JsonParseException rawError = assertThrows(JsonParseException.class, () -> objectMapper.readTree(rawFailingJson));
        assertTrue(rawError.getMessage().contains("CTRL-CHAR"), "Expected raw Jackson CTRL-CHAR failure, got: " + rawError.getMessage());

        // Sanitized DeepSeekPatchParser MUST parse cleanly
        DeepSeekResponse response = parser.parse(rawFailingJson, 512, 1024, 256, "deepseek-chat");

        assertNotNull(response);
        assertEquals("Add multi-line service", response.summary());
        assertEquals("Line 1 of rationale\n\tLine 2 with tab\r\nLine 3", response.rationale());
        assertEquals(ScopeStatus.SUFFICIENT, response.scopeStatus());
        assertEquals(1, response.files().size());

        PatchOperation op = response.files().get(0);
        assertEquals("src/main/java/com/example/DemoService.java", op.path());
        assertEquals("package com.example;\n\npublic class DemoService {\n\tpublic String greet() {\r\n\t\treturn \"ok\";\n\t}\n}", op.content());
        assertEquals(1, op.patchBlocks().size());
        assertEquals("public void oldMethod() {\n\treturn;\n}", op.patchBlocks().get(0).search());
        assertEquals("public void newMethod() {\r\n\tSystem.out.println(\"hi\");\n}", op.patchBlocks().get(0).replace());
    }

    @Test
    @DisplayName("D003 Gate: escapes backspace, form-feed, and other < 0x20 control chars inside strings")
    void testEscapesOtherControlCharsUnder0x20() {
        String rawJson = "{\"summary\": \"ctrl\b\f\u0001\u001btest\", \"rationale\": \"ok\", \"scope_status\": \"SUFFICIENT\", \"files\": []}";

        DeepSeekResponse response = parser.parse(rawJson, 0, 10, 10, "deepseek-chat");
        assertEquals("ctrl\b\f\u0001\u001btest", response.summary());
    }

    @Test
    @DisplayName("D003 Gate: strict idempotency on valid JSON with escaped quotes, newlines, tabs, regex, and unicode")
    void testStrictIdempotencyOnValidJson() {
        String validJson = "{\n"
                + "\t\"summary\": \"Regex & escapes preserved\",\n"
                + "\t\"rationale\": \"Pattern: \\\\d+\\\\s+\\\"quoted\\\" \\u00A9\",\n"
                + "\t\"scope_status\": \"SUFFICIENT\",\n"
                + "\t\"files\": [\n"
                + "\t\t{\n"
                + "\t\t\t\"path\": \"src/main/RegexUtil.java\",\n"
                + "\t\t\t\"operation\": \"CREATE\",\n"
                + "\t\t\t\"mode\": \"FULL_CONTENT\",\n"
                + "\t\t\t\"content\": \"String p = \\\"^\\\\w+\\\\n\\\\t$\\\";\\nreturn p.replaceAll(\\\"\\\\\\\\\\\", \\\"/\\\");\"\n"
                + "\t\t}\n"
                + "\t]\n"
                + "}";

        // Structural whitespace outside strings and valid escape sequences inside strings remain byte-identical
        String once = DeepSeekPatchParser.sanitizeJsonControlChars(validJson);
        String twice = DeepSeekPatchParser.sanitizeJsonControlChars(once);
        assertEquals(validJson, once, "Valid JSON must not be altered by sanitizer");
        assertEquals(once, twice, "Sanitizer must be strictly idempotent");

        DeepSeekResponse response = parser.parse(validJson, 0, 50, 50, "deepseek-chat");
        assertEquals("Pattern: \\d+\\s+\"quoted\" \u00A9", response.rationale());
        assertEquals("String p = \"^\\w+\\n\\t$\";\nreturn p.replaceAll(\"\\\\\", \"/\");", response.files().get(0).content());
    }

    @Test
    @DisplayName("D008 Gate: Anti-Truncation still rejects stop-marker inside multiline unsanitized content and patch_blocks")
    void testAntiTruncationTriggersAfterControlCharSanitization() {
        String multilineContentWithStopMarker = "{\n"
                + "  \"summary\": \"Truncated multiline\",\n"
                + "  \"rationale\": \"Test D008 after D003 sanitization\",\n"
                + "  \"scope_status\": \"SUFFICIENT\",\n"
                + "  \"files\": [\n"
                + "    {\n"
                + "      \"path\": \"src/main/LazyService.java\",\n"
                + "      \"operation\": \"MODIFY\",\n"
                + "      \"mode\": \"FULL_CONTENT\",\n"
                + "      \"content\": \"public class LazyService {\n\t// ... rest of implementation\n}\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        LazyTruncationDetectedException ex = assertThrows(
                LazyTruncationDetectedException.class,
                () -> parser.parse(multilineContentWithStopMarker, 0, 100, 50, "deepseek-chat")
        );
        assertTrue(ex.getMessage().contains("Anti-Truncation Gate"));

        String multilinePatchBlockWithStopMarker = "{\n"
                + "  \"summary\": \"Truncated patch block\",\n"
                + "  \"rationale\": \"Test D008 in patch_blocks\",\n"
                + "  \"scope_status\": \"SUFFICIENT\",\n"
                + "  \"files\": [\n"
                + "    {\n"
                + "      \"path\": \"src/main/LazyService.java\",\n"
                + "      \"operation\": \"MODIFY\",\n"
                + "      \"mode\": \"PATCH_BLOCKS\",\n"
                + "      \"patch_blocks\": [\n"
                + "        {\n"
                + "          \"search\": \"void a() {\n}\",\n"
                + "          \"replace\": \"void a() {\n\t// ... existing code ...\n}\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        assertThrows(
                LazyTruncationDetectedException.class,
                () -> parser.parse(multilinePatchBlockWithStopMarker, 0, 100, 50, "deepseek-chat")
        );
    }
}
