package com.eneik.production.controllers.dashboard;

import com.eneik.production.services.dashboard.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-status")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;
    private final com.eneik.production.repositories.AccountRepository accountRepository;

    public SystemStatusController(SystemStatusService systemStatusService,
                                  com.eneik.production.repositories.AccountRepository accountRepository) {
        this.systemStatusService = systemStatusService;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public Map<String, Object> getStatus() {
        return systemStatusService.getStatus();
    }

    @GetMapping("/debug-keys")
    public List<Map<String, Object>> getDebugKeys() {
        return accountRepository.findAll().stream().map(a -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("name", a.getName());
            map.put("status", a.getStatus().toString());
            map.put("apiKey", a.getApiKey());
            map.put("enabled", a.isEnabled());
            return map;
        }).toList();
    }
}
