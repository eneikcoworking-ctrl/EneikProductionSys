package com.eneik.production.repositories;

import com.eneik.production.models.persistence.LeverPromotionStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeverPromotionStateRepository extends JpaRepository<LeverPromotionStateEntity, String> {
}
