package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.dashboard.CommandDashboardDto;
import com.eneik.production.services.dashboard.CommandDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/command-dashboard")
public class CommandDashboardController {

    private final CommandDashboardService commandDashboardService;

    public CommandDashboardController(CommandDashboardService commandDashboardService) {
        this.commandDashboardService = commandDashboardService;
    }

    @GetMapping
    public CommandDashboardDto getDashboard(@PathVariable UUID projectId) {
        return commandDashboardService.getDashboard(projectId);
    }
}
