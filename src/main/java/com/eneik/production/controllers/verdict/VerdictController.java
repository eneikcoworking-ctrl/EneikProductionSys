package com.eneik.production.controllers.verdict;

import com.eneik.production.services.verdict.VerdictReconciliation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only view of the one place where the flow's layers are reconciled.
 *
 * Nothing gates on this. It exists so the reconciliation can be observed against the live flow before
 * anything depends on it - a reading first, a decision later. Until every layer's measure has been
 * repaired some of them honestly owe an abstention rather than a number, and a gate is only ever as honest
 * as its inputs.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/verdict")
public class VerdictController {

    private final VerdictReconciliation reconciliation;

    public VerdictController(VerdictReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping
    public VerdictReconciliation.Reconciliation verdict(@PathVariable UUID projectId) {
        return reconciliation.reconcile(projectId);
    }
}
