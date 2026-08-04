package com.eneik.production.services.settings;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 2026-08-04 (live incident, design/QA acceptance redesign, Phase B): DesignSystemFalsificationService
 * called settingsService.effectiveBoolean("design_system_falsification_enabled") without that key ever
 * being registered in SystemSettingsService's own definitions - every 30-minute cron tick threw
 * IllegalArgumentException before the flag check could even short-circuit, silently and completely
 * preventing the whole falsification pass from ever running. No existing test caught this because
 * DesignSystemFalsificationServiceTest mocks SystemSettingsService entirely, never exercising the real
 * key registry. This locks in that every setting key actually read by production code is registered.
 */
class SystemSettingsServiceTest {

    private final SystemSettingsService service =
            new SystemSettingsService(mock(JdbcTemplate.class), mock(Environment.class));

    @Test
    void designSystemFalsificationEnabledKeyIsRegistered() {
        assertTrue(service.isKnownKey("design_system_falsification_enabled"));
    }

    @Test
    void stitchApiKeyIsRegistered() {
        assertTrue(service.isKnownKey("stitch_api_key"));
    }

    @Test
    void stitchEnabledKeyIsRegistered() {
        assertTrue(service.isKnownKey("stitch_enabled"));
    }

    @Test
    void debugSqlEndpointEnabledKeyIsRegistered() {
        assertTrue(service.isKnownKey("debug_sql_endpoint_enabled"));
    }
}
