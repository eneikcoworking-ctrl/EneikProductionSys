package com.eneik.production.services;

import com.eneik.production.models.persistence.ContextChunkEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.repositories.ContextChunkRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * RAG (retrieval-augmented generation) layer for Gemini calls (2026-07-25, operator directive: "Gemini needs
 * to keep learning the context of my system and to be as competent as possible on every call...
 * mathematically sound solutions that are cheap in tokens"). Two concrete, testable mechanisms, deliberately NOT
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

    // 2026-07-26 operator directive ("the overall figure runs out fast" - reduce spend): OBSERVER_LOG.md is
    // append-only and grows forever (4700+ lines and counting) - indexing the WHOLE file every reindex
    // would make embedding cost scale with total project history, not current relevance. Bounded to the
    // tail (most recent activity is what matters for pattern-matching against a live anomaly anyway).
    private static final int OBSERVER_LOG_TAIL_CHARS = 60_000;

    /**
     * Re-indexes the standing knowledge base: OBSERVER_LOG.md (tail only), the engineering-invariants
     * charter, the AI review guidelines, every BARCAN-TAG role charter, and the philosopher-patterns
     * corpus (docs/philosopher-patterns/) found under the mounted repo root. Idempotent per source
     * (delete-then-insert IF content changed - see indexDocument's content-hash skip), safe to call daily
     * or on demand. A no-op when the repo root isn't mounted (e.g. a local unit-test context) or the
     * feature flag is off.
     */
    /**
     * Reindex the corpus when the embedding model behind it has changed underneath it.
     *
     * 2026-08-23. Vectors from two different models cannot be compared, and the comparison does not fail -
     * it returns zero, which reads as "not similar". Without this, switching the model would leave
     * retrieval empty for up to a day until the nightly cron, and empty is exactly the state that went
     * unnoticed for three days after the Gemini account ran out. A dimension probe is one call and settles
     * the question at boot rather than leaving it to be discovered.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void reindexIfEmbeddingModelChanged() {
        float[] probe = mlPredictionServiceClient.embed("dimension probe");
        if (probe == null) {
            log.warn("GeminiContextService: the embedding service gave no vector at startup, so whether the "
                    + "corpus matches the current model is unknown. Retrieval will return nothing until it "
                    + "answers - this is the condition that hid for three days once already.");
            return;
        }
        long stale = repository.findAll().stream()
                .filter(chunk -> parseEmbedding(chunk.getEmbedding()).length != probe.length)
                .count();
        if (stale == 0) {
            return;
        }
        log.warn("GeminiContextService: {} chunk(s) are stored at a dimension other than {} - the embedding "
                + "model changed. Reindexing now rather than waiting for the nightly run, because until it "
                + "finishes the corpus is invisible to every prompt.", stale, probe.length);
        reindexStandingKnowledge();
    }

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
        indexFileIfPresent(root.resolve("OBSERVER_LOG.md"), "observer_log", OBSERVER_LOG_TAIL_CHARS);
        indexFileIfPresent(root.resolve("docs/ENGINEERING_INVARIANTS_CHARTER.md"), "engineering_charter", -1);
        indexFileIfPresent(root.resolve("docs/AI_REVIEW_GUIDELINES.md"), "ai_review_guidelines", -1);
        // The orchestrator's own accumulated experience/knowledge about this project (architecture
        // decisions, confirmed bugs, standing principles) - a manually-refreshed snapshot maintained by
        // Claude, not a live feed (operator directive 2026-07-25: "put in your own experience and knowledge of the project").
        indexFileIfPresent(root.resolve("docs/CLAUDE_OPERATOR_KNOWLEDGE.md"), "claude_operator_notes", -1);
        // 2026-07-26 operator directive ("knew most of the mathematically ideal programming
        // patterns"): a catalog of structural/operational failure signatures the observer can match
        // against evidence-snapshot symptoms - she never sees code, so these are written as
        // symptom -> pattern, not code smells.
        indexFileIfPresent(root.resolve("docs/OPERATIONAL_FAILURE_PATTERNS.md"), "operational_failure_patterns", -1);

        try (DirectoryStream<Path> charters = Files.newDirectoryStream(root, "BARCAN-TAG-*.md")) {
            for (Path charterFile : charters) {
                indexFileIfPresent(charterFile, "role_charter", -1);
            }
        } catch (IOException e) {
            log.warn("GeminiContextService: failed to list BARCAN-TAG charter files under {}: {}", root, e.getMessage());
        }

        // 2026-07-26: philosopher-patterns corpus (operator-built, docs/philosopher-patterns/) - 78
        // per-philosopher pattern files plus one common-patterns digest. Safe to bulk-index despite its
        // size (~660KB) because it is stable content - the content-hash skip in indexDocument means every
        // reindex after the first one costs nothing for this corpus unless a file actually changes. Index
        // and QA/README/generator files are deliberately excluded - not prose content for RAG.
        indexFileIfPresent(root.resolve("docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md"),
                "philosopher_pattern_common", -1);
        indexFileIfPresent(root.resolve("docs/philosopher-patterns/01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md"),
                "parallel_development_conflict_prevention", -1);
        Path philosophersDir = root.resolve("docs/philosopher-patterns/philosophers");
        if (Files.isDirectory(philosophersDir)) {
            try (DirectoryStream<Path> philosopherFiles = Files.newDirectoryStream(philosophersDir, "*.md")) {
                for (Path philosopherFile : philosopherFiles) {
                    indexFileIfPresent(philosopherFile, "philosopher_pattern", -1);
                }
            } catch (IOException e) {
                log.warn("GeminiContextService: failed to list philosopher-pattern files under {}: {}", philosophersDir, e.getMessage());
            }
        }
    }

    /** @param tailChars if positive, only the last this-many characters of the file are indexed. */
    /**
     * Whether this source's stored vectors were produced by the model that is answering now.
     *
     * 2026-08-23. The skip above is content-addressed - a cost fix from 2026-07-26, correct while every
     * vector was billed. It cannot see that the EMBEDDING MODEL changed rather than the text, and vectors
     * from two models are not comparable: cosine similarity across dimensions returns 0.0, which reads as
     * "not similar" rather than "cannot be compared". Measured on the first switch: of the whole corpus
     * only the two files that happened to be edited that day were reindexed, and the other 1523 chunks
     * stayed invisible while every log line said the reindex had run.
     *
     * Cheap by construction: one probe embedding for the whole pass, and one chunk read per source.
     */
    private boolean storedAtCurrentDimension(String sourceRef) {
        float[] probe = currentDimensionProbe();
        if (probe == null) {
            return true;   // Unknown, so do not force work on a guess; the dimension filter still protects retrieval.
        }
        return repository.findBySourceRef(sourceRef).stream()
                .findFirst()
                .map(chunk -> parseEmbedding(chunk.getEmbedding()).length == probe.length)
                .orElse(false);
    }

    private float[] probeCache;

    private float[] currentDimensionProbe() {
        if (probeCache == null) {
            probeCache = mlPredictionServiceClient.embed("dimension probe");
        }
        return probeCache;
    }

    private void indexFileIfPresent(Path file, String sourceType, int tailChars) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String content = Files.readString(file);
            if (tailChars > 0 && content.length() > tailChars) {
                content = content.substring(content.length() - tailChars);
            }
            indexDocument(sourceType, file.getFileName().toString(), content);
        } catch (IOException e) {
            log.warn("GeminiContextService: failed to read {}: {}", file, e.getMessage());
        }
    }

    /**
     * Delete-then-insert re-index of one source document, chunked and embedded - but skipped entirely when
     * the source's content hasn't changed since last time (2026-07-26 cost fix: reindexing used to
     * re-embed EVERY source on EVERY cron tick regardless of whether anything changed - real, recurring
     * cost for stable content like role charters and the philosopher-patterns corpus).
     */
    public void indexDocument(String sourceType, String sourceRef, String content) {
        if (content == null || content.isBlank()) {
            repository.deleteBySourceRef(sourceRef);
            return;
        }
        String contentHash = sha256Hex(content);
        if (repository.existsBySourceRefAndContentHash(sourceRef, contentHash)
                && storedAtCurrentDimension(sourceRef)) {
            log.debug("GeminiContextService: {} unchanged since last index, skipping re-embed", sourceRef);
            return;
        }
        repository.deleteBySourceRef(sourceRef);
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
            entity.setContentHash(contentHash);
            toSave.add(entity);
        }
        repository.saveAll(toSave);
        log.info("GeminiContextService: indexed {} ({}) - {} of {} chunk(s) embedded", sourceRef, sourceType, toSave.size(), chunks.size());
    }

    private static String sha256Hex(String content) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm - never actually throws in practice.
            throw new IllegalStateException(e);
        }
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
        return retrieveFiltered(query, topK, null);
    }

    /** Scoped to chunks whose sourceRef starts with the given prefix (e.g. a role tag - role charter and
     * philosopher-pattern files share that filename prefix, so this covers both in one call). */
    public List<RetrievedChunk> retrieveRelevantContext(String query, int topK, String sourceRefPrefix) {
        return retrieveFiltered(query, topK,
                sourceRefPrefix == null ? null : c -> c.getSourceRef() != null && c.getSourceRef().startsWith(sourceRefPrefix));
    }

    /** Scoped to chunks whose sourceType is one of the given values (role-independent standing knowledge). */
    public List<RetrievedChunk> retrieveRelevantContextBySourceTypes(String query, int topK, List<String> sourceTypes) {
        return retrieveFiltered(query, topK, c -> sourceTypes.contains(c.getSourceType()));
    }

    private List<RetrievedChunk> retrieveFiltered(String query, int topK, Predicate<ContextChunkEntity> filter) {
        if (!settingsService.effectiveBoolean("gemini_context_learning_enabled")) {
            return List.of();
        }
        List<ContextChunkEntity> corpus = repository.findAll();
        if (filter != null) {
            corpus = corpus.stream().filter(filter).toList();
        }
        if (corpus.isEmpty()) {
            return List.of();
        }
        float[] queryVector = mlPredictionServiceClient.embed(query);
        if (queryVector == null) {
            return List.of();
        }

        // 2026-08-23. cosineSimilarity returns 0.0 when two vectors have different lengths - a value that
        // cannot be told apart from "not similar at all", so a corpus stored under a different embedding
        // model would rank uniformly at zero and retrieval would come back empty for a reason nobody could
        // see. That is the same shape as the empty list this method returned for three days after the
        // Gemini quota ran out. Incomparable chunks are now excluded by dimension and counted out loud.
        int queryDimension = queryVector.length;
        List<RetrievedChunk> scored = corpus.stream()
                .map(chunk -> java.util.Map.entry(chunk, parseEmbedding(chunk.getEmbedding())))
                .filter(entry -> entry.getValue().length == queryDimension)
                .map(entry -> new RetrievedChunk(entry.getKey().getSourceRef(), entry.getKey().getContent(),
                        cosineSimilarity(queryVector, entry.getValue())))
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
                .collect(Collectors.toList());

        if (scored.isEmpty()) {
            log.warn("GeminiContextService: none of the {} stored chunks is comparable with a query vector "
                    + "of dimension {} - the corpus was indexed under a different embedding model and "
                    + "must be reindexed before retrieval can return anything. Retrieval is EMPTY, and "
                    + "that is a fact about the index, not about the query.", corpus.size(), queryDimension);
            return List.of();
        }
        if (scored.size() < corpus.size()) {
            log.warn("GeminiContextService: {} of {} chunks were skipped as stored at a different dimension "
                    + "than {}; they are invisible to retrieval until reindexed.",
                    corpus.size() - scored.size(), corpus.size(), queryDimension);
        }

        double floor = dynamicSimilarityFloor(scored.stream().map(RetrievedChunk::similarity).toList());
        return scored.stream()
                .filter(c -> c.similarity() >= floor)
                .limit(topK)
                .toList();
    }

    // Common corpus/type constants shared by buildRoleAndPatternContext below and reindexStandingKnowledge
    // above - kept as a single list here so a future third common-knowledge file only needs to be added in
    // one place.
    /**
     * The corpus that holds this system's METHOD, as opposed to the material any one project happens to be
     * about. Anything reasoning about how this factory should work draws from here and from nowhere else.
     */
    private static final List<String> METHOD_SOURCE_TYPES = List.of(
            "philosopher_pattern", "philosopher_pattern_common", "role_charter", "engineering_charter",
            "operational_failure_patterns", "ai_review_guidelines", "claude_operator_notes",
            // The observer log is this factory's own record of itself, standing knowledge alongside the
            // charters - not the material of any one project. Omitting it was an error in this list,
            // caught by a test that had been asserting its retrievability all along.
            "observer_log",
            "parallel_development_conflict_prevention");

    private static final List<String> ROLE_INDEPENDENT_PATTERN_SOURCE_TYPES =
            List.of("philosopher_pattern_common", "parallel_development_conflict_prevention");
    private static final int RAW_FALLBACK_MAX_CHARS = 8000;

    /**
     * Single, universal entry point for "give this role its charter + philosopher-pattern context" -
     * 2026-08-07 fix for a pattern that had been independently, inconsistently reimplemented as raw,
     * unbounded file reads in at least 4 places (FalsificationCycleService's three prompt builders and
     * JulesDispatchService's per-task dispatch prompt), none of them using this already-indexed corpus.
     * Confirmed live: the code-defect falsification audit alone produced a ~1.5-2MB prompt from 13 roles'
     * full raw charters + full philosopher-pattern files, which Jules rejected with HTTP 400 on every retry.
     *
     * Retrieves the role's own charter+philosopher-pattern chunks (scoped by sourceRef prefix - both share
     * the role-tag filename prefix) plus the role-independent common-patterns/parallel-dev-conflict chunks,
     * ranked by cosine similarity to query. Falls back to a size-capped raw read of just the role's own
     * charter file (never the unbounded original, never the philosopher corpus) when RAG retrieval is
     * unavailable (flag off, empty index, embedding failure) - a caller never gets nothing just because
     * indexing hasn't run yet for this role.
     */
    public String buildRoleAndPatternContext(RoleEntity role, String query, int topKPerSource) {
        return buildRoleScopedContext(role, query, topKPerSource) + buildCommonPatternContext(query, topKPerSource);
    }

    /**
     * Just the role's own charter + philosopher-pattern chunks - split out from buildRoleAndPatternContext
     * so a caller auditing many roles in one cycle (FalsificationCycleService, 13 active roles) can call
     * this once per role and buildCommonPatternContext exactly once for the whole cycle, instead of
     * re-retrieving the same role-independent common corpus on every role.
     */
    public String buildRoleScopedContext(RoleEntity role, String query, int topK) {
        if (role == null) {
            return "";
        }
        List<RetrievedChunk> roleChunks = retrieveRelevantContext(query, topK, role.getTag());
        if (roleChunks.isEmpty()) {
            return rawRoleContextFallback(role);
        }
        StringBuilder block = new StringBuilder();
        block.append("\n\n=== ROLE ").append(role.getTag())
                .append(" CHARTER & PHILOSOPHER PATTERNS (retrieved by relevance to the current task) ===\n");
        for (RetrievedChunk c : roleChunks) {
            block.append("[").append(c.sourceRef()).append("]\n").append(c.content()).append("\n\n");
        }
        return block.toString();
    }

    /**
     * Just this role's philosopher-pattern chunks, excluding its charter - for a caller (JulesDispatchService's
     * per-task dispatch) that already sends the role's full raw charter verbatim through a different,
     * dedicated loader (RoleCapabilityLoader.loadRawCharter - deliberately NOT retrieval-scoped, since a
     * single role's own charter is small enough to send whole and losing any of it would be a real
     * regression, per that call site's own comment) and only needs the philosopher-pattern/common-pattern
     * side of this corpus, without duplicating charter content a second time via retrieval.
     */
    public String buildPhilosopherPatternContext(RoleEntity role, String query, int topK) {
        if (role == null) {
            return "";
        }
        List<RetrievedChunk> chunks = retrieveFiltered(query, topK,
                c -> "philosopher_pattern".equals(c.getSourceType())
                        && c.getSourceRef() != null && c.getSourceRef().startsWith(role.getTag()));
        if (chunks.isEmpty()) {
            return rawPhilosopherPatternFallback(role);
        }
        StringBuilder block = new StringBuilder();
        block.append("\n\n=== ROLE ").append(role.getTag())
                .append(" PHILOSOPHER PATTERNS (retrieved by relevance to the current task) ===\n");
        for (RetrievedChunk c : chunks) {
            block.append("[").append(c.sourceRef()).append("]\n").append(c.content()).append("\n\n");
        }
        return block.toString();
    }

    /** Role-independent common-patterns/parallel-dev-conflict chunks - call once per audit cycle, not per role. */
    public String buildCommonPatternContext(String query, int topK) {
        List<RetrievedChunk> commonChunks = retrieveRelevantContextBySourceTypes(query, topK,
                ROLE_INDEPENDENT_PATTERN_SOURCE_TYPES);
        if (commonChunks.isEmpty()) {
            return rawCommonPatternFallback();
        }
        StringBuilder block = new StringBuilder();
        block.append("\n\n=== COMMON ANALYTIC PROGRAMMING PATTERNS (retrieved by relevance) ===\n");
        for (RetrievedChunk c : commonChunks) {
            block.append("[").append(c.sourceRef()).append("]\n").append(c.content()).append("\n\n");
        }
        return block.toString();
    }

    private String rawRoleContextFallback(RoleEntity role) {
        if (role.getRulesPath() == null || role.getRulesPath().isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(role.getRulesPath());
            if (!Files.isRegularFile(path)) {
                return "";
            }
            String raw = Files.readString(path);
            String capped = raw.length() <= RAW_FALLBACK_MAX_CHARS
                    ? raw
                    : raw.substring(0, RAW_FALLBACK_MAX_CHARS) + "\n[...truncated, RAG retrieval unavailable...]";
            return "\n\n=== ROLE " + role.getTag() + " CHARTER (raw fallback - RAG retrieval unavailable) ===\n" + capped;
        } catch (IOException e) {
            log.warn("GeminiContextService: raw fallback charter read failed for role {}: {}", role.getTag(), e.getMessage());
            return "";
        }
    }

    private String rawPhilosopherPatternFallback(RoleEntity role) {
        java.nio.file.Path dir = Path.of("docs/philosopher-patterns/philosophers");
        if (!Files.isDirectory(dir)) {
            return "";
        }
        StringBuilder block = new StringBuilder();
        int remaining = RAW_FALLBACK_MAX_CHARS;
        try (var stream = Files.newDirectoryStream(dir, role.getTag() + "*.md")) {
            for (java.nio.file.Path philFile : stream) {
                if (remaining <= 0) {
                    break;
                }
                String raw = Files.readString(philFile);
                String capped = raw.length() <= remaining ? raw : raw.substring(0, remaining);
                block.append("\n\n=== ROLE ").append(role.getTag()).append(" PHILOSOPHER PATTERN (raw fallback - RAG retrieval unavailable): ")
                        .append(philFile.getFileName()).append(" ===\n").append(capped);
                remaining -= capped.length();
            }
        } catch (IOException e) {
            log.warn("GeminiContextService: raw fallback philosopher-pattern read failed for role {}: {}", role.getTag(), e.getMessage());
            return "";
        }
        return block.toString();
    }

    private String rawCommonPatternFallback() {
        try {
            Path path = Path.of("docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md");
            if (!Files.isRegularFile(path)) {
                return "";
            }
            String raw = Files.readString(path);
            String capped = raw.length() <= RAW_FALLBACK_MAX_CHARS
                    ? raw
                    : raw.substring(0, RAW_FALLBACK_MAX_CHARS) + "\n[...truncated, RAG retrieval unavailable...]";
            return "\n\n=== COMMON ANALYTIC PROGRAMMING PATTERNS (raw fallback - RAG retrieval unavailable) ===\n" + capped;
        } catch (IOException e) {
            log.warn("GeminiContextService: raw fallback common-patterns read failed: {}", e.getMessage());
            return "";
        }
    }

    /** Convenience wrapper: retrieves with the default top-k and formats a ready-to-inject prompt block. */
    /**
     * 2026-08-23. This retrieved across the ENTIRE index, so the method this factory judges by competed for
     * ranking slots with client briefs and requirement text - including those of finished test projects,
     * which outnumber the method roughly ten to one. The three contexts of section 3 are FACTORY, DELIVERY
     * and PRODUCT, and they are not merged; storing them in one table and ranking them with one cosine
     * merges them at retrieval, which is the same error one layer down. A chunk of an old brief outranking
     * a Frege pattern is not a relevance failure, it is a category error.
     *
     * The role-scoped and common-pattern builders were already restricted by source type. This one, used by
     * the operations auditor, was not - so the only reasoner without a scope was the one ruling on the
     * factory itself.
     */
    public String buildContextBlock(String query) {
        List<RetrievedChunk> retrieved = retrieveRelevantContextBySourceTypes(query, DEFAULT_TOP_K, METHOD_SOURCE_TYPES);
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

    // 2026-08-08: delegates to EmbeddingSimilarityUtil (extracted, ML-update patch Phase 2) so
    // FlowSpineService's duplicate-content detector can reuse the same computation without depending on
    // this RAG-indexing service. Kept as a thin static wrapper, not removed, so existing callers/tests in
    // this package don't need to change.
    static double cosineSimilarity(float[] a, float[] b) {
        return EmbeddingSimilarityUtil.cosineSimilarity(a, b);
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

    /**
     * Concatenates all 12 BARCAN-TAG charters and philosopher patterns into a static text block
     * suitable for Gemini Prompt Caching (cachedContents/*).
     */
    public String buildStaticCorpus() {
        if (repoRoot == null || repoRoot.isBlank()) {
            return "";
        }
        Path root = Path.of(repoRoot);
        StringBuilder sb = new StringBuilder();
        sb.append("=== BARCAN SYSTEM CHARTERS & PHILOSOPHER PATTERNS STANDING CORPUS ===\n\n");

        try (DirectoryStream<Path> charters = Files.newDirectoryStream(root, "BARCAN-TAG-*.md")) {
            for (Path charterFile : charters) {
                try {
                    sb.append("--- FILE: ").append(charterFile.getFileName()).append(" ---\n");
                    sb.append(Files.readString(charterFile, StandardCharsets.UTF_8)).append("\n\n");
                } catch (Exception e) {
                    log.warn("Failed to read charter file {}: {}", charterFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list BARCAN-TAG charter files: {}", e.getMessage());
        }

        Path philosophersDir = root.resolve("docs/philosopher-patterns/philosophers");
        if (Files.isDirectory(philosophersDir)) {
            try (DirectoryStream<Path> philosopherFiles = Files.newDirectoryStream(philosophersDir, "*.md")) {
                for (Path pFile : philosopherFiles) {
                    try {
                        sb.append("--- FILE: ").append(pFile.getFileName()).append(" ---\n");
                        sb.append(Files.readString(pFile, StandardCharsets.UTF_8)).append("\n\n");
                    } catch (Exception e) {
                        log.warn("Failed to read philosopher pattern file {}: {}", pFile, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to list philosopher pattern files: {}", e.getMessage());
            }
        }

        return sb.toString();
    }
}
