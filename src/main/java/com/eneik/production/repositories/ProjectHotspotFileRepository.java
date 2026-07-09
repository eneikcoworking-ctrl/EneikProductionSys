package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProjectHotspotFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectHotspotFileRepository extends JpaRepository<ProjectHotspotFileEntity, UUID> {
    List<ProjectHotspotFileEntity> findByProjectId(UUID projectId);
}
