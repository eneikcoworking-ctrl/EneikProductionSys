package com.eneik.production.services;

import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.dto.WishlistResponseDto;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
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

    // Placeholder for actual project existence check until projects table is available
    public static final UUID VALID_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final WishlistRepository wishlistRepository;
    private final com.eneik.production.repositories.ProjectRepository projectRepository;

    public WishlistService(WishlistRepository wishlistRepository,
                           com.eneik.production.repositories.ProjectRepository projectRepository) {
        this.wishlistRepository = wishlistRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public WishlistResponseDto create(WishlistRequestDto request) {
        validateProjectExists(request.projectId());
        validateSourceAndTag(request.source(), request.sourceRoleTag());

        WishlistEntity entity = new WishlistEntity();
        entity.setProjectId(request.projectId());
        entity.setSource(request.source());
        entity.setSourceRoleTag(request.sourceRoleTag());
        entity.setContent(request.content());
        entity.setStatus(WishlistStatus.pending);

        WishlistEntity saved = wishlistRepository.save(entity);
        return mapToDto(saved);
    }

    private void validateProjectExists(UUID projectId) {
        if (!VALID_PROJECT_ID.equals(projectId) && !projectRepository.existsById(projectId)) {
             throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
        }
    }

    private void validateSourceAndTag(WishlistSource source, String sourceRoleTag) {
        if (source == WishlistSource.role && (sourceRoleTag == null || sourceRoleTag.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceRoleTag is required when source is 'role'");
        }
        if (source == WishlistSource.client && sourceRoleTag != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceRoleTag must be null when source is 'client'");
        }
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
