package com.eneik.production.controllers;

import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.dto.WishlistResponseDto;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.WishlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;
    private final ProjectFlowService projectFlowService;

    public WishlistController(WishlistService wishlistService, ProjectFlowService projectFlowService) {
        this.wishlistService = wishlistService;
        this.projectFlowService = projectFlowService;
    }

    // Ф-followup (2026-07-21): this used to call wishlistService.create(), a second, divergent wishlist-
    // creation implementation (no content trimming, defaulted null source silently instead of to
    // 'client', didn't check project state) living alongside ProjectController's
    // POST /{projectId}/wishlist -> projectFlowService.addWishlistItem(). Delegating to the same service
    // method both endpoints now share means there is exactly one business-rule implementation regardless
    // of which URL the frontend calls (CommandDashboardV2.svelte still uses both call sites).
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody WishlistRequestDto request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(projectFlowService.addWishlistItem(request.projectId(), request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @GetMapping
    public List<WishlistResponseDto> list(
            @RequestParam UUID projectId,
            @RequestParam(required = false) WishlistStatus status) {
        return wishlistService.listByProject(projectId, status);
    }

    @PatchMapping("/{id}/dismiss")
    public void dismiss(@PathVariable UUID id) {
        wishlistService.dismiss(id);
    }
}
