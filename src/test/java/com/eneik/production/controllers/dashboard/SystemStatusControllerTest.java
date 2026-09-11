package com.eneik.production.controllers.dashboard;

import com.eneik.production.services.GeminiContextService;
import com.eneik.production.services.ProjectEventLogService;
import com.eneik.production.services.dashboard.SystemStatusService;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verification for SystemStatusController.
 *
 * <p>Applied philosophy: FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK (D011 Perception failure)
 * A signal is valid only if it helps the system perform the function it is meant to support.
 * Prompt caching in GeminiContextCacheManager created expensive cachedContents resources at the provider
 * with TTL 86400s that were never consumed by any model requests in Java (the active caching path is in Python).
 * Re-indexing preserves standing knowledge re-indexing for RAG while eliminating dead provider cache creation.
 */
class SystemStatusControllerTest {

    @Test
    void reindexGeminiContextTriggersKnowledgeReindexWithoutPromptCacheCall() {
        SystemStatusService systemStatusService = mock(SystemStatusService.class);
        SystemSettingsService systemSettingsService = mock(SystemSettingsService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GeminiContextService geminiContextService = mock(GeminiContextService.class);
        ProjectEventLogService projectEventLogService = mock(ProjectEventLogService.class);

        SystemStatusController controller = new SystemStatusController(
                systemStatusService,
                systemSettingsService,
                jdbcTemplate,
                geminiContextService,
                projectEventLogService
        );

        Map<String, Object> response = controller.reindexGeminiContext();

        verify(geminiContextService, times(1)).reindexStandingKnowledge();
        assertEquals("Re-index triggered", response.get("message"));
        assertFalse(response.containsKey("cacheResourceName"),
                "Response must not expose cacheResourceName when prompt cache manager is removed");
    }

    @Test
    void geminiContextCacheManagerClassIsRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.eneik.production.services.googleai.GeminiContextCacheManager"),
                "GeminiContextCacheManager must be removed from classpath as dead duplicate mechanism");
    }

    @Test
    void noCachedContentsCreationInBackendJavaSource() throws IOException {
        Path srcMainJava = Path.of("src/main/java");
        assertTrue(Files.exists(srcMainJava), "src/main/java must exist");

        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            long matches = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("cachedContents");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .count();

            assertEquals(0, matches,
                    "No backend Java source may create or reference cachedContents (eliminated dead mechanism)");
        }
    }
}
