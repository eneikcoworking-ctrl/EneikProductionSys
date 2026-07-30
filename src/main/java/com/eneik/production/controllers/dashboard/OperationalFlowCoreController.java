package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.services.operational.OperationalFlowCoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/flow-core")
public class OperationalFlowCoreController {
    private final OperationalFlowCoreService flowCoreService;

    public OperationalFlowCoreController(OperationalFlowCoreService flowCoreService) {
        this.flowCoreService = flowCoreService;
    }

    @GetMapping
    public FlowCoreDto getFlowCore(@PathVariable UUID projectId) {
        return flowCoreService.build(projectId);
    }

    @PostMapping("/observe")
    public FlowCoreDto observeFlowCore(@PathVariable UUID projectId) {
        return flowCoreService.observe(projectId);
    }

    @GetMapping("/events")
    public List<FlowCoreDto.DecisionEvent> events(@PathVariable UUID projectId,
                                                  @RequestParam(required = false, defaultValue = "50") int limit) {
        return flowCoreService.events(projectId, limit);
    }
}
