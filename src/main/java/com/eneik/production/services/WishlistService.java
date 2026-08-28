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

    @Transactional
    public void hardDelete(UUID id) {
        if (!wishlistRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wishlist item not found");
        }
        wishlistRepository.deleteById(id);
    }

    @Transactional
    public int purgeGhostWishlists(UUID projectId) {
        List<WishlistEntity> entities = wishlistRepository.findByProjectId(projectId);
        List<WishlistEntity> ghosts = entities.stream()
                .filter(e -> (e.getSource() != null && (
                                "delivery_never_reached_main".equalsIgnoreCase(e.getSource().name())
                                || "design_review_concern_pattern".equalsIgnoreCase(e.getSource().name())
                                || "gemini_observer".equalsIgnoreCase(e.getSource().name())
                             ))
                        || (e.getContent() != null && (
                                e.getContent().contains("Work that was reported as delivered never reached the main branch")
                                || e.getContent().contains("Six Sigma u-chart out of control")
                                || e.getContent().contains("orchestrator_tasks")
                           )))
                .toList();
        wishlistRepository.deleteAll(ghosts);
        return ghosts.size();
    }



    private WishlistResponseDto mapToDto(WishlistEntity entity) {
        return new WishlistResponseDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getSource(),
                entity.getSourceRoleTag(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getFeatureId()
        );
    }
}
