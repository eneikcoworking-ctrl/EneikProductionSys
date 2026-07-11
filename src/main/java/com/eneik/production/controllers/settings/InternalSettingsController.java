package com.eneik.production.controllers.settings;

import com.eneik.production.services.settings.SystemSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/settings")
public class InternalSettingsController {

    private final SystemSettingsService settingsService;

    public InternalSettingsController(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/gemini")
    public ResponseEntity<?> getGeminiSettings() {
        boolean enabled = settingsService.effectiveBoolean("gemini_enabled");
        String apiKey = settingsService.effectiveValue("gemini_api_key");
        return ResponseEntity.ok(Map.of(
                "api_key", apiKey,
                "enabled", enabled
        ));
    }

    @PostMapping("/resolve")
    public ResponseEntity<?> resolve(@RequestBody Map<String, String> request) {
        String key = request == null ? null : request.get("key");
        if (!settingsService.isKnownKey(key)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown setting key"));
        }
        return ResponseEntity.ok(Map.of(
                "key", key,
                "value", settingsService.effectiveValue(key),
                "source", settingsService.sourceOf(key)
        ));
    }
}
