package com.eneik.production.controllers;

import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.dto.WishlistResponseDto;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.services.WishlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WishlistResponseDto create(@Valid @RequestBody WishlistRequestDto request) {
        return wishlistService.create(request);
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
