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
        String maskedValue = definition.enabledFlag()
                ? null
                : definition.secret() ? mask(effective.value()) : effective.value();
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
        definitions.put("github_enabled", flag("github_enabled", "GITHUB_ENABLED", "github.enabled"));
        definitions.put("github_token", secret("github_token", "GITHUB_TOKEN", "github.token"));
        definitions.put("linear_enabled", flag("linear_enabled", "LINEAR_ENABLED", "linear.enabled"));
        definitions.put("linear_api_key", secret("linear_api_key", "LINEAR_API_KEY", "linear.api-key"));
        definitions.put("linear_team_id", plain("linear_team_id", "LINEAR_TEAM_ID", "linear.team-id"));
        definitions.put("jules_enabled", flag("jules_enabled", "JULES_ENABLED", "JULES_ENABLED"));
        definitions.put("jules_api_key", secret("jules_api_key", "JULES_API_KEY", "JULES_API_KEY"));
        definitions.put("gemini_enabled", flag("gemini_enabled", "GEMINI_ENABLED", "GEMINI_ENABLED"));
        definitions.put("gemini_api_key", secret("gemini_api_key", "GEMINI_API_KEY", "GEMINI_API_KEY"));
        definitions.put("gemini_model", plain("gemini_model", "GEMINI_MODEL", "gemini.model"));
        definitions.put("gemini_fallback_models", plain("gemini_fallback_models", "GEMINI_FALLBACK_MODELS", "gemini.fallback-models"));
        definitions.put("gemini_pro_model", plain("gemini_pro_model", "GEMINI_PRO_MODEL", "gemini.pro-model"));
        definitions.put("gemini_pro_fallback_models", plain("gemini_pro_fallback_models", "GEMINI_PRO_FALLBACK_MODELS", "gemini.pro-fallback-models"));
        definitions.put("google_search_grounding_enabled", flag("google_search_grounding_enabled", "GOOGLE_SEARCH_GROUNDING_ENABLED", "google-search-grounding.enabled"));
        definitions.put("url_context_enabled", flag("url_context_enabled", "URL_CONTEXT_ENABLED", "url-context.enabled"));
        definitions.put("design_service_enabled", flag("design_service_enabled", "DESIGN_SERVICE_ENABLED", "design-service.enabled"));
        definitions.put("nano_banana_enabled", flag("nano_banana_enabled", "NANO_BANANA_ENABLED", "nano-banana.enabled"));
        definitions.put("nano_banana_model", plain("nano_banana_model", "NANO_BANANA_MODEL", "nano-banana.model"));
        definitions.put("nano_banana_pro_model", plain("nano_banana_pro_model", "NANO_BANANA_PRO_MODEL", "nano-banana.pro-model"));
        definitions.put("veo_enabled", flag("veo_enabled", "VEO_ENABLED", "veo.enabled"));
        definitions.put("veo_model", plain("veo_model", "VEO_MODEL", "veo.model"));
        definitions.put("stitch_enabled", flag("stitch_enabled", "STITCH_ENABLED", "stitch.enabled"));
        definitions.put("stitch_api_key", secret("stitch_api_key", "STITCH_API_KEY", "stitch.api-key"));
        definitions.put("falsification_cycle_enabled", flag("falsification_cycle_enabled", "FALSIFICATION_CYCLE_ENABLED", "falsification-cycle.enabled"));
        // Independent of falsification_cycle_enabled above - the philosophical track (product-critique per
        // real philosopher, Kano-classified) is a separate generative track from the formal/corrective one
        // and must be toggleable on its own (operator directive, 2026-07-25).
        definitions.put("philosophical_falsification_enabled", flag("philosophical_falsification_enabled", "PHILOSOPHICAL_FALSIFICATION_ENABLED", "philosophical-falsification.enabled"));
        // Off by default (2026-07-25, testimony-vs-evidence Phase 2): periodic sweep that can WRITE task
        // status (marks a task failed when its PR was closed without merge on GitHub with no active claim
        // left to complete it normally) - a real write action, unlike Phase 1's read-only evidence checks,
        // so it gets its own explicit opt-in even though the underlying logic is low-risk (CAS-guarded,
        // only ever touches already-orphaned tasks).
        definitions.put("github_truth_reconciliation_enabled", flag("github_truth_reconciliation_enabled", "GITHUB_TRUTH_RECONCILIATION_ENABLED", "github-truth-reconciliation.enabled"));
        // Off by default (2026-07-25, operator directive: "мало системных решений... встроить такого же
        // аудитора как ты прямо внутрь бекенда"). An embedded Gemini-powered ops auditor that reasons over
        // real evidence (not guesses) and autonomously calls a curated, pre-vetted tool set to fix bounded-
        // risk operational issues (orphaned wishlists behind a terminally-failed task, etc.) - the same
        // pattern the operator watched the human orchestrator (this session) perform by hand all night.
        definitions.put("ops_auditor_enabled", flag("ops_auditor_enabled", "OPS_AUDITOR_ENABLED", "ops-auditor.enabled"));
        // Off by default (2026-07-25, operator directive: "нужно чтобы Джемини постоянно училась контексту
        // моей системы и в каждом вызове была максимально компетентна"). Gates GeminiContextService's
        // scheduled re-indexing (embeds OBSERVER_LOG/engineering-charter/role-charter text via the ML
        // service - a real, metered Gemini API cost) AND whether callers augment their prompts with
        // retrieved context at all - off means byte-for-byte the pre-existing prompt behavior, no surprise
        // cost the operator didn't opt into.
        definitions.put("gemini_context_learning_enabled", flag("gemini_context_learning_enabled", "GEMINI_CONTEXT_LEARNING_ENABLED", "gemini-context.learning-enabled"));
        // Gates GeminiProjectObserverService's scheduled analysis cycle (2026-07-25, operator directive:
        // "нужен был именно наблюдатель, джемини, который подключается раз в 30 минут"). Redesigned same
        // day after "она вообще должна была создать свой отдельный лог, а не работать с твоим" - the
        // observer now reasons over a structured evidence snapshot of real project state plus Gemini's own
        // journal (GeminiObserverJournalEntity), never the backend's internal Logback log.
        definitions.put("gemini_project_observer_enabled", flagDefaultTrue("gemini_project_observer_enabled", "GEMINI_PROJECT_OBSERVER_ENABLED", "gemini-project-observer.enabled"));
        // On by default (2026-07-26 restoration, operator directive: "лог проекта должен независеть от
        // деплоев!! это огромное упущение") - baseline infrastructure, not an opt-in experiment. Seeded
        // 'true' in V61. Gates only the DB write (ProjectEventLogService.flush); the appender always
        // enqueues so the kill switch can never leak into unbounded in-memory growth either way.
        definitions.put("project_event_log_enabled", flag("project_event_log_enabled", "PROJECT_EVENT_LOG_ENABLED", "project-event-log.enabled"));
        definitions.put("simulated_actuator_health", plain("simulated_actuator_health", "SIMULATED_ACTUATOR_HEALTH", "simulated.actuator.health"));
        definitions.put("system_stall_status", plain("system_stall_status", "SYSTEM_STALL_STATUS", "system-stall.status"));
        definitions.put("task_compiler_account_name", plain("task_compiler_account_name", "TASK_COMPILER_ACCOUNT_NAME", "task-compiler.account-name"));
        // Off by default: SystemStatusController's /sql endpoint is an unauthenticated raw JDBC executor
        // (SELECT and DML both). Must be explicitly opted into per-environment, never assumed safe.
        definitions.put("debug_sql_endpoint_enabled", flag("debug_sql_endpoint_enabled", "DEBUG_SQL_ENDPOINT_ENABLED", "debug.sql-endpoint.enabled"));
        return definitions;
    }

    private static SettingDefinition flagDefaultTrue(String key, String envName, String propertyName) {
        return new SettingDefinition(key, envName, propertyName, true, true);
    }

    private static SettingDefinition flag(String key, String envName, String propertyName) {
        return new SettingDefinition(key, envName, propertyName, true, false);
    }

    private static SettingDefinition secret(String key, String envName, String propertyName) {
        return new SettingDefinition(key, envName, propertyName, false, true);
    }

    private static SettingDefinition plain(String key, String envName, String propertyName) {
        return new SettingDefinition(key, envName, propertyName, false, false);
    }

    private record SettingDefinition(String key, String envName, String propertyName, boolean enabledFlag, boolean secret) {
    }

    private record EffectiveSetting(String value, String source) {
    }
}
