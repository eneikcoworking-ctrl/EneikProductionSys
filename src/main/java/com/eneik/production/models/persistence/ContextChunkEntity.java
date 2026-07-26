package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One embedded chunk of standing system knowledge (OBSERVER_LOG entry, engineering-invariants excerpt,
 * BARCAN role charter section, ...) - see GeminiContextService. The embedding is stored as a comma-
 * separated float vector rather than a native vector column since H2 has none; cosine similarity is
 * computed in application code, so the storage format only needs to round-trip exactly.
 */
@Entity
@Table(name = "context_chunks")
public class ContextChunkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_ref", nullable = false, length = 256)
    private String sourceRef;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String content;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String embedding;

    @Column(name = "embedding_dims", nullable = false)
    private int embeddingDims;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
    public int getEmbeddingDims() { return embeddingDims; }
    public void setEmbeddingDims(int embeddingDims) { this.embeddingDims = embeddingDims; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
