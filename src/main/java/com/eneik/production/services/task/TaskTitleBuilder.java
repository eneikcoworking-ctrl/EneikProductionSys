package com.eneik.production.services.task;

import com.eneik.production.models.persistence.TaskEntity;

import java.util.Locale;
import java.util.Map;

public final class TaskTitleBuilder {
    private static final Map<String, String> ROLE_DEFAULTS = Map.ofEntries(
            Map.entry("BARCAN-TAG-00", "Merge Readiness"),
            Map.entry("BARCAN-TAG-01", "Service Boundary"),
            Map.entry("BARCAN-TAG-02", "API Slice"),
            Map.entry("BARCAN-TAG-03", "Design Brief"),
            Map.entry("BARCAN-TAG-04", "AI Context"),
            Map.entry("BARCAN-TAG-05", "Build Pipeline"),
            Map.entry("BARCAN-TAG-06", "Test Coverage"),
            Map.entry("BARCAN-TAG-07", "Access Guard"),
            Map.entry("BARCAN-TAG-08", "Data Schema"),
            Map.entry("BARCAN-TAG-09", "Delivery Plan"),
            Map.entry("BARCAN-TAG-10", "Compliance Check"),
            Map.entry("BARCAN-TAG-11", "UI Slice"),
            Map.entry("BARCAN-TAG-12", "API Contract")
    );

    private TaskTitleBuilder() {
    }

    public static String displayTitle(TaskEntity task) {
        if (task == null) {
            return "Task Slice";
        }
        if (notBlank(task.getTitle())) {
            return enforceTwoOrThreeWords(task.getTitle());
        }
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "";
        String payloadSource = "";
        if (task.getPayload() != null) {
            payloadSource = task.getPayload().path("short_title").asText("") + " "
                    + task.getPayload().path("slice_title").asText("") + " "
                    + task.getPayload().path("role_atomic_goal").asText("");
        }
        return build(roleTag, payloadSource + " " + task.getDescription());
    }

    public static String build(String roleTag, String source) {
        String lower = source == null ? "" : source.toLowerCase(Locale.ROOT);
        String special = specialTitle(lower);
        if (notBlank(special)) {
            return special;
        }
        String roleDefault = ROLE_DEFAULTS.get(roleTag);
        if (notBlank(roleDefault)) {
            return roleDefault;
        }
        String matched = keywordTitle(lower);
        return notBlank(matched) ? matched : "Task Slice";
    }

    public static String enforceTwoOrThreeWords(String title) {
        if (!notBlank(title)) {
            return "Task Slice";
        }
        String[] words = title.replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split(" ");
        if (words.length == 0 || words[0].isBlank()) {
            return "Task Slice";
        }
        int max = Math.min(3, words.length);
        if (max >= 2) {
            return titleCase(String.join(" ", java.util.Arrays.copyOfRange(words, 0, max)));
        }
        return titleCase(words[0] + " Slice");
    }

    private static String specialTitle(String lower) {
        if (lower.contains("merge conflict") || lower.contains("rebase")) {
            return "Conflict Resolve";
        }
        if (lower.contains("bootstrap") || lower.contains("runtime contract") || lower.contains("project scaffold")
                || lower.contains("repository execution")) {
            return "Runtime Contract";
        }
        return "";
    }

    private static String keywordTitle(String lower) {
        if (lower.contains("architecture") || lower.contains("service boundary") || lower.contains("adr")) {
            return "Service Boundary";
        }
        if (lower.contains("e2e") || lower.contains("test") || lower.contains("qa") || lower.contains("verify")
                || lower.contains("coverage")) {
            return "Test Coverage";
        }
        if (lower.contains("auth") || lower.contains("security") || lower.contains("credential")
                || lower.contains("permission") || lower.contains("access-control")) {
            return "Access Guard";
        }
        if (lower.contains("database") || lower.contains("schema") || lower.contains("migration")
                || lower.contains("storage") || lower.contains("retention")) {
            return "Data Schema";
        }
        if (lower.contains("ai") || lower.contains("model") || lower.contains("prompt") || lower.contains("rag")
                || lower.contains("context")) {
            return "AI Context";
        }
        if (lower.contains("tax") || lower.contains("legal") || lower.contains("compliance")
                || lower.contains("regulatory") || lower.contains("policy")) {
            return "Compliance Check";
        }
        if (lower.contains("docker") || lower.contains("deploy") || lower.contains("ci")
                || lower.contains("build") || lower.contains("pipeline")) {
            return "Build Pipeline";
        }
        if (lower.contains("design") || lower.contains("mockup") || lower.contains("wireframe")
                || lower.contains("ux")) {
            return "Design Brief";
        }
        if (lower.contains("frontend") || lower.contains("svelte") || lower.contains("browser")
                || lower.contains("ui") || lower.contains("form") || lower.contains("page")) {
            return "UI Slice";
        }
        if (lower.contains("api contract") || lower.contains("openapi") || lower.contains("swagger")
                || lower.contains("endpoint spec")) {
            return "API Contract";
        }
        if (lower.contains("api") || lower.contains("backend") || lower.contains("endpoint")
                || lower.contains("controller")) {
            return "API Slice";
        }
        if (lower.contains("delivery") || lower.contains("sequence") || lower.contains("priority")
                || lower.contains("handoff")) {
            return "Delivery Plan";
        }
        if (lower.contains("artifact") || lower.contains("repository hygiene") || lower.contains("pr diff")) {
            return "Repo Hygiene";
        }
        return "";
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(formatWord(word));
        }
        return result.isEmpty() ? "Task Slice" : result.toString();
    }

    private static String formatWord(String word) {
        return switch (word) {
            case "ai", "api", "ui", "ux", "pr", "qa", "ci", "cd", "db", "ml" -> word.toUpperCase(Locale.ROOT);
            default -> Character.toUpperCase(word.charAt(0)) + word.substring(1);
        };
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
