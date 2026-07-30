package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.services.operational.FlowSpineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    @PostMapping("/observe")
    public FlowSpineDto observeFlowSpine(@PathVariable UUID projectId) {
        return flowSpineService.observe(projectId);
    }

    @GetMapping("/events")
    public List<FlowSpineDto.FlowEvent> events(@PathVariable UUID projectId,
                                               @RequestParam(required = false, defaultValue = "50") int limit) {
        return flowSpineService.events(projectId, limit);
    }
}
