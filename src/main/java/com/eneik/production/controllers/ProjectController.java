package com.eneik.production.controllers;

import com.eneik.production.dto.ProjectDto;
import com.eneik.production.dto.ProjectRequestDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectDto> create(@RequestBody ProjectRequestDto request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ProjectEntity project = projectService.createProject(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(project));
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projectService.listProjects().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> get(@PathVariable UUID id) {
        return projectService.getProject(id)
                .map(project -> ResponseEntity.ok(toDto(project)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ProjectDto> accept(@PathVariable UUID id) {
        return projectService.acceptProject(id)
                .map(project -> ResponseEntity.ok(toDto(project)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ProjectDto toDto(ProjectEntity entity) {
        return new ProjectDto(
                entity.getId(),
                entity.getName(),
                entity.getRepoUrl(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getAcceptedAt(),
                projectService.getAccountCount(entity.getId())
        );
    }
}
