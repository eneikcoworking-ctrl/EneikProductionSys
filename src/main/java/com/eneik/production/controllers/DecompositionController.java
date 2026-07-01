package com.eneik.production.controllers;

import com.eneik.production.dto.DecompositionResponseDto;
import com.eneik.production.dto.RequirementRequestDto;
import com.eneik.production.services.task.DecompositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requirements")
@CrossOrigin(origins = "http://localhost:3000")
public class DecompositionController {

    private final DecompositionService decompositionService;

    public DecompositionController(DecompositionService decompositionService) {
        this.decompositionService = decompositionService;
    }

    @PostMapping
    public ResponseEntity<DecompositionResponseDto> createRequirement(@RequestBody RequirementRequestDto request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        DecompositionResponseDto response = decompositionService.decompose(request.text());
        return ResponseEntity.ok(response);
    }
}
