package com.eneik.production.repositories;

import com.eneik.production.models.persistence.WishlistItemEntity;
import com.eneik.production.models.persistence.WishlistItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, UUID> {
    List<WishlistItemEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<WishlistItemEntity> findByProjectIdAndStatusOrderByCreatedAtAsc(UUID projectId, WishlistItemStatus status);
    long countByProjectIdAndStatus(UUID projectId, WishlistItemStatus status);
}
