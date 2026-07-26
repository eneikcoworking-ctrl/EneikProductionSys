package com.eneik.production.services;

import com.eneik.production.models.persistence.ContextChunkEntity;
import com.eneik.production.repositories.ContextChunkRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG (retrieval-augmented generation) layer for Gemini calls (2026-07-25, operator directive: "нужно
 * чтобы Джемини постоянно училась контексту моей системы и в каждом вызове была максимально компетентна...
 * мат. выверенные недорогие по токенам решения"). Two concrete, testable mechanisms, deliberately NOT
 * fine-tuning or a hosted vector DB - both unnecessary at this corpus size:
 *
 * 1. Standing knowledge (OBSERVER_LOG, engineering-invariants charter, BARCAN role charters) is chunked
 *    and embedded once (re-indexed on a schedule, idempotent per source), not re-sent as raw text on every
 *    call - that's the token-cost lever.
 * 2. Per-call retrieval ranks the indexed corpus by EXACT cosine similarity to the query (dot product /
 *    norms - real linear algebra, not a heuristic), and callers inject only the top-k most relevant chunks
 *    into their prompt instead of the whole corpus.
 *
 * Fully opt-in: gated behind {@code gemini_context_learning_enabled} (default off) so an operator who
 * hasn't opted in gets byte-for-byte the old prompt behavior and pays zero extra Gemini cost.
 */
@Service
public class GeminiContextService {
    private static final Logger log = LoggerFactory.getLogger(GeminiContextService.class);

    private static final int MAX_CHUNK_CHARS = 1400;
    private static final int DEFAULT_TOP_K = 5;
    // Bounds on the dynamic similarity floor below - same Otsu-style reasoning as
    // WishlistContentSimilarityMatcher.dynamicClusterThreshold, tuned for Gemini text-embedding cosine
    // similarities (which typically run higher than lexical/Jaccard scores), so a genuinely irrelevant
    // corpus never gets force-included just because "something" has to be the top of the ranking.
    private static final double MIN_SIMILARITY_FLOOR = 0.35;
    private static final double MAX_SIMILARITY_FLOOR = 0.80;

    private final ContextChunkRepository repository;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final SystemSettingsService settingsService;
    private final String repoRoot;

    public GeminiContextService(ContextChunkRepository repository,
                                 MLPredictionServiceClient mlPredictionServiceClient,
                                 SystemSettingsService settingsService,
                                 @Value("${eneik.operator.system-repo-root:}") String repoRoot) {
        this.repository = repository;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.settingsService = settingsService;
        this.repoRoot = repoRoot;
    }

    /**
     * Re-indexes the standing knowledge base: OBSERVER_LOG.md, the engineering-invariants charter, the AI
     * review guidelines, and every BARCAN-TAG role charter found under the mounted repo root. Idempotent
     * per source (delete-then-insert), safe to call daily or on demand. A no-op when the repo root isn't
     * mounted (e.g. a local unit-test context) or the feature flag is off.
     */
    @Scheduled(cron = "${gemini-context.reindex-cron:0 0 3 * * ?}")
    public void reindexStandingKnowledge() {
        if (!settingsService.effectiveBoolean("gemini_context_learning_enabled")) {
            return;
        }
        if (repoRoot == null || repoRoot.isBlank()) {
            log.info("GeminiContextService: repo root not configured, skipping standing-knowledge re-index");
            return;
        }
        Path root = Path.of(repoRoot);
        indexFileIfPresent(root.resolve("OBSERVER_LOG.md"), "observer_log");
        indexFileIfPresent(root.resolve("docs/ENGINEERING_INVARIANTS_CHARTER.md"), "engineering_charter");
        indexFileIfPresent(root.resolve("docs/AI_REVIEW_GUIDELINES.md"), "ai_review_guidelines");
        // The orchestrator's own accumulated experience/knowledge about this project (architecture
        // decisions, confirmed bugs, standing principles) - a manually-refreshed snapshot maintained by
        // Claude, not a live feed (operator directive 2026-07-25: "свой опыт и знания о проекте поместить").
        indexFileIfPresent(root.resolve("docs/CLAUDE_OPERATOR_KNOWLEDGE.md"), "claude_operator_notes");
        // 2026-07-26 operator directive ("знала большинство математически идеальных паттернов
        // программирования"): a catalog of structural/operational failure signatures the observer can match
        // against evidence-snapshot symptoms - she never sees code, so these are written as
        // symptom -> pattern, not code smells.
        indexFileIfPresent(root.resolve("docs/OPERATIONAL_FAILURE_PATTERNS.md"), "operational_failure_patterns");

        try (DirectoryStream<Path> charters = Files.newDirectoryStream(root, "BARCAN-TAG-*.md")) {
            for (Path charterFile : charters) {
                indexFileIfPresent(charterFile, "role_charter");
            }
        } catch (IOException e) {
            log.warn("GeminiContextService: failed to list BARCAN-TAG charter files under {}: {}", root, e.getMessage());
        }
    }

    private void indexFileIfPresent(Path file, String sourceType) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String content = Files.readString(file);
            indexDocument(sourceType, file.getFileName().toString(), content);
        } catch (IOException e) {
            log.warn("GeminiContextService: failed to read {}: {}", file, e.getMessage());
        }
    }

    /** Delete-then-insert re-index of one source document, chunked and embedded. */
    public void indexDocument(String sourceType, String sourceRef, String content) {
        repository.deleteBySourceRef(sourceRef);
        if (content == null || content.isBlank()) {
            return;
        }
        List<String> chunks = chunkText(content);
        List<ContextChunkEntity> toSave = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] vector = mlPredictionServiceClient.embed(chunks.get(i));
            if (vector == null) {
                log.warn("GeminiContextService: embedding failed for {} chunk {}/{}, skipping that chunk (partial index)",
                        sourceRef, i + 1, chunks.size());
                continue;
            }
            ContextChunkEntity entity = new ContextChunkEntity();
            entity.setSourceType(sourceType);
            entity.setSourceRef(sourceRef);
            entity.setChunkIndex(i);
            entity.setContent(chunks.get(i));
            entity.setEmbedding(serializeEmbedding(vector));
            entity.setEmbeddingDims(vector.length);
            toSave.add(entity);
        }
        repository.saveAll(toSave);
        log.info("GeminiContextService: indexed {} ({}) - {} of {} chunk(s) embedded", sourceRef, sourceType, toSave.size(), chunks.size());
    }

    /** Greedy paragraph-aware chunking: never splits a paragraph unless it alone exceeds the chunk size. */
    static List<String> chunkText(String content) {
        String[] paragraphs = content.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_CHUNK_CHARS) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().strip());
                    current.setLength(0);
                }
                for (int i = 0; i < trimmed.length(); i += MAX_CHUNK_CHARS) {
                    chunks.add(trimmed.substring(i, Math.min(trimmed.length(), i + MAX_CHUNK_CHARS)));
                }
                continue;
            }
            if (current.length() + trimmed.length() + 2 > MAX_CHUNK_CHARS && !current.isEmpty()) {
                chunks.add(current.toString().strip());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    public record RetrievedChunk(String sourceRef, String content, double similarity) {
    }

    /**
     * Ranks the indexed corpus by exact cosine similarity to the query, applies a dynamic (Otsu-style,
     * data-driven) similarity floor so a weakly-related corpus doesn't get force-included, and returns at
     * most topK chunks above that floor. Returns an empty list (never throws) whenever retrieval isn't
     * possible - feature flag off, no indexed corpus, or the query embedding call itself fails - so callers
     * can always safely treat the result as "nothing extra to add", not an error.
     */
    public List<RetrievedChunk> retrieveRelevantContext(String query, int topK) {
        if (!settingsService.effectiveBoolean("gemini_context_learning_enabled")) {
            return List.of();
        }
        List<ContextChunkEntity> corpus = repository.findAll();
        if (corpus.isEmpty()) {
            return List.of();
        }
        float[] queryVector = mlPredictionServiceClient.embed(query);
        if (queryVector == null) {
            return List.of();
        }

        List<RetrievedChunk> scored = corpus.stream()
                .map(chunk -> new RetrievedChunk(chunk.getSourceRef(), chunk.getContent(),
                        cosineSimilarity(queryVector, parseEmbedding(chunk.getEmbedding()))))
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
                .collect(Collectors.toList());

        double floor = dynamicSimilarityFloor(scored.stream().map(RetrievedChunk::similarity).toList());
        return scored.stream()
                .filter(c -> c.similarity() >= floor)
                .limit(topK)
                .toList();
    }

    /** Convenience wrapper: retrieves with the default top-k and formats a ready-to-inject prompt block. */
    public String buildContextBlock(String query) {
        List<RetrievedChunk> retrieved = retrieveRelevantContext(query, DEFAULT_TOP_K);
        if (retrieved.isEmpty()) {
            return "";
        }
        StringBuilder block = new StringBuilder(
                "RELEVANT SYSTEM KNOWLEDGE (retrieved from the indexed knowledge base by embedding similarity, "
                        + "top " + retrieved.size() + " of the full corpus - this augments, never replaces, any "
                        + "explicit evidence already provided elsewhere in this prompt):\n");
        for (RetrievedChunk chunk : retrieved) {
            block.append("- [").append(chunk.sourceRef()).append("] ").append(chunk.content().replace("\n", " ")).append('\n');
        }
        return block.toString();
    }

    /**
     * Otsu's method applied to the similarity-score distribution: finds the threshold that maximizes
     * between-class variance between "relevant" and "irrelevant" chunks for THIS query, instead of a fixed
     * cutoff that's too strict for a broad query and too loose for a narrow one. Falls back to the fixed
     * MIN_SIMILARITY_FLOOR when there isn't enough real separation to compute one (fewer than 2 distinct
     * scores). Same technique as WishlistContentSimilarityMatcher.dynamicClusterThreshold, applied to a
     * different score distribution (embedding cosine similarity vs. lexical Jaccard).
     */
    static double dynamicSimilarityFloor(List<Double> scores) {
        List<Double> sorted = scores.stream().sorted().distinct().toList();
        if (sorted.size() < 2) {
            return MIN_SIMILARITY_FLOOR;
        }
        double bestThreshold = MIN_SIMILARITY_FLOOR;
        double bestVariance = -1;
        for (int i = 0; i < sorted.size() - 1; i++) {
            double candidate = (sorted.get(i) + sorted.get(i + 1)) / 2.0;
            List<Double> below = scores.stream().filter(s -> s < candidate).toList();
            List<Double> above = scores.stream().filter(s -> s >= candidate).toList();
            if (below.isEmpty() || above.isEmpty()) {
                continue;
            }
            double meanBelow = below.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double meanAbove = above.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double weightBelow = below.size() / (double) scores.size();
            double weightAbove = above.size() / (double) scores.size();
            double betweenClassVariance = weightBelow * weightAbove * Math.pow(meanAbove - meanBelow, 2);
            if (betweenClassVariance > bestVariance) {
                bestVariance = betweenClassVariance;
                bestThreshold = candidate;
            }
        }
        return Math.min(MAX_SIMILARITY_FLOOR, Math.max(MIN_SIMILARITY_FLOOR, bestThreshold));
    }

    static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    static String serializeEmbedding(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    static float[] parseEmbedding(String csv) {
        if (csv == null || csv.isBlank()) {
            return new float[0];
        }
        String[] parts = csv.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
