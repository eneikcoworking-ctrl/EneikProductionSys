package com.eneik.production.controllers.acceptance;

import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalDto;
import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalRequestDto;
import com.eneik.production.services.acceptance.ClientAcceptanceTraversalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * REST-контроллер фиксации институциональных фактов приёмки продукта (AGY_ASKS #1, V100).
 *
 * Паттерны:
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER [D007, Searle]
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_07_RIGHTS_DUTIES_MATRIX [D006, Searle]
 */
@RestController
@RequestMapping("/api/projects/{projectId}/acceptance-traversals")
public class ClientAcceptanceTraversalController {

    private final ClientAcceptanceTraversalService traversalService;

    public ClientAcceptanceTraversalController(ClientAcceptanceTraversalService traversalService) {
        this.traversalService = traversalService;
    }

    /**
     * Регистрирует обход цепочки ценности (POST /api/projects/{projectId}/acceptance-traversals).
     */
    @PostMapping
    public ResponseEntity<?> recordTraversal(
            @PathVariable UUID projectId,
            @RequestBody ClientAcceptanceTraversalRequestDto request) {
        try {
            ClientAcceptanceTraversalDto recorded = traversalService.recordTraversal(projectId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(recorded);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", e.getMessage(),
                    "code", 404
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "code", 400
            ));
        }
    }

    /**
     * Получает список зарегистрированных обходов (GET /api/projects/{projectId}/acceptance-traversals).
     */
    @GetMapping
    public ResponseEntity<?> listTraversals(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String walkedBy) {
        try {
            List<ClientAcceptanceTraversalDto> list = traversalService.listTraversals(projectId, walkedBy);
            return ResponseEntity.ok(list);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", e.getMessage(),
                    "code", 404
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "code", 400
            ));
        }
    }
}
