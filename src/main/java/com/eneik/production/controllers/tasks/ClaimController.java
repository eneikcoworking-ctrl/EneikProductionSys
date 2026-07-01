package com.eneik.production.controllers.tasks;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.dto.ClaimRequestDto;
import com.eneik.production.services.ClaimService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping("/claim")
    public ResponseEntity<ClaimDto> claim(@RequestBody ClaimRequestDto request) {
        ClaimDto claim = claimService.claim(request.accountId(), request.capableTags());
        if (claim == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(claim);
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID id) {
        claimService.heartbeat(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable UUID id) {
        claimService.complete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<Void> fail(@PathVariable UUID id) {
        claimService.fail(id);
        return ResponseEntity.ok().build();
    }
}
