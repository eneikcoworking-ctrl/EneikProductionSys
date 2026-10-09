package com.eneik.production.toc.repository;

import com.eneik.production.toc.model.persistence.TocNodeSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TocNodeSnapshotRepository extends JpaRepository<TocNodeSnapshotEntity, UUID> {

    List<TocNodeSnapshotEntity> findByNodeNameOrderBySnapshotAtDesc(String nodeName);

    Optional<TocNodeSnapshotEntity> findFirstByNodeNameOrderBySnapshotAtDesc(String nodeName);

    default List<TocNodeSnapshotEntity> findLatestSnapshotsForAllNodes() {
        Map<String, TocNodeSnapshotEntity> latest = new HashMap<>();
        for (TocNodeSnapshotEntity s : findAll()) {
            TocNodeSnapshotEntity existing = latest.get(s.getNodeName());
            if (existing == null || (s.getSnapshotAt() != null && (existing.getSnapshotAt() == null || s.getSnapshotAt().isAfter(existing.getSnapshotAt())))) {
                latest.put(s.getNodeName(), s);
            }
        }
        return new ArrayList<>(latest.values());
    }
}
