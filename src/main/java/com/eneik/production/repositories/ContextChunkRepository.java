package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ContextChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContextChunkRepository extends JpaRepository<ContextChunkEntity, UUID> {
    List<ContextChunkEntity> findBySourceRef(String sourceRef);

    List<ContextChunkEntity> findBySourceType(String sourceType);

    // Re-indexing a source is delete-then-insert (see GeminiContextService.indexDocument) so editing a
    // doc never leaves stale chunks from a previous, longer version of the same source behind.
    @Modifying
    @Transactional
    @Query("DELETE FROM ContextChunkEntity c WHERE c.sourceRef = :sourceRef")
    void deleteBySourceRef(String sourceRef);
}
