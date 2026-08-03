package com.eneik.production.controllers;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.FeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Internal API for one-off manual data repair (2026-08-03, operator-authorized: restoring the
 * FeatureEntity/wishlist.featureId links a broken dedup tie-break wiped out on test-forty-first - see
 * ClientDeliverableReadinessService.deduplicateFeaturesByTitle's fix commit for the root cause).
 * Same localhost-restricted internal-controller pattern as InternalTaskController - a real, reusable
 * admin capability for this exact class of manual correction, not a one-off hack.
 */
@RestController
@RequestMapping("/internal/repair")
public class InternalRepairController {

    private final FeatureService featureService;
    private final WishlistRepository wishlistRepository;

    public InternalRepairController(FeatureService featureService, WishlistRepository wishlistRepository) {
        this.featureService = featureService;
        this.wishlistRepository = wishlistRepository;
    }

    @PostMapping("/features")
    public ResponseEntity<FeatureEntity> createFeature(@RequestBody Map<String, String> body) {
        UUID projectId = UUID.fromString(body.get("projectId"));
        UUID rootWishlistId = UUID.fromString(body.get("rootWishlistId"));
        FeatureEntity feature = featureService.createFeature(projectId, rootWishlistId,
                body.get("title"), body.get("jtbd"), body.get("kanoClass"), body.get("cynefinDomain"),
                body.get("sixSigmaMetric"), body.get("tocConstraintRef"));
        return ResponseEntity.ok(feature);
    }

    @GetMapping("/wishlist/{id}")
    public ResponseEntity<WishlistEntity> getWishlist(@PathVariable UUID id) {
        return wishlistRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/wishlist/{id}/feature")
    public ResponseEntity<Void> setWishlistFeatureId(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        WishlistEntity item = wishlistRepository.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        String rawFeatureId = body.get("featureId");
        item.setFeatureId(rawFeatureId == null || rawFeatureId.isBlank() ? null : UUID.fromString(rawFeatureId));
        wishlistRepository.save(item);
        return ResponseEntity.ok().build();
    }
}
