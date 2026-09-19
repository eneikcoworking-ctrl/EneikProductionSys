package com.eneik.production.repositories;

import com.eneik.production.models.persistence.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, String> {
    List<RoleEntity> findByActiveTrueOrderByTagAsc();
    List<RoleEntity> findAllByActiveTrueOrderByTagAsc();
    long countByActiveTrue();

    default List<RoleEntity> findAllByIsActiveTrueOrderByTagAsc() {
        return findByActiveTrueOrderByTagAsc();
    }
}
