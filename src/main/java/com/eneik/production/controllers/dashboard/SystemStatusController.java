package com.eneik.production.controllers.dashboard;

import com.eneik.production.services.dashboard.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system-status")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping
    public Map<String, Object> getStatus() {
        return systemStatusService.getStatus();
    }
}
