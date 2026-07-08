package com.eneik.production.repositories;

import com.eneik.production.models.persistence.FalsificationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FalsificationRunRepository extends JpaRepository<FalsificationRunEntity, UUID> {
    List<FalsificationRunEntity> findByProjectId(UUID projectId);
}
