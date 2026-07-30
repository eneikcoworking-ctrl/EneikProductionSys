package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.services.operational.FlowSpineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/flow-spine")
public class FlowSpineController {
    private final FlowSpineService flowSpineService;

    public FlowSpineController(FlowSpineService flowSpineService) {
        this.flowSpineService = flowSpineService;
    }

    @GetMapping
    public FlowSpineDto getFlowSpine(@PathVariable UUID projectId) {
        return flowSpineService.build(projectId);
    }
}
