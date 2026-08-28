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

    /**
     * The factory's own metalanguage, L_factory - the submission harness a Jules agent writes into the
     * CLIENT repository to drive its own session. Never product code, no matter what it contains.
     *
     * <p>Added 2026-08-27 (Phase 2 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md) after four "Architectural
     * contradiction" blocker PRs (#304, #306, #307, #308 on eneikdru/test-fiftieth) were auto-merged into
     * main. Each contained exactly one file: a Jules runner script. The classifier below was asked "is
     * there code here", saw a .sh extension it had no rule for, and answered yes - so an agent's REFUSAL
     * to do the work was recorded as delivered work, and 18 of these scripts accumulated in the client's
     * main branch.
     *
     * <p>Deliberately literal, not a blanket ban on shell scripts: a client project may legitimately own
     * scripts/backup.sh or scripts/migrate.sh, and treating those as contamination would reject real
     * product code - the expensive direction of this error. Only the runner shapes the factory itself
     * emits are listed, plus the harness HTML a verification agent leaves behind.
     */
    private static final Pattern FACTORY_ARTIFACT = Pattern.compile(
            "(^|/)(_temp_submit[^/]*\\.sh"
                    + "|final_submit[^/]*\\.sh"
                    + "|submit_[^/]*\\.sh"
                    + "|prep\\.sh"
                    + "|[^/]*harness\\.html)$");

    public boolean hasCode(List<String> changedFilePaths) {
        if (changedFilePaths == null || changedFilePaths.isEmpty()) {
            return false;
        }
        return changedFilePaths.stream().anyMatch(path -> path != null && isProductCodeFile(path));
    }

    /**
     * The predicate the merge gate actually needs: not "is this a file" but "is this a file of the
     * PRODUCT". A path is product code when it is neither disposable process noise (the pre-existing
     * deny-list) nor the factory's own metalanguage.
     *
     * <p>The action plan specified an allow-list here (src/**, frontend/src/**, pom.xml, ...). Implemented
     * as an extension of the deny-list instead, on purpose: this EMS generates projects on a client-chosen
     * stack, so an allow-list would need a new entry for every stack it ever meets and would silently
     * report "no product code" for the ones it hasn't met - which, in this same gate, now means REJECTING
     * a real PR. The deny-list keeps that failure direction closed while still enforcing the invariant the
     * plan is actually after, Code(t) INTERSECT L_factory = EMPTY.
     */
    public boolean isProductCodeFile(String path) {
        return path != null && !isNonCode(path) && !isFactoryArtifact(path);
    }

    /** True if this path belongs to the factory's metalanguage rather than to the client's product. */
    public boolean isFactoryArtifact(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.trim();
        return !normalized.isEmpty() && FACTORY_ARTIFACT.matcher(normalized).find();
    }

    /**
     * The factory's transient record files inside the client repository. Distinct from
     * {@link #isFactoryArtifact}: these are legitimately produced by the pipeline (task plans, review
     * verdicts) and a record PR exists precisely to carry them - so their presence is not grounds to
     * reject a PR. They are stripped from a PRODUCT branch before it merges instead.
     */
    public boolean isFactoryRecordFile(String path) {
        return path != null && path.trim().startsWith(".eneik/");
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
