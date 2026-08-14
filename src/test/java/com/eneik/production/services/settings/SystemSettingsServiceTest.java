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

    /**
     * 2026-08-14: the same failure happened a second time. JulesDispatchService read
     * "system_orchestrator_repository_name" - carrying a fallback for when it is unset, which never
     * applied because requireDefinition throws on an unregistered key first - so every ORCHESTRATOR_SYSTEM
     * dispatch failed with HTTP 400 the first time that path was ever exercised.
     *
     * A per-key test only ever covers keys someone remembered to add, which is the same memory that failed
     * both times. This scans the production sources instead: any literal key read through effectiveValue or
     * effectiveBoolean must exist in the registry. It fails on the NEXT such key without anyone updating
     * this test, which is the only way this stops recurring.
     */
    @Test
    void everySettingKeyReadByProductionCodeIsRegistered() throws Exception {
        java.nio.file.Path sourceRoot = java.nio.file.Paths.get("src/main/java");
        java.util.regex.Pattern usage =
                java.util.regex.Pattern.compile("effective(?:Value|Boolean)\\(\"([a-z0-9_]+)\"");

        java.util.Map<String, String> keyToFile = new java.util.TreeMap<>();
        try (var paths = java.nio.file.Files.walk(sourceRoot)) {
            for (java.nio.file.Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = usage.matcher(java.nio.file.Files.readString(file));
                while (matcher.find()) {
                    keyToFile.putIfAbsent(matcher.group(1), file.getFileName().toString());
                }
            }
        }

        assertTrue(keyToFile.size() > 5,
                "the scan found suspiciously few setting reads (" + keyToFile.size() + ") - the pattern or "
                        + "the source path is probably wrong, which would make this test pass vacuously");

        java.util.List<String> unregistered = keyToFile.entrySet().stream()
                .filter(e -> !service.isKnownKey(e.getKey()))
                .map(e -> e.getKey() + " (read in " + e.getValue() + ")")
                .toList();

        assertTrue(unregistered.isEmpty(),
                "these setting keys are read by production code but are not registered in "
                        + "SystemSettingsService, so every call site throws IllegalArgumentException at "
                        + "runtime before any fallback can apply: " + unregistered);
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
