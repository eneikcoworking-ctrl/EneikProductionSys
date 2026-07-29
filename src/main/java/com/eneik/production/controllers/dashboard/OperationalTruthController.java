package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.operational.OperationalTruthDto;
import com.eneik.production.services.operational.OperationalTruthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/operational-truth")
public class OperationalTruthController {
    private final OperationalTruthService operationalTruthService;

    public OperationalTruthController(OperationalTruthService operationalTruthService) {
        this.operationalTruthService = operationalTruthService;
    }

    @GetMapping
    public OperationalTruthDto getOperationalTruth(@PathVariable UUID projectId) {
        return operationalTruthService.build(projectId);
    }
}
