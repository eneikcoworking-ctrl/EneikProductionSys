package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ContextChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContextChunkRepository extends JpaRepository<ContextChunkEntity, UUID> {
    List<ContextChunkEntity> findBySourceRef(String sourceRef);

    List<ContextChunkEntity> findBySourceType(String sourceType);

    boolean existsBySourceRefAndContentHash(String sourceRef, String contentHash);

    @Query("select c.id as id, c.sourceType as sourceType, c.sourceRef as sourceRef, "
            + "c.embedding as embedding, c.embeddingDims as embeddingDims from ContextChunkEntity c")
    List<VectorRow> findAllVectorRows();

    @Query("select c.id as id, c.sourceType as sourceType, c.sourceRef as sourceRef, "
            + "c.embedding as embedding, c.embeddingDims as embeddingDims from ContextChunkEntity c "
            + "where c.sourceType in :sourceTypes")
    List<VectorRow> findVectorRowsBySourceTypeIn(@Param("sourceTypes") List<String> sourceTypes);

    @Query("select c.id as id, c.sourceType as sourceType, c.sourceRef as sourceRef, "
            + "c.embedding as embedding, c.embeddingDims as embeddingDims from ContextChunkEntity c "
            + "where c.sourceRef is not null and c.sourceRef like concat(:sourceRefPrefix, '%')")
    List<VectorRow> findVectorRowsBySourceRefStartingWith(@Param("sourceRefPrefix") String sourceRefPrefix);

    interface VectorRow {
        UUID getId();
        String getSourceType();
        String getSourceRef();
        String getEmbedding();
        int getEmbeddingDims();
    }

    // Re-indexing a source is delete-then-insert (see GeminiContextService.indexDocument) so editing a
    // doc never leaves stale chunks from a previous, longer version of the same source behind.
    @Modifying
    @Transactional
    @Query("DELETE FROM ContextChunkEntity c WHERE c.sourceRef = :sourceRef")
    void deleteBySourceRef(String sourceRef);
}
