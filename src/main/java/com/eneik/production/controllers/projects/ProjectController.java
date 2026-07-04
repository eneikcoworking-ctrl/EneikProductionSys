package com.eneik.production.controllers.projects;

import com.eneik.production.dto.*;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.ProjectFlowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectFlowService projectFlowService;
    private final ClaimService claimService;

    public ProjectController(ProjectFlowService projectFlowService, ClaimService claimService) {
        this.projectFlowService = projectFlowService;
        this.claimService = claimService;
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projectFlowService.listProjects();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProjectCreateRequestDto request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(projectFlowService.createProject(request.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @GetMapping("/{projectId}/dashboard")
    public ProjectDashboardDto dashboard(@PathVariable UUID projectId) {
        return projectFlowService.dashboard(projectId);
    }

    @PostMapping("/{projectId}/wishlist")
    public ResponseEntity<?> addWishlist(@PathVariable UUID projectId, @RequestBody WishlistItemRequestDto request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(projectFlowService.addWishlistItem(projectId, request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/orchestrate")
    public ResponseEntity<?> orchestrate(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.orchestrate(projectId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/claim")
    public ResponseEntity<?> claim(@PathVariable UUID projectId, @RequestBody ProjectClaimRequestDto request) {
        try {
            ClaimDto claim = claimService.claimForProject(projectId, request.accountId());
            if (claim == null) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(claim);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/accept")
    public ResponseEntity<?> accept(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.acceptProject(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/activate")
    public ResponseEntity<?> activate(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.activateProject(projectId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }
}
