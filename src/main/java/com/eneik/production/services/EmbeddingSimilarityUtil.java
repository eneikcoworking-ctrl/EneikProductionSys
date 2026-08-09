package com.eneik.production.services;

/**
 * 2026-08-08 (ML-update patch, Phase 2): extracted out of GeminiContextService, whose own cosineSimilarity
 * was package-private and coupled every caller (e.g. FlowSpineService's duplicate-content detector) to a
 * dependency on the RAG-indexing service just to reuse basic vector math. One shared, dependency-free home
 * for this - both GeminiContextService and FlowSpineService call the SAME method now (invariant #14: never
 * two independently-maintained copies of the same computation).
 */
public final class EmbeddingSimilarityUtil {

    private EmbeddingSimilarityUtil() {
    }

    public static double cosineSimilarity(float[] a, float[] b) {
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
}
