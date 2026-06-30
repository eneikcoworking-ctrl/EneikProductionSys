package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AgentAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentAccountRepository extends JpaRepository<AgentAccountEntity, UUID> {
    Optional<AgentAccountEntity> findByAccountCode(String accountCode);

    List<AgentAccountEntity> findAllByOrderByAccountCodeAsc();
}
