package com.eneik.production.models.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * @file GreetingRepository.java
 * @agent TAG-08 (Substitutivity Salva Veritate)
 * @description Spring Data JPA Repository for Greetings.
 */
@Repository
public interface GreetingRepository extends JpaRepository<GreetingEntity, UUID> {
    // Custom query methods can be added here
}
