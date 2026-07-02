package com.eneik.production.repositories;

import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {
    List<WishlistEntity> findByProjectId(UUID projectId);
    List<WishlistEntity> findByProjectIdAndStatus(UUID projectId, WishlistStatus status);
}
