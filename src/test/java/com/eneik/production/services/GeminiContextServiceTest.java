package com.eneik.production.services;

import com.eneik.production.models.persistence.ContextChunkEntity;
import com.eneik.production.models.persistence.RoleEntity;
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
        verify(repository, never()).findAllVectorRows();
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
        when(repository.count()).thenReturn(1L);
        when(mlPredictionServiceClient.embed("query")).thenReturn(null);

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContext("query", 5);

        assertTrue(result.isEmpty());
        verify(repository, never()).findAll();
        verify(repository, never()).findAllVectorRows();
    }

    @Test
    void retrieveRelevantContextRanksByCosineSimilarityAndAppliesTopK() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        ContextChunkEntity match = chunk("exact match", "ref-match", new float[]{1f, 0f});
        ContextChunkEntity irrelevant = chunk("orthogonal, irrelevant", "ref-irrelevant", new float[]{0f, 1f});
        ContextChunkRepository.VectorRow matchRow = vectorRow(match);
        ContextChunkRepository.VectorRow irrelevantRow = vectorRow(irrelevant);
        when(repository.count()).thenReturn(2L);
        when(repository.findAllVectorRows()).thenReturn(List.of(matchRow, irrelevantRow));
        when(repository.findAllById(List.of(match.getId()))).thenReturn(List.of(match));
        when(mlPredictionServiceClient.embed("query")).thenReturn(new float[]{1f, 0f});

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContext("query", 5);

        assertEquals(1, result.size(), "the orthogonal (similarity 0) chunk must be filtered by the dynamic floor");
        assertEquals("ref-match", result.get(0).sourceRef());
        assertEquals(1.0, result.get(0).similarity(), 1e-6);
    }

    @Test
    void buildPhilosopherPatternContextUsesSourceTypeAndPrefixVectorQuery() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-07");
        ContextChunkEntity chunk = chunk("role pattern", "BARCAN-TAG-07_patterns.md", new float[]{1f, 0f});
        ContextChunkRepository.VectorRow row = vectorRow(chunk);
        when(repository.count()).thenReturn(1L);
        when(repository.findVectorRowsBySourceTypeAndSourceRefStartingWith("philosopher_pattern", "BARCAN-TAG-07"))
                .thenReturn(List.of(row));
        when(repository.findAllById(List.of(chunk.getId()))).thenReturn(List.of(chunk));
        when(mlPredictionServiceClient.embed("query")).thenReturn(new float[]{1f, 0f});

        String block = service.buildPhilosopherPatternContext(role, "query", 5);

        assertTrue(block.contains("role pattern"));
        verify(repository, never()).findAllVectorRows();
        verify(repository).findVectorRowsBySourceTypeAndSourceRefStartingWith("philosopher_pattern", "BARCAN-TAG-07");
    }

    @Test
    void retrieveRelevantContextBySourceRefPrefixUsesPrefixVectorQuery() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        ContextChunkEntity chunk = chunk("role fact", "BARCAN-TAG-07_role.md", new float[]{1f, 0f});
        ContextChunkRepository.VectorRow row = vectorRow(chunk);
        when(repository.count()).thenReturn(1L);
        when(repository.findVectorRowsBySourceRefStartingWith("BARCAN-TAG-07")).thenReturn(List.of(row));
        when(repository.findAllById(List.of(chunk.getId()))).thenReturn(List.of(chunk));
        when(mlPredictionServiceClient.embed("query")).thenReturn(new float[]{1f, 0f});

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContext(
                "query", 5, "BARCAN-TAG-07");

        assertEquals(1, result.size());
        assertEquals("role fact", result.get(0).content());
        verify(repository, never()).findAllVectorRows();
        verify(repository).findVectorRowsBySourceRefStartingWith("BARCAN-TAG-07");
    }

    @Test
    void retrieveRelevantContextBySourceTypesUsesSourceTypeVectorQuery() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);
        ContextChunkEntity chunk = chunk("typed fact", "OBSERVER_LOG.md", new float[]{1f, 0f});
        ContextChunkRepository.VectorRow row = vectorRow(chunk);
        when(repository.count()).thenReturn(1L);
        when(repository.findVectorRowsBySourceTypeIn(List.of("observer_log"))).thenReturn(List.of(row));
        when(repository.findAllById(List.of(chunk.getId()))).thenReturn(List.of(chunk));
        when(mlPredictionServiceClient.embed("query")).thenReturn(new float[]{1f, 0f});

        List<GeminiContextService.RetrievedChunk> result = service.retrieveRelevantContextBySourceTypes(
                "query", 5, List.of("observer_log"));

        assertEquals(1, result.size());
        assertEquals("typed fact", result.get(0).content());
        verify(repository, never()).findAllVectorRows();
        verify(repository).findVectorRowsBySourceTypeIn(List.of("observer_log"));
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
        ContextChunkEntity chunk = chunk("relevant fact", "OBSERVER_LOG.md", new float[]{1f, 0f});
        ContextChunkRepository.VectorRow row = vectorRow(chunk);
        when(repository.count()).thenReturn(1L);
        when(repository.findVectorRowsBySourceTypeIn(anyList())).thenReturn(List.of(row));
        when(repository.findAllById(List.of(chunk.getId()))).thenReturn(List.of(chunk));
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

    @Test
    @org.junit.jupiter.api.DisplayName("Substitution Oracle (Frege 1892 / D009): retrieval output before and after content_hash skip is identical salva veritate")
    void retrievalOutputIsIdenticalBeforeAndAfterContentHashSkip() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);

        java.util.List<ContextChunkEntity> store = new java.util.ArrayList<>();
        when(repository.count()).thenAnswer(inv -> (long) store.size());
        when(repository.findAllVectorRows()).thenAnswer(inv -> store.stream().map(GeminiContextServiceTest::vectorRow).toList());
        when(repository.findVectorRowsBySourceTypeIn(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.List<String> types = (java.util.List<String>) inv.getArgument(0);
            return store.stream().filter(c -> types.contains(c.getSourceType())).map(GeminiContextServiceTest::vectorRow).toList();
        });
        when(repository.findAllById(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.List<java.util.UUID> ids = (java.util.List<java.util.UUID>) inv.getArgument(0);
            return store.stream().filter(c -> ids.contains(c.getId())).toList();
        });
        when(repository.findBySourceRef(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            return store.stream().filter(c -> ref.equals(c.getSourceRef())).toList();
        });
        when(repository.existsBySourceRefAndContentHash(anyString(), anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            String hash = inv.getArgument(1);
            return store.stream().anyMatch(c -> ref.equals(c.getSourceRef()) && hash.equals(c.getContentHash()));
        });
        doAnswer(inv -> {
            String ref = inv.getArgument(0);
            store.removeIf(c -> ref.equals(c.getSourceRef()));
            return null;
        }).when(repository).deleteBySourceRef(anyString());
        when(repository.saveAll(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Iterable<ContextChunkEntity> iterable = (Iterable<ContextChunkEntity>) inv.getArgument(0);
            for (ContextChunkEntity c : iterable) {
                if (c.getId() == null) {
                    c.setId(java.util.UUID.randomUUID());
                }
                store.add(c);
            }
            return store;
        });

        float[] sampleVector = new float[]{1.0f, 0.0f};
        when(mlPredictionServiceClient.embed(anyString())).thenReturn(sampleVector);

        String docSource = "00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md";
        String docType = "philosopher_pattern_common";
        String content = "Gottlob Frege: Substitution Oracle (Salva Veritate).\n\nTwo expressions with identical reference can be replaced without changing the truth value.";

        // Pass 1: Fresh index of document
        service.indexDocument(docType, docSource, content);
        assertFalse(store.isEmpty(), "Initial indexing must persist chunks into repository");

        List<GeminiContextService.RetrievedChunk> baselineRetrieval = service.retrieveRelevantContext("substitution oracle", 5);
        String baselineBlock = service.buildContextBlock("substitution oracle");

        assertFalse(baselineRetrieval.isEmpty(), "Baseline retrieval must return indexed content");
        assertEquals(docSource, baselineRetrieval.get(0).sourceRef());
        assertTrue(baselineRetrieval.get(0).content().contains("Substitution Oracle"));
        assertTrue(baselineBlock.contains("Substitution Oracle"));

        // Reset invocation counters on mlPredictionServiceClient to monitor re-embedding cost
        clearInvocations(mlPredictionServiceClient);
        when(mlPredictionServiceClient.embed(anyString())).thenReturn(sampleVector);

        // Pass 2: Reindex exact same content (content_hash matches)
        service.indexDocument(docType, docSource, content);

        // Prove zero cost: ml client must NOT have embedded document chunks (only dimension probe is permitted)
        verify(repository, times(1)).deleteBySourceRef(docSource); // Only once from Pass 1, NOT Pass 2
        verify(repository, atLeastOnce()).existsBySourceRefAndContentHash(eq(docSource), anyString());

        // Pass 3: Retrieval after skip
        List<GeminiContextService.RetrievedChunk> afterSkipRetrieval = service.retrieveRelevantContext("substitution oracle", 5);
        String afterSkipBlock = service.buildContextBlock("substitution oracle");

        // Salva Veritate Oracle assertions:
        assertEquals(baselineRetrieval.size(), afterSkipRetrieval.size(), "Retrieval size must be identical before and after skip");
        for (int i = 0; i < baselineRetrieval.size(); i++) {
            GeminiContextService.RetrievedChunk b = baselineRetrieval.get(i);
            GeminiContextService.RetrievedChunk a = afterSkipRetrieval.get(i);
            assertEquals(b.sourceRef(), a.sourceRef(), "Chunk sourceRef must be identical");
            assertEquals(b.content(), a.content(), "Chunk content must be identical");
            assertEquals(b.similarity(), a.similarity(), 1e-9, "Cosine similarity score must be identical");
        }
        assertEquals(baselineBlock, afterSkipBlock, "Context block rendering must be identical salva veritate");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Substitution Oracle (Frege 1892 / D009): content modification invalidates hash and updates retrieval")
    void contentModificationInvalidatesHashAndUpdatesRetrieval() {
        setUp("");
        when(settingsService.effectiveBoolean("gemini_context_learning_enabled")).thenReturn(true);

        java.util.List<ContextChunkEntity> store = new java.util.ArrayList<>();
        when(repository.count()).thenAnswer(inv -> (long) store.size());
        when(repository.findAllVectorRows()).thenAnswer(inv -> store.stream().map(GeminiContextServiceTest::vectorRow).toList());
        when(repository.findAllById(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.List<java.util.UUID> ids = (java.util.List<java.util.UUID>) inv.getArgument(0);
            return store.stream().filter(c -> ids.contains(c.getId())).toList();
        });
        when(repository.findBySourceRef(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            return store.stream().filter(c -> ref.equals(c.getSourceRef())).toList();
        });
        when(repository.existsBySourceRefAndContentHash(anyString(), anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            String hash = inv.getArgument(1);
            return store.stream().anyMatch(c -> ref.equals(c.getSourceRef()) && hash.equals(c.getContentHash()));
        });
        doAnswer(inv -> {
            String ref = inv.getArgument(0);
            store.removeIf(c -> ref.equals(c.getSourceRef()));
            return null;
        }).when(repository).deleteBySourceRef(anyString());
        when(repository.saveAll(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Iterable<ContextChunkEntity> iterable = (Iterable<ContextChunkEntity>) inv.getArgument(0);
            for (ContextChunkEntity c : iterable) {
                if (c.getId() == null) {
                    c.setId(java.util.UUID.randomUUID());
                }
                store.add(c);
            }
            return store;
        });

        float[] vector1 = new float[]{1.0f, 0.0f};
        when(mlPredictionServiceClient.embed(anyString())).thenReturn(vector1);

        String docSource = "00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md";
        String docType = "philosopher_pattern_common";
        String initialContent = "Original content before modification.";

        service.indexDocument(docType, docSource, initialContent);
        List<GeminiContextService.RetrievedChunk> initial = service.retrieveRelevantContext("original", 5);
        assertFalse(initial.isEmpty());
        assertTrue(initial.get(0).content().contains("Original content"));

        // Modify content
        String modifiedContent = "Modified content replacing previous version.";
        service.indexDocument(docType, docSource, modifiedContent);

        // Verification: repository delete was called twice, new content is in store
        verify(repository, times(2)).deleteBySourceRef(docSource);
        List<GeminiContextService.RetrievedChunk> modified = service.retrieveRelevantContext("modified", 5);
        assertFalse(modified.isEmpty());
        assertTrue(modified.get(0).content().contains("Modified content"));
        assertFalse(modified.get(0).content().contains("Original content"));
    }

    private static ContextChunkRepository.VectorRow vectorRow(ContextChunkEntity chunk) {
        ContextChunkRepository.VectorRow row = mock(ContextChunkRepository.VectorRow.class);
        when(row.getId()).thenReturn(chunk.getId());
        when(row.getSourceType()).thenReturn(chunk.getSourceType());
        when(row.getSourceRef()).thenReturn(chunk.getSourceRef());
        when(row.getEmbedding()).thenReturn(chunk.getEmbedding());
        when(row.getEmbeddingDims()).thenReturn(chunk.getEmbeddingDims());
        return row;
    }

    private static ContextChunkEntity chunk(String content, String sourceRef, float[] embedding) {
        ContextChunkEntity entity = new ContextChunkEntity();
        entity.setId(java.util.UUID.randomUUID());
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
