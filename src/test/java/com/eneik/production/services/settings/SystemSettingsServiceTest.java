package com.eneik.production.services.settings;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    /**
     * A registered flag with no value anywhere reads false and turns its feature off with nobody having
     * decided that - and at every call site it is indistinguishable from a deliberate false. This has cost
     * real capability twice (design_system_falsification_enabled silently disabled a whole falsification
     * pass). The reporter must therefore actually name them, or the condition stays exactly as invisible
     * as it was.
     */
    @Test
    void namesEveryBooleanFlagThatHasNoValueAnywhere() {
        // A service with no database rows and an environment that answers nothing: every boolean flag
        // resolves with source "none", which is the pathological case this reporter exists to catch.
        SystemSettingsService bare = new SystemSettingsService(mock(JdbcTemplate.class), mock(Environment.class));

        assertDoesNotThrow(bare::reportValuelessBooleanFlags,
                "reporting must never be able to prevent startup - a factory that refuses to boot over an "
                        + "unset flag trades a silent gap for a total outage");

        long valuelessFlags = bare.listSettings().stream()
                .filter(dto -> dto.enabled() != null)
                .filter(dto -> "none".equals(dto.source()))
                .count();
        assertTrue(valuelessFlags > 0,
                "with no database and no environment every boolean flag should resolve to source 'none' - "
                        + "if this is zero the fixture no longer exercises the case it was written for");
    }

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
    void aLinearTeamIdThatIsNotATeamIdIsRefusedAtTheBoundary() {
        // F6, 2026-08-23. The stored value is "Eneik Production System" - the team NAME, where Linear
        // requires the team UUID. LinearProjectFactoryClient detects this correctly and skips, on every
        // project, silently, for as long as the value stands. The check existed; it was asked at the one
        // moment nothing could be done about it. A designator that picks out no object is not an id.
        SystemSettingsService settings =
                new SystemSettingsService(mock(JdbcTemplate.class), mock(Environment.class));

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> settings.save("linear_team_id", "Eneik Production System"));
        assertTrue(refusal.getMessage().contains("must be a Linear team UUID"));

        // The pair: a real team id is stored without complaint, so this refuses the malformed value only.
        assertDoesNotThrow(
                () -> settings.save("linear_team_id", "3f2504e0-4f89-11d3-9a0c-0305e82c3301"));
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
