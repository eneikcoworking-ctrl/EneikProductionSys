package com.eneik.production.toc.repository;

import com.eneik.production.toc.model.persistence.TocEdgeSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TocEdgeSnapshotRepository extends JpaRepository<TocEdgeSnapshotEntity, UUID> {

    Optional<TocEdgeSnapshotEntity> findFirstBySourceNodeAndTargetNodeOrderBySnapshotAtDesc(String sourceNode, String targetNode);

    @Query("SELECT e FROM TocEdgeSnapshotEntity e WHERE e.snapshotAt = (SELECT MAX(e2.snapshotAt) FROM TocEdgeSnapshotEntity e2 WHERE e2.sourceNode = e.sourceNode AND e2.targetNode = e.targetNode)")
    List<TocEdgeSnapshotEntity> findLatestSnapshotsForAllEdges();
}
