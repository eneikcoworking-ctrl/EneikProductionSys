package com.eneik.production.toc.repository;

import com.eneik.production.toc.model.persistence.TocNodeSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TocNodeSnapshotRepository extends JpaRepository<TocNodeSnapshotEntity, UUID> {

    List<TocNodeSnapshotEntity> findByNodeNameOrderBySnapshotAtDesc(String nodeName);

    Optional<TocNodeSnapshotEntity> findFirstByNodeNameOrderBySnapshotAtDesc(String nodeName);

    @Query("SELECT s FROM TocNodeSnapshotEntity s WHERE s.snapshotAt = (SELECT MAX(s2.snapshotAt) FROM TocNodeSnapshotEntity s2 WHERE s2.nodeName = s.nodeName)")
    List<TocNodeSnapshotEntity> findLatestSnapshotsForAllNodes();
}
