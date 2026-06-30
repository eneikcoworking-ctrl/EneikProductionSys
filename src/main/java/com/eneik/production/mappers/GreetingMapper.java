package com.eneik.production.mappers;

import com.eneik.production.dto.GreetingResponseDTO;
import com.eneik.production.models.domain.Greeting;
import com.eneik.production.models.persistence.GreetingEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Entity, Domain, and DTO layers.
 */
@Component
public class GreetingMapper {

    public Greeting toDomain(GreetingEntity entity) {
        if (entity == null) return null;
        return new Greeting(
            entity.getId(),
            entity.getMessage(),
            entity.getCurrentStatus(),
            entity.getCreatedAt(),
            entity.getProcessingStartedAt(),
            entity.getCompletedAt()
        );
    }

    public GreetingResponseDTO toResponseDTO(Greeting domain) {
        if (domain == null) return null;
        return GreetingResponseDTO.builder()
            .id(domain.getId())
            .message(domain.getMessage())
            .currentStatus(domain.getCurrentStatus())
            .createdAt(domain.getCreatedAt())
            .leadTimeSeconds(domain.getLeadTimeSeconds())
            .build();
    }
}
