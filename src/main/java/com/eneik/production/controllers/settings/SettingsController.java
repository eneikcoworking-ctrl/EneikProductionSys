package com.eneik.production.controllers.settings;

import com.eneik.production.dto.settings.SettingDto;
import com.eneik.production.dto.settings.SettingUpdateRequest;
import com.eneik.production.services.settings.SystemSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SystemSettingsService settingsService;

    public SettingsController(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public List<SettingDto> list() {
        return settingsService.listSettings();
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestBody SettingUpdateRequest request) {
        if (request == null || request.key() == null || !settingsService.isKnownKey(request.key())) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown setting key"));
        }
        return ResponseEntity.ok(settingsService.save(request.key(), request.value()));
    }
}
