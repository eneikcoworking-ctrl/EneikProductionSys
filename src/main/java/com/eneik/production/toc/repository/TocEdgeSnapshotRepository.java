package com.eneik.production.toc.repository;

import com.eneik.production.toc.model.persistence.TocEdgeSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TocEdgeSnapshotRepository extends JpaRepository<TocEdgeSnapshotEntity, UUID> {

    Optional<TocEdgeSnapshotEntity> findFirstBySourceNodeAndTargetNodeOrderBySnapshotAtDesc(String sourceNode, String targetNode);

    default List<TocEdgeSnapshotEntity> findLatestSnapshotsForAllEdges() {
        Map<String, TocEdgeSnapshotEntity> latest = new HashMap<>();
        for (TocEdgeSnapshotEntity e : findAll()) {
            String key = e.getSourceNode() + "->" + e.getTargetNode();
            TocEdgeSnapshotEntity existing = latest.get(key);
            if (existing == null || (e.getSnapshotAt() != null && (existing.getSnapshotAt() == null || e.getSnapshotAt().isAfter(existing.getSnapshotAt())))) {
                latest.put(key, e);
            }
        }
        return new ArrayList<>(latest.values());
    }
}