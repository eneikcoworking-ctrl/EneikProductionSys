package com.eneik.production.services;

import com.eneik.production.models.persistence.ContextChunkEntity;
import com.eneik.production.repositories.ContextChunkRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testimony-vs-evidence, applied to the RAG layer itself: retrieval must degrade to "nothing extra" (never
 * an error, never a stale/garbage answer) whenever any precondition is missing - flag off, empty corpus, or
 * the embedding call itself failing. The ranking math (cosine similarity, Otsu-style dynamic floor) is
 * exact and is asserted directly against known vectors, not against Gemini's own output.
 */
class GeminiContextServiceTest {

    private ContextChunkRepository repository;
    private MLPredictionServiceClient mlPredictionServiceClient;
    private SystemSettingsService settingsService;
    private GeminiContextService service;

    private void setUp(String repoRoot) {
        repository = mock(ContextChunkRepository.class);
        mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        settingsService = mock(SystemSettingsService.class);
        service = new GeminiContextService(repository, mlPredictionServiceClient, settingsService, repoRoot);
    }

    @Test
    void cosineSimilarityOfIdenticalVectorsIsOne() {
        float[] a = {1f, 2f, 3f};
        assertEquals(1.0, GeminiContextService.cosineSimilarity(a, a), 1e-9);
    }

    @Test
    void cosineSimilarityOfOrthogonalVectorsIsZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(0.0, GeminiContextService.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void cosineSimilarityHandlesMismatchedLengthsAndZeroVectorsSafely() {
        assertEquals(0.0, GeminiContextService.cosineSimilarity(new float[]{1f}, new float[]{1f, 2f}));
        assertEquals(0.0, GeminiContextService.cosineSimilarity(new float[]{0f, 0f}, new float[]{1f, 1f}));
    }

    @Test
    void embeddingRoundTripsThroughSerialization() {
        float[] original = {0.1f, -0.25f, 3.5f};
        float[] parsed = GeminiContextService.parseEmbedding(GeminiContextService.serializeEmbedding(original));
        assertArrayEquals(original, parsed, 1e-6f);
    }

    @Test
    void chunkTextNeverSplitsAParagraphThatFitsInOneChunk() {
        String content = "First paragraph.\n\nSecond paragraph.";
        List<String> chunks = GeminiContextService.chunkText(content);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("First paragraph."));
        assertTrue(chunks.get(0).contains("Second paragraph."));
    }

