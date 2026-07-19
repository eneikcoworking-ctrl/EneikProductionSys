package com.eneik.production.services;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic, deny-list-based answer to "does this PR actually contain product code" - not an LLM
 * judgment call. A PR is classified as having no code only if every changed file matches one of these
 * categories; a deny-list rather than a language/extension allow-list because this EMS generates
 * projects on an arbitrary client-chosen stack (FastAPI, Svelte, whatever), and a fixed extension
 * allow-list would need updating for every new stack. A false negative here (treating a real process
 * file as code) is harmless; a false positive (treating real code as disposable process noise) would
 * delete a real branch - the deny-list makes that structurally hard to hit.
 */
@Service
public class CodeChangeClassifier {

    private static final Pattern GENERATED_ARTIFACT_PATH = Pattern.compile(
            "(^|/)(playwright-report|test-results|coverage|node_modules|\\.next)/");
    private static final Pattern GENERATED_ARTIFACT_EXTENSION = Pattern.compile(
            "\\.(trace|webm|zip)$");

    public boolean hasCode(List<String> changedFilePaths) {
        if (changedFilePaths == null || changedFilePaths.isEmpty()) {
            return false;
        }
        return changedFilePaths.stream().anyMatch(path -> path != null && !isNonCode(path));
    }

    private boolean isNonCode(String path) {
        String normalized = path.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        if (normalized.startsWith(".eneik/")) {
            return true;
        }
        if (normalized.startsWith("design/draft/") || normalized.startsWith("design/approved/")) {
            return true;
        }
        if (normalized.endsWith(".md") || normalized.equals("README") || normalized.endsWith("/README")) {
            return true;
        }
        if (GENERATED_ARTIFACT_PATH.matcher(normalized).find() || GENERATED_ARTIFACT_EXTENSION.matcher(normalized).find()) {
            return true;
        }
        return false;
    }
}
