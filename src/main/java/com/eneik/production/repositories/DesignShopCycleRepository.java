package com.eneik.production.repositories;

import com.eneik.production.models.persistence.DesignShopCycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesignShopCycleRepository extends JpaRepository<DesignShopCycleEntity, UUID> {
    Optional<DesignShopCycleEntity> findByProjectId(UUID projectId);
}
