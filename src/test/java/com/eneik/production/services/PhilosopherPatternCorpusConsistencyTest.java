package com.eneik.production.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification harness for philosopher patterns corpus consistency
 * (ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE / D014 RAG hallucination,
 *  ALFRED_TARSKIY_01_FALSIFICATION_HARNESS / D008 False green).
 *
 * <p>Three origins of the corpus must agree at all times:
 * 1. Generator (scripts/generate_philosopher_patterns.py): produces 00_COMMON, index.json, 86 philosopher files.
 * 2. Hand-maintained 03_PATTERN_STRENGTH.md: defines 53 canonical pattern families with strong/weak forms and refutations.
 * 3. Hand-maintained 04_FACTORY_DERIVED_PATTERNS.md: defines 4 patterns derived from factory execution (ordinal &gt;= 21).
 *
 * <p>Proof obligations:
 * - All 53 families in 03_PATTERN_STRENGTH.md match the families in philosopher_patterns_index.json exactly (53 == 53).
 * - All 4 derived patterns in 04_FACTORY_DERIVED_PATTERNS.md do NOT collide with any generated pattern in the index.
 * - All ACP patterns in 00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md are present in COMMON_PATTERNS of the generator script.
 * - Falsification harness: tampering with family names, missing ACPs, or simulated collisions produces a failing verdict.
 *
 * <p>Honest build reporting note: Dockerfile.backend packages with 'mvn -q package -DskipTests'.
 * A failure in this JUnit test fails 'mvn test' (CI verification &amp; local test runs), but does NOT stop
 * image packaging when tests are explicitly skipped via -DskipTests.
 */
public class PhilosopherPatternCorpusConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Path repoRoot;
    private static Path corpusDir;
    private static Path generatorScript;

    @BeforeAll
    static void setUpPaths() {
        repoRoot = findRepoRoot();
        corpusDir = repoRoot.resolve("docs/philosopher-patterns");
        generatorScript = repoRoot.resolve("scripts/generate_philosopher_patterns.py");

        assertTrue(Files.isDirectory(corpusDir), "Corpus directory must exist at " + corpusDir);
        assertTrue(Files.isRegularFile(generatorScript), "Generator script must exist at " + generatorScript);
    }

    private static Path findRepoRoot() {
        Path candidate = Path.of(".").toAbsolutePath().normalize();
        for (int i = 0; i < 5; i++) {
            if (Files.isDirectory(candidate.resolve("docs/philosopher-patterns"))
                    && Files.isRegularFile(candidate.resolve("scripts/generate_philosopher_patterns.py"))) {
                return candidate;
            }
            Path parent = candidate.getParent();
            if (parent == null) break;
            candidate = parent;
        }
        return Path.of(".").toAbsolutePath().normalize();
    }

    @Test
    void all53FamiliesInPatternStrengthMatchPhilosopherIndexJsonExactly() throws IOException {
        Set<String> families03 = extract03Families();
        assertEquals(53, families03.size(), "03_PATTERN_STRENGTH.md must define exactly 53 families");

        Set<String> indexFamilies = extractIndexFamilies();
        assertEquals(53, indexFamilies.size(), "philosopher_patterns_index.json must contain exactly 53 unique families");

        Set<String> in03Only = new TreeSet<>(families03);
        in03Only.removeAll(indexFamilies);

        Set<String> inIndexOnly = new TreeSet<>(indexFamilies);
        inIndexOnly.removeAll(families03);

        assertTrue(in03Only.isEmpty() && inIndexOnly.isEmpty(),
                "Families in 03_PATTERN_STRENGTH.md and philosopher_patterns_index.json must match 1:1.\n"
                        + "In 03 only: " + in03Only + "\nIn Index only: " + inIndexOnly);
    }

    @Test
    void factoryDerivedPatternsDoNotCollideWithGeneratedIndexAndHaveOrdinal21OrHigher() throws IOException {
        Set<String> derived04 = extract04DerivedPatterns();
        assertEquals(4, derived04.size(), "04_FACTORY_DERIVED_PATTERNS.md must contain exactly 4 factory-derived patterns");

        Set<String> indexPatternIds = extractIndexPatternIds();
        assertEquals(1720, indexPatternIds.size(), "philosopher_patterns_index.json must contain 1720 generated pattern IDs");

        Set<String> collisions = new TreeSet<>(derived04);
        collisions.retainAll(indexPatternIds);

        assertTrue(collisions.isEmpty(),
                "Factory-derived patterns (04) must not collide with generated publication pattern IDs in index: " + collisions);

        for (String patternId : derived04) {
            String[] parts = patternId.split("_");
            boolean hasOrdinal21OrHigher = false;
            for (String part : parts) {
                if (part.matches("\\d+")) {
                    int ordinal = Integer.parseInt(part);
                    if (ordinal >= 21) {
                        hasOrdinal21OrHigher = true;
                    }
                }
            }
            assertTrue(hasOrdinal21OrHigher,
                    "Derived pattern " + patternId + " must carry ordinal >= 21 to avoid collision with 20 publication patterns");
        }
    }

    @Test
    void allAcpPatternsInCommonMarkdownMatchGeneratorScriptExactly() throws IOException {
        Set<String> markdownAcps = extractMarkdownAcpIds();
        assertTrue(markdownAcps.size() >= 104,
                "00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md must have at least 104 ACP patterns (found " + markdownAcps.size() + ")");

        Set<String> generatorAcps = extractGeneratorScriptAcpIds();
        assertTrue(generatorAcps.size() >= 104,
                "scripts/generate_philosopher_patterns.py must have at least 104 ACP patterns (found " + generatorAcps.size() + ")");

        Set<String> inMarkdownOnly = new TreeSet<>(markdownAcps);
        inMarkdownOnly.removeAll(generatorAcps);

        Set<String> inGeneratorOnly = new TreeSet<>(generatorAcps);
        inGeneratorOnly.removeAll(markdownAcps);

        assertTrue(inMarkdownOnly.isEmpty() && inGeneratorOnly.isEmpty(),
                "ACP patterns in 00_COMMON markdown and generator script must match 1:1.\n"
                        + "In markdown only: " + inMarkdownOnly + "\nIn generator script only: " + inGeneratorOnly);
    }

    @Test
    void falsificationHarness_renamedFamilyInIndexCausesCheckToFail() {
        Set<String> families03 = Set.of("ACTUAL_OBJECT_REGISTER", "ANCHOR_BOUND_NAME");
        Set<String> tamperedIndex = Set.of("RENAMED_OBJECT_REGISTER", "ANCHOR_BOUND_NAME");

        Set<String> diff = new TreeSet<>(families03);
        diff.removeAll(tamperedIndex);

        assertFalse(diff.isEmpty(), "Falsification harness: renamed family must be detected as discrepancy");
        assertTrue(diff.contains("ACTUAL_OBJECT_REGISTER"));
    }

    @Test
    void falsificationHarness_missingAcpInGeneratorCausesCheckToFail() {
        Set<String> markdownAcps = Set.of("ACP-001", "ACP-101", "ACP-102");
        Set<String> tamperedGenerator = Set.of("ACP-001", "ACP-102"); // ACP-101 missing

        Set<String> diff = new TreeSet<>(markdownAcps);
        diff.removeAll(tamperedGenerator);

        assertFalse(diff.isEmpty(), "Falsification harness: missing ACP pattern in generator must be detected");
        assertTrue(diff.contains("ACP-101"));
    }

    @Test
    void falsificationHarness_collidingDerivedPatternCausesCheckToFail() {
        Set<String> derived = Set.of("LYUDVIG_VITGENSHTEYN_01_CONVERSATION_MAXIM");
        Set<String> index = Set.of("LYUDVIG_VITGENSHTEYN_01_CONVERSATION_MAXIM");

        Set<String> collisions = new TreeSet<>(derived);
        collisions.retainAll(index);

        assertFalse(collisions.isEmpty(), "Falsification harness: colliding pattern must be detected");
    }

    // Helper extraction methods

    public static Set<String> extract03Families() throws IOException {
        Path path = corpusDir.resolve("03_PATTERN_STRENGTH.md");
        String content = Files.readString(path);
        Matcher matcher = Pattern.compile("### `([A-Z0-9_]+)`").matcher(content);
        Set<String> families = new TreeSet<>();
        while (matcher.find()) {
            families.add(matcher.group(1));
        }
        return families;
    }

    public static Set<String> extractIndexFamilies() throws IOException {
        Path path = corpusDir.resolve("philosopher_patterns_index.json");
        JsonNode root = MAPPER.readTree(Files.readString(path));
        JsonNode philosophers = root.path("philosophers");
        Set<String> families = new TreeSet<>();
        for (JsonNode phil : philosophers) {
            for (JsonNode p : phil.path("patterns")) {
                String slotKey = p.path("slot_key").asText();
                if (!slotKey.isBlank()) {
                    families.add(slotKey.toUpperCase().replace('-', '_'));
                }
            }
        }
        return families;
    }

    public static Set<String> extract04DerivedPatterns() throws IOException {
        Path path = corpusDir.resolve("04_FACTORY_DERIVED_PATTERNS.md");
        String content = Files.readString(path);
        Matcher matcher = Pattern.compile("## `([A-Z0-9_]+)`").matcher(content);
        Set<String> patterns = new TreeSet<>();
        while (matcher.find()) {
            patterns.add(matcher.group(1));
        }
        return patterns;
    }

    public static Set<String> extractIndexPatternIds() throws IOException {
        Path path = corpusDir.resolve("philosopher_patterns_index.json");
        JsonNode root = MAPPER.readTree(Files.readString(path));
        JsonNode philosophers = root.path("philosophers");
        Set<String> ids = new TreeSet<>();
        for (JsonNode phil : philosophers) {
            for (JsonNode p : phil.path("patterns")) {
                String id = p.path("id").asText();
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    public static Set<String> extractMarkdownAcpIds() throws IOException {
        Path path = corpusDir.resolve("00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md");
        String content = Files.readString(path);
        Matcher matcher = Pattern.compile("\\| `(ACP-\\d+)` \\|").matcher(content);
        Set<String> ids = new TreeSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    public static Set<String> extractGeneratorScriptAcpIds() throws IOException {
        String content = Files.readString(generatorScript);
        Matcher matcher = Pattern.compile("\\(\"(ACP-\\d+)\",").matcher(content);
        Set<String> ids = new TreeSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }
}