    @Test
    void chunkTextHardSplitsAParagraphLongerThanTheChunkSize() {
        String longParagraph = "x".repeat(3000);
        List<String> chunks = GeminiContextService.chunkText(longParagraph);
        assertTrue(chunks.size() >= 2, "a 3000-char paragraph must be split across multiple chunks");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 1400);
        }
    }

    @Test
    void dynamicSimilarityFloorFallsBackToFixedFloorWhenScoresAreIndistinguishable() {
        double floor = GeminiContextService.dynamicSimilarityFloor(List.of(0.5, 0.5, 0.5));
        assertEquals(0.35, floor, 1e-9);
    }

    @Test
    void dynamicSimilarityFloorSeparatesAClearHighLowCluster() {
        // Two tight clusters (0.05-0.08 = noise, 0.9-0.95 = genuinely relevant) - Otsu should land the
        // threshold cleanly between them, not at the fixed 0.35 floor.
        List<Double> scores = List.of(0.05, 0.06, 0.08, 0.90, 0.92, 0.95);
        double floor = GeminiContextService.dynamicSimilarityFloor(scores);
        assertTrue(floor > 0.08 && floor < 0.90, "expected floor between the two clusters, got " + floor);
    }

    @Test
    void retrieveRelevantContextReturnsEmptyWhenFeatureFlagIsOff() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(false);

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContext("query", 5);

        assertTrue(result.isEmpty());
        verify(repository, never()).findAll();
        verify(mlPredictionServiceClient, never()).embed(anyString());
    }

    @Test
    void retrieveRelevantContextReturnsEmptyWhenCorpusIsEmpty() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of());

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContext("query", 5);

        assertTrue(result.isEmpty());
        verify(mlPredictionServiceClient, never()).embed(anyString());
    }

    @Test
    void retrieveRelevantContextReturnsEmptyWhenQueryEmbeddingFails() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(chunk("a", "ref1", new float[]{1f, 0f})));
        when(mlPredictionServiceClient.embed("query")).thenReturn(null);

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContext("query", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void retrieveRelevantContextRanksByCosineSimilarityAndAppliesTopK() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(
                chunk("exact match", "ref-match", new float[]{1f, 0f}),
                chunk("orthogonal, irrelevant", "ref-irrelevant", new float[]{0f, 1f})
        ));
        when(mlPredictionServiceClient.embed("query")).thenReturn(new float[]{1f, 0f});

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContext("query", 5);

        assertEquals(1, result.size(), "the orthogonal (similarity 0) chunk must be filtered by the dynamic floor");
        assertEquals("ref-match", result.get(0).sourceRef());
        assertEquals(1.0, result.get(0).similarity(), 1e-6);
    }

    @Test
    void buildContextBlockReturnsEmptyStringWhenNothingRelevantFound() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of());

        assertEquals("", service.buildContextBlock("anything"));
    }

    @Test
    void buildContextBlockFormatsRetrievedChunksWithSourceAttribution() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(chunk("relevant fact", "OBSERVER_LOG.md", new float[]{1f, 0f})));
        when(mlPredictionServiceClient.embed(anyString())).thenReturn(new float[]{1f, 0f});

        String block = service.buildContextBlock("query about relevant fact");

        assertTrue(block.contains("OBSERVER_LOG.md"));
        assertTrue(block.contains("relevant fact"));
    }

    @Test
    void indexDocumentDeletesExistingChunksBeforeReindexingForIdempotency() {
        setUp("");
        when(mlPredictionServiceClient.embed(anyString())).thenReturn(new float[]{1f, 2f});

        service.indexDocument("observer_log", "OBSERVER_LOG.md", "Some paragraph of real content.");

        verify(repository).deleteBySourceRef("OBSERVER_LOG.md");
        verify(repository).saveAll(argThat(iterable -> {
            long count = 0;
            for (Object ignored : iterable) count++;
            return count == 1;
        }));
    }

    @Test
    void indexDocumentSkipsChunksWhoseEmbeddingFailsInsteadOfFailingTheWholeIndex() {
        setUp("");
        // Two paragraphs each near the chunk-size limit so they land in separate chunks (small paragraphs
        // would otherwise be merged into one chunk by the greedy chunker) - the first embed call fails, the
        // second succeeds, regardless of the exact chunk text.
        String content = "A".repeat(1200) + "\n\n" + "B".repeat(1200);
        when(mlPredictionServiceClient.embed(anyString())).thenReturn(null, new float[]{1f});

        service.indexDocument("observer_log", "ref", content);

        verify(repository).saveAll(argThat(iterable -> {
            long count = 0;
            for (Object ignored : iterable) count++;
            return count == 1;
        }));
    }

    @Test
    void indexDocumentWithBlankContentJustClearsExistingChunks() {
        setUp("");

        service.indexDocument("observer_log", "ref", "");

        verify(repository).deleteBySourceRef("ref");
        verify(mlPredictionServiceClient, never()).embed(anyString());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void reindexStandingKnowledgeNoOpsWhenFeatureFlagIsOff() {
        setUp("/some/repo/root");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(false);

        service.reindexStandingKnowledge();

        verify(repository, never()).deleteBySourceRef(anyString());
    }

    @Test
    void reindexStandingKnowledgeNoOpsWhenRepoRootIsNotConfigured() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);

        service.reindexStandingKnowledge();

        verify(repository, never()).deleteBySourceRef(anyString());
    }

    @Test
    void reindexStandingKnowledgeIncludesParallelDevelopmentConflictPreventionCharter(@TempDir Path root) throws Exception {
        Path patternsDir = root.resolve("docs/philosopher-patterns");
        Files.createDirectories(patternsDir.resolve("philosophers"));
        Files.writeString(patternsDir.resolve("01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md"),
                "Single Writer Ownership\n\nContract-First Parallelism");
        setUp(root.toString());
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        when(mlPredictionServiceClient.embed(anyString())).thenReturn(new float[]{1f, 0f});

        service.reindexStandingKnowledge();

        verify(repository).deleteBySourceRef("01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md");
        verify(repository).saveAll(argThat(iterable -> {
            for (Object item : iterable) {
                ContextChunkEntity chunk = (ContextChunkEntity) item;
                if (chunk.getSourceType().equals("parallel_development_conflict_prevention")
                        && chunk.getContent().contains("Single Writer Ownership")) {
                    return true;
                }
            }
            return false;
        }));
    }

    private static ContextChunkEntity chunk(String content, String sourceRef, float[] embedding) {
        ContextChunkEntity entity = new ContextChunkEntity();
        // 2026-08-23: was "test", which buildContextBlock now filters out - it draws only from the METHOD
        // corpus so client briefs cannot outrank a charter in the same ranking. The fixture stands for the
        // observer log it names, so it carries that type.
        entity.setSourceType("observer_log");
        entity.setSourceRef(sourceRef);
        entity.setChunkIndex(0);
        entity.setContent(content);
        entity.setEmbedding(GeminiContextService.serializeEmbedding(embedding));
        entity.setEmbeddingDims(embedding.length);
        return entity;
    }
}
