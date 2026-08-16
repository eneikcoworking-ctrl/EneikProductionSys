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

    /**
     * Manual/admin escape hatch for a claim that's stuck open on a task the operator no longer cares
     * about (e.g. leftover from an old project) - requeues the task and frees the holding account, same
     * mechanism createProject() now runs automatically for every account on greenfield creation.
     */
    @PostMapping("/{id}/release")
    public ResponseEntity<Void> release(@PathVariable UUID id, @RequestBody(required = false) ReleaseRequest request) {
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "Released by operator";
        claimService.releaseClaimToQueue(id, reason);
        return ResponseEntity.ok().build();
    }

    public record ReleaseRequest(String reason) {}

    /**
     * Permanent kill for a task that should never run (e.g. the losing half of a confirmed duplicate
     * pair) - unlike /release (requeues) this is inert, same as JulesDispatchService.cancelSession's own
     * closeTaskAsFailed call for a task that never got as far as having a session to cancel.
     */
    @PostMapping("/{id}/close-failed")
    public ResponseEntity<Void> closeFailed(@PathVariable UUID id, @RequestBody(required = false) ReleaseRequest request) {
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "Closed by operator";
        claimService.closeTaskAsFailed(id, reason);
        return ResponseEntity.ok().build();
    }
}
