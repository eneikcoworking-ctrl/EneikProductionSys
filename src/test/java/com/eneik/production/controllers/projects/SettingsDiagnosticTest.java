package com.eneik.production.controllers.projects;

import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("production") // Try production profile to see real settings if any
class SettingsDiagnosticTest {

    @Autowired
    private SystemSettingsService settingsService;

    @Test
    void printSettings() {
        System.out.println("GITHUB_TOKEN: " + settingsService.effectiveValue("github_token"));
        System.out.println("GITHUB_ORG: " + settingsService.effectiveValue("github_org"));
        System.out.println("GITHUB_ENABLED: " + settingsService.effectiveBoolean("github_enabled"));
    }
}
