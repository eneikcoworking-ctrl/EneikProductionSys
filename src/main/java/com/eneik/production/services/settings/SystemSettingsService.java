package com.eneik.production.services.settings;

import com.eneik.production.dto.settings.SettingDto;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemSettingsService {

    private static final Map<String, SettingDefinition> DEFINITIONS = definitions();

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public SystemSettingsService(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    public List<SettingDto> listSettings() {
        return DEFINITIONS.keySet().stream()
                .map(this::toDto)
                .toList();
    }

    public SettingDto toDto(String key) {
        SettingDefinition definition = requireDefinition(key);
        EffectiveSetting effective = effectiveSetting(definition.key());
        Boolean enabled = definition.enabledFlag()
                ? Boolean.parseBoolean(effective.value())
                : null;
        String maskedValue = definition.enabledFlag() ? null : mask(effective.value());
        return new SettingDto(definition.key(), enabled, maskedValue, effective.source());
    }

    public boolean isKnownKey(String key) {
        return key != null && DEFINITIONS.containsKey(key);
    }

    public String effectiveValue(String key) {
        return effectiveSetting(key).value();
    }

    public boolean effectiveBoolean(String key) {
        return Boolean.parseBoolean(effectiveValue(key));
    }

    public String sourceOf(String key) {
        return effectiveSetting(key).source();
    }

    @Transactional
    public SettingDto save(String key, String value) {
        SettingDefinition definition = requireDefinition(key);
        int updated = jdbcTemplate.update(
                "UPDATE system_settings SET \"value\" = ?, updated_at = CURRENT_TIMESTAMP WHERE \"key\" = ?",
                value,
                definition.key()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO system_settings (\"key\", \"value\", updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    definition.key(),
                    value
            );
        }
        return toDto(definition.key());
    }

    private EffectiveSetting effectiveSetting(String key) {
        SettingDefinition definition = requireDefinition(key);
        Optional<String> databaseValue = databaseValue(definition.key());
        if (databaseValue.isPresent() && !databaseValue.get().isBlank()) {
            return new EffectiveSetting(databaseValue.get(), "database");
        }

        String envValue = firstNonBlank(
                environment.getProperty(definition.envName()),
                environment.getProperty(definition.propertyName()),
                System.getenv(definition.envName())
        );
        if (envValue != null) {
            return new EffectiveSetting(envValue, "env");
        }

        return new EffectiveSetting("", "none");
    }

    private Optional<String> databaseValue(String key) {
        try {
            return jdbcTemplate.query(
                    "SELECT \"value\" FROM system_settings WHERE \"key\" = ?",
                    rs -> rs.next() ? Optional.ofNullable(rs.getString("value")) : Optional.empty(),
                    key
            );
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    private SettingDefinition requireDefinition(String key) {
        SettingDefinition definition = key == null ? null : DEFINITIONS.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown setting key: " + key);
        }
        return definition;
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String suffix = value.length() <= 4 ? value : value.substring(value.length() - 4);
        return "****" + suffix;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, SettingDefinition> definitions() {
        Map<String, SettingDefinition> definitions = new LinkedHashMap<>();
        definitions.put("github_enabled", new SettingDefinition("github_enabled", "GITHUB_ENABLED", "github.enabled", true));
        definitions.put("github_token", new SettingDefinition("github_token", "GITHUB_TOKEN", "github.token", false));
        definitions.put("linear_enabled", new SettingDefinition("linear_enabled", "LINEAR_ENABLED", "linear.enabled", true));
        definitions.put("linear_api_key", new SettingDefinition("linear_api_key", "LINEAR_API_KEY", "linear.api-key", false));
        definitions.put("linear_team_id", new SettingDefinition("linear_team_id", "LINEAR_TEAM_ID", "linear.team-id", false));
        definitions.put("jules_enabled", new SettingDefinition("jules_enabled", "JULES_ENABLED", "JULES_ENABLED", true));
        definitions.put("jules_api_key", new SettingDefinition("jules_api_key", "JULES_API_KEY", "JULES_API_KEY", false));
        definitions.put("gemini_enabled", new SettingDefinition("gemini_enabled", "GEMINI_ENABLED", "GEMINI_ENABLED", true));
        definitions.put("gemini_api_key", new SettingDefinition("gemini_api_key", "GEMINI_API_KEY", "GEMINI_API_KEY", false));
        definitions.put("falsification_cycle_enabled", new SettingDefinition("falsification_cycle_enabled", "FALSIFICATION_CYCLE_ENABLED", "falsification-cycle.enabled", true));
        definitions.put("simulated_actuator_health", new SettingDefinition("simulated_actuator_health", "SIMULATED_ACTUATOR_HEALTH", "simulated.actuator.health", false));
        return definitions;
    }

    private record SettingDefinition(String key, String envName, String propertyName, boolean enabledFlag) {
    }

    private record EffectiveSetting(String value, String source) {
    }
}
