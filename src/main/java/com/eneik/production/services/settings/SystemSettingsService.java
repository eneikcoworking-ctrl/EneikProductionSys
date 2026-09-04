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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SystemSettingsService.class);

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

    /**
     * Names, at startup, every boolean flag that is registered and has no value anywhere.
     *
     * A flag whose source is `none` reads `false` through {@link #effectiveBoolean}, so its feature is off
     * and nobody decided that - it is indistinguishable from a deliberate `false` at every call site. This
     * has now cost real capability twice: `design_system_falsification_enabled` silently disabled an entire
     * falsification pass, and `system_orchestrator_repository_name` produced "Unknown setting key" errors
     * until it was registered. A registered-but-valueless flag is a defect, not a default, and the only
     * reason it survived is that nothing ever said it out loud.
     *
     * Reports rather than refuses to start: a factory that will not boot because a flag is unset trades a
     * silent gap for a total outage, which is a worse failure for a system meant to run unattended.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void reportValuelessBooleanFlags() {
        java.util.List<String> valueless;
        try {
            valueless = DEFINITIONS.values().stream()
                    .filter(SettingDefinition::enabledFlag)
                    // 2026-08-23: source "none" was too narrow, and the gap is exactly where this defect
                    // lives. A flag written once and then cleared keeps its row, so its source is
                    // "database" while its value is empty - a source, and no content. Measured: all three
                    // falsification flags sat in that state, this reporter said nothing because each had a
                    // source, and the core of the method was off on every project. The question is whether
                    // anyone ever DECIDED, and an empty value answers it as plainly as a missing row.
                    .filter(d -> {
                        EffectiveSetting setting = effectiveSetting(d.key());
                        return "none".equals(setting.source())
                                || setting.value() == null || setting.value().isBlank();
                    })
                    .map(SettingDefinition::key)
                    .sorted()
                    .toList();
        } catch (RuntimeException e) {
            // A reporter that can throw is a reporter that can become the very outage it exists to
            // prevent. Reading settings must never be able to break the startup it is describing.
            log.warn("SystemSettingsService: could not check for valueless boolean flags: {}", e.getMessage());
            return;
        }
        if (valueless.isEmpty()) {
            return;
        }
        log.warn("SystemSettingsService: {} boolean flag(s) are registered with NO value in database, env or "
                        + "properties, so they read false and their features are OFF without anyone having "
                        + "decided that: {}. Set them explicitly - an unset flag is a defect, not a default.",
                valueless.size(), valueless);
    }

    public boolean isKnownKey(String key) {
        return key != null && DEFINITIONS.containsKey(key);
    }

    public String effectiveValue(String key) {
        return effectiveSetting(key).value();
    }

    /**
     * 2026-08-23. `Boolean.parseBoolean` maps a blank, a null and the string "off" all to false, so a flag
     * that was never given a value is indistinguishable from a flag someone deliberately turned off. That
     * is not a parsing detail, it is a decision taken by a default (ACP-104): measured on test-fiftieth,
     * `falsification_cycle_enabled`, `philosophical_falsification_enabled` and
     * `design_system_falsification_enabled` all held an empty string in the database, and the log said
     * "Falsification cycle is disabled via feature flag" - true of what the code did, false about anyone
     * having chosen it. The core of this system's method was off because nobody had said it was on.
     *
     * `reportValuelessBooleanFlags` exists for this and could not have caught it: it looks for flags whose
     * source is "none", and these had a row in the database with an empty value - a source, and no content.
     *
     * The value is still false. What changes is that the absence is now audible the first time it decides
     * anything, so an unmade decision cannot go on being made silently.
     */
    public boolean effectiveBoolean(String key) {
        String value = effectiveValue(key);
        if (value == null || value.isBlank()) {
            if (unreportedBlankFlags.add(key)) {
                log.warn("SystemSettingsService: flag '{}' has no value, so it reads as false - and nothing "
                        + "on record says anyone chose that. If it should be on, set it; if it should be "
                        + "off, set it to false so the decision is a decision.", key);
            }
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    public double effectiveDouble(String key, double defaultValue) {
        String value = effectiveValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int effectiveInt(String key, int defaultValue) {
        String value = effectiveValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** One warning per key per boot: audible, never a flood. */
    private final java.util.Set<String> unreportedBlankFlags = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public String sourceOf(String key) {
        return effectiveSetting(key).source();
    }

    @Transactional
    public SettingDto save(String key, String value) {
        SettingDefinition definition = requireDefinition(key);
        rejectIfMalformed(definition.key(), value);
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

    /**
     * Validate at the boundary, once, loudly - instead of at every use, forever, quietly.
     *
     * 2026-08-23 (F6). `linear_team_id` holds the string "Eneik Production System", a team NAME where
     * Linear's ProjectCreateInput.teamIds requires a UUID. LinearProjectFactoryClient detects this
     * perfectly well and skips - on every project, silently, for as long as the value stands. The check was
     * always there; it was simply asked at the moment nothing could be done about it. A value that can
     * never be correct should not be storable, and a designator that picks out no object is not an
     * identifier no matter how readable it is.
     */
    private void rejectIfMalformed(String key, String value) {
        // The blank case does NOT return early here. Blank is precisely the value this guard exists to
        // refuse for a flag, and putting the exemption first would have made the check unreachable for its
        // own defect - the same shape of mistake as a repair placed on the path the defect does not take.
        // Format checks that only apply to content still skip a blank below.
        // 2026-08-23. A boolean flag may not be stored blank. This has cost real capability three times -
        // design_system_falsification_enabled, then all three falsification flags together - and every
        // time the mechanism was the same: a row exists, its value is empty, effectiveBoolean reads false,
        // and nothing distinguishes that from someone deciding false. Refusing the write is the only point
        // where the two can still be told apart; afterwards the information is simply gone. Whoever means
        // off writes "false", and the decision survives as a decision.
        SettingDefinition definition = DEFINITIONS.get(key);
        if (definition != null && definition.enabledFlag()) {
            String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            if (!"true".equals(normalized) && !"false".equals(normalized)) {
                throw new IllegalArgumentException(
                        "Flag '" + key + "' must be stored as true or false, not '" + value + "'. A flag "
                        + "with an empty or unrecognised value reads as false, and nothing afterwards can "
                        + "tell that apart from someone having chosen false.");
            }
        }

        if (value == null || value.isBlank()) {
            return;
        }

        if ("linear_team_id".equals(key)) {
            try {
                java.util.UUID.fromString(value.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "linear_team_id must be a Linear team UUID, not '" + value + "'. Linear's "
                        + "ProjectCreateInput.teamIds takes the id, not the team's name or key - open the "
                        + "team in Linear and copy the id out of its settings URL. Stored as it is, every "
                        + "project silently skips Linear.");
            }
        }
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
            // JdbcTemplate.query(String, ResultSetExtractor, Object...) is declared @Nullable, so this
            // Optional-returning method could itself return null and every caller does .isPresent() on it
            // without a null check (2026-08-15 - found by the new startup reporter, which NPE'd here). An
            // Optional that can be null defeats the entire point of returning an Optional.
            Optional<String> value = jdbcTemplate.query(
                    "SELECT \"value\" FROM system_settings WHERE \"key\" = ?",
                    rs -> rs.next() ? Optional.ofNullable(rs.getString("value")) : Optional.empty(),
                    key
            );
            return value == null ? Optional.empty() : value;
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
        // 2026-08-21: factory-level judgment (FactoryJudgmentService). ONE flag and no credential at all -
        // the judgment-sidecar runs the operator's already-paid Claude subscription, so there is nothing
        // to supply. The first build of this registered judgment_agent_api_key alongside it, following the
        // provider pattern; that was the wrong pattern, because it meant a metered API account and a
        // balance, which is what the plan had promised NOT to require. Removed rather than left unused: a
        // registered setting nobody can fill is a question the operator is asked forever.
        definitions.put("judgment_agent_enabled", flag("judgment_agent_enabled",
                "JUDGMENT_AGENT_ENABLED", "judgment-agent.enabled"));
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
        // 2026-08-04 (design/QA acceptance redesign, Phase B): live incident - this key was read via
        // effectiveBoolean in DesignSystemFalsificationService without ever being registered here, so
        // every 30-minute cron tick threw IllegalArgumentException("Unknown setting key") before the flag
        // check could even short-circuit - the whole falsification pass never ran, not once, silently.
        definitions.put("design_system_falsification_enabled", flag("design_system_falsification_enabled",
                "DESIGN_SYSTEM_FALSIFICATION_ENABLED", "design-system-falsification.enabled"));
        // Off by default: the parallel "design shop" cycle (DesignShopOrchestrationService) - fires a
        // Stitch mockup + autonomous design review + BARCAN-TAG-11 implementation task each time a
        // project's readiness ratio rises to 1.0 (see ClientDeliverableReadinessService), reusing the
        // same generateAsset/dispatchDesignReview building blocks already live in production.
        definitions.put("design_shop_enabled", flag("design_shop_enabled",
                "DESIGN_SHOP_ENABLED", "design-shop.enabled"));
        definitions.put("design_shop_readiness_threshold", plain("design_shop_readiness_threshold",
                "DESIGN_SHOP_READINESS_THRESHOLD", "design-shop.readiness-threshold"));
        definitions.put("max_repair_depth", plain("max_repair_depth",
                "MAX_REPAIR_DEPTH", "delivery.max-repair-depth"));
        // Off by default (Step 18). Lets the verdict lattice constrain the readiness the factory REPORTS -
        // never what the client may do. See VerdictGate: acceptance is the client's act of ending an
        // engagement, and a lattice that abstains must not be able to stop them ending it.
        definitions.put("verdict_gating_enabled", flag("verdict_gating_enabled",
                "VERDICT_GATING_ENABLED", "verdict.gating.enabled"));
        // Empty by default: which single project the gate applies to while it is being trusted. Empty means
        // no project, not every project - a scoping value that falls back to "all" would turn the first
        // careless deploy into a factory-wide change, which is the opposite of what a staged rollout is for.
        definitions.put("verdict_gating_project_slug", plain("verdict_gating_project_slug",
                "VERDICT_GATING_PROJECT_SLUG", "verdict.gating.project-slug"));
        // 2026-08-14: same class of failure as design_system_falsification_enabled above, found the same
        // way - by being the first caller to actually exercise the path. JulesDispatchService.
        // systemOrchestratorRepositoryName() reads this key and carries a fallback for when it is unset,
        // but requireDefinition throws on an UNREGISTERED key before any fallback can apply. So every
        // ORCHESTRATOR_SYSTEM dispatch - the mechanism for the factory to task itself against its own
        // repository - failed with HTTP 400 the moment it was first used. The code path existed unused
        // since it was written, which is exactly why nobody hit it.
        definitions.put("system_orchestrator_repository_name", plain("system_orchestrator_repository_name",
                "SYSTEM_ORCHESTRATOR_REPOSITORY_NAME", "system.orchestrator.repository-name"));
        definitions.put("falsification_cycle_enabled", flag("falsification_cycle_enabled", "FALSIFICATION_CYCLE_ENABLED", "falsification-cycle.enabled"));
        // Independent of falsification_cycle_enabled above - the philosophical track (product-critique per
        // real philosopher, Kano-classified) is a separate generative track from the formal/corrective one
        // and must be toggleable on its own (operator directive, 2026-07-25).
        definitions.put("philosophical_falsification_enabled", flag("philosophical_falsification_enabled", "PHILOSOPHICAL_FALSIFICATION_ENABLED", "philosophical-falsification.enabled"));
        // Off by default (2026-08-09, Phase 1 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md):
        // the only feature in this codebase that spins up the active project's own delivered product via
        // the runtime-launcher sidecar. Deliberately observe_only in effect even once turned on - nothing
        // downstream yet reads client_runtime_observations to change any autonomous behavior (that starts
        // in Phase 2/3).
        definitions.put("client_runtime_observability_enabled", flag("client_runtime_observability_enabled", "CLIENT_RUNTIME_OBSERVABILITY_ENABLED", "client-runtime-observability.enabled"));
        // Off by default (2026-07-25, testimony-vs-evidence Phase 2): periodic sweep that can WRITE task
        // status (marks a task failed when its PR was closed without merge on GitHub with no active claim
        // left to complete it normally) - a real write action, unlike Phase 1's read-only evidence checks,
        // so it gets its own explicit opt-in even though the underlying logic is low-risk (CAS-guarded,
        // only ever touches already-orphaned tasks).
        definitions.put("github_truth_reconciliation_enabled", flag("github_truth_reconciliation_enabled", "GITHUB_TRUTH_RECONCILIATION_ENABLED", "github-truth-reconciliation.enabled"));
        // Off by default (2026-07-25, operator directive: "too few systemic decisions... embed an
        // auditor like you right inside the backend"). An embedded Gemini-powered ops auditor that reasons over
        // real evidence (not guesses) and autonomously calls a curated, pre-vetted tool set to fix bounded-
        // risk operational issues (orphaned wishlists behind a terminally-failed task, etc.) - the same
        // pattern the operator watched the human orchestrator (this session) perform by hand all night.
        definitions.put("ops_auditor_enabled", flag("ops_auditor_enabled", "OPS_AUDITOR_ENABLED", "ops-auditor.enabled"));
        // Off by default (2026-07-25, operator directive: "Gemini needs to keep learning the context
        // of my system and to be as competent as possible on every call"). Gates GeminiContextService's
        // scheduled re-indexing (embeds OBSERVER_LOG/engineering-charter/role-charter text via the ML
        // service - a real, metered Gemini API cost) AND whether callers augment their prompts with
        // retrieved context at all - off means byte-for-byte the pre-existing prompt behavior, no surprise
        // cost the operator didn't opt into.
        definitions.put("gemini_context_learning_enabled", flag("gemini_context_learning_enabled", "GEMINI_CONTEXT_LEARNING_ENABLED", "gemini-context.learning-enabled"));
        // Gates GeminiProjectObserverService's scheduled analysis cycle. Permanently disabled as Muda (2026-08-26).
        definitions.put("gemini_project_observer_enabled", flag("gemini_project_observer_enabled", "GEMINI_PROJECT_OBSERVER_ENABLED", "gemini-project-observer.enabled"));
        // On by default (2026-07-26 restoration, operator directive: "the project log must not depend on
        // deployments!! this is a huge omission") - baseline infrastructure, not an opt-in experiment. Seeded
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
