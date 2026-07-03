package com.eneik.production.repositories;

import com.eneik.production.models.persistence.JulesConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JulesConfigRepository extends JpaRepository<JulesConfigEntity, UUID> {
    List<JulesConfigEntity> findByEnabledTrue();
}
