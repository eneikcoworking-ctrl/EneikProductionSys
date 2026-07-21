package com.eneik.production.services;

import com.eneik.production.dto.WishlistResponseDto;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.WishlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    public WishlistService(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public List<WishlistResponseDto> listByProject(UUID projectId, WishlistStatus status) {
        List<WishlistEntity> entities;
        if (status != null) {
            entities = wishlistRepository.findByProjectIdAndStatus(projectId, status);
        } else {
            entities = wishlistRepository.findByProjectId(projectId);
        }
        return entities.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public void dismiss(UUID id) {
        WishlistEntity entity = wishlistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wishlist item not found"));
        entity.setStatus(WishlistStatus.dismissed);
        // Hibernate dirty checking will handle the update
    }

    private WishlistResponseDto mapToDto(WishlistEntity entity) {
        return new WishlistResponseDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getSource(),
                entity.getSourceRoleTag(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
