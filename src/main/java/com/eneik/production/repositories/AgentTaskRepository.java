package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AgentAccountEntity;
import com.eneik.production.models.persistence.AgentTaskEntity;
import com.eneik.production.models.persistence.AgentTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, UUID> {
    List<AgentTaskEntity> findAllByOrderByCreatedAtDesc();

    List<AgentTaskEntity> findByStatusOrderByCreatedAtAsc(AgentTaskStatus status);

    long countByClaimedByAndStatusIn(AgentAccountEntity account, Collection<AgentTaskStatus> statuses);

    long countByStatus(AgentTaskStatus status);
}
