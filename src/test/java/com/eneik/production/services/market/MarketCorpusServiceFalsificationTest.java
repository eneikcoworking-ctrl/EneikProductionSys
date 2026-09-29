package com.eneik.production.services.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification tests for MarketCorpusService anchored in Barcan philosopher patterns:
 * 1. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010 Data lineage loss, Goldman):
 *    Trust data only when its acquisition process is reliable; influential expectations require
 *    explicit source, verified status (statutory, standard, observed, derived), and valid freshness rule.
 * 2. LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY (D013 Runtime drift, Wittgenstein):
 *    Prefer empirical operational truth over speculative narrative; strict word boundary regex prevents
 *    false positives (workshop != shop), mtime caching tracks physical filesystem changes, and missing/corrupt
 *    corpus degrades safely without crashing downstream flow.
 */
class MarketCorpusServiceFalsificationTest {

    @Test
    @DisplayName("Goldman D010: Hypothesis and unverified entries are strictly filtered out of influential expectations")
    void falsifyGoldmanReliabilityChain_unverifiedAndHypothesisEntriesNeverInfluence(@TempDir Path dir) throws Exception {
        String json = """
                {"capabilities":[{"id":"cap-auth","appliesWhen":"always","expectations":[
                  {"requirement":"Unverified assumption","kano":"delighter","status":"hypothesis","source":"AI model hallucination"},
                  {"requirement":"Draft concept","kano":"must-be","status":"draft","source":"unreviewed discussion"},
                  {"requirement":"Statutory requirement","kano":"must-be","status":"statutory","source":"GDPR Art. 17"}
                ]}]}
                """;
        Files.writeString(dir.resolve("capabilities.json"), json);
        MarketCorpusService service = new MarketCorpusService(dir.toString());

        List<MarketCorpusService.Expectation> influential = service.influentialExpectations(null);

        assertThat(influential)
                .extracting(MarketCorpusService.Expectation::requirement)
                .containsExactly("Statutory requirement");
    }

    @Test
    @DisplayName("Goldman D010: Every influential entry in the real shipped corpus possesses a non-blank source")
    void falsifyGoldmanReliabilityChain_everyShippedEntryCitesSource() {
        MarketCorpusService service = new MarketCorpusService("market-corpus");
        assertThat(service.isAvailable()).isTrue();

        List<MarketCorpusService.Expectation> expectations = service.influentialExpectations("DE");
        assertThat(expectations).isNotEmpty();
        assertThat(expectations).allSatisfy(e -> {
            assertThat(e.source()).as("Expectation '%s' must cite a valid source", e.requirement()).isNotBlank();
            assertThat(e.status()).isIn("statutory", "standard", "observed", "derived");
        });
    }

    @Test
    @DisplayName("Goldman D010: Stale market observations expire, and malformed dates expire rather than gain immortality")
    void falsifyGoldmanReliabilityChain_staleObservationsExpire(@TempDir Path dir) throws Exception {
        String json = """
                {"capabilities":[{"id":"cap-retention","appliesWhen":"always","expectations":[
                  {"requirement":"Old observation","kano":"performance","status":"observed","source":"survey-2018","validUntil":"2019-12-31"},
                  {"requirement":"Malformed date observation","kano":"performance","status":"observed","source":"survey-2022","validUntil":"invalid-date"},
                  {"requirement":"Fresh observation","kano":"performance","status":"observed","source":"survey-current","validUntil":"2099-01-01"},
                  {"requirement":"Statutory law with old date","kano":"must-be","status":"statutory","source":"BGB","validUntil":"2019-12-31"}
                ]}]}
                """;
        Files.writeString(dir.resolve("capabilities.json"), json);
        MarketCorpusService service = new MarketCorpusService(dir.toString());

        List<MarketCorpusService.Expectation> influential = service.influentialExpectations(null);

        assertThat(influential)
                .extracting(MarketCorpusService.Expectation::requirement)
                .as("Only fresh observation and statutory law (laws do not expire by timeout) must influence")
                .containsExactlyInAnyOrder("Fresh observation", "Statutory law with old date");
    }

    @Test
    @DisplayName("Wittgenstein D013: Word-boundary mentions() rejects substring collisions (anti-mirror)")
    void falsifyWittgensteinAntiMirror_wordBoundaryPreventsCollisions() {
        // Substring false positives must be rejected
        assertThat(MarketCorpusService.mentions("We organize workshops for enterprise teams", "shop")).isFalse();
        assertThat(MarketCorpusService.mentions("The library focuses on ancient cartography", "cart")).isFalse();
        assertThat(MarketCorpusService.mentions("The author wrote three novels", "auth")).isFalse();

        // Legitimate morphological inflections must pass
        assertThat(MarketCorpusService.mentions("Users shopping online expect quick checkout", "shop")).isTrue();
        assertThat(MarketCorpusService.mentions("Adding items to shopping carts", "cart")).isTrue();
        assertThat(MarketCorpusService.mentions("A multi-tenant system with robust auth", "auth")).isTrue();
    }

    @Test
    @DisplayName("Wittgenstein D013: Mtime cache tracks physical filesystem changes (anti-mirror telemetry)")
    void falsifyWittgensteinAntiMirror_mtimeCacheReloadsOnChange(@TempDir Path dir) throws Exception {
        Path capPath = dir.resolve("capabilities.json");
        String v1 = """
                {"capabilities":[{"id":"c1","appliesWhen":"always","expectations":[
                  {"requirement":"Version 1 requirement","kano":"must-be","status":"statutory","source":"act-1"}
                ]}]}
                """;
        Files.writeString(capPath, v1);
        MarketCorpusService service = new MarketCorpusService(dir.toString());

        List<MarketCorpusService.Expectation> firstRead = service.influentialExpectations(null);
        assertThat(firstRead).extracting(MarketCorpusService.Expectation::requirement).containsExactly("Version 1 requirement");

        // Update file on disk and advance modification timestamp
        String v2 = """
                {"capabilities":[{"id":"c1","appliesWhen":"always","expectations":[
                  {"requirement":"Version 2 updated requirement","kano":"must-be","status":"statutory","source":"act-2"}
                ]}]}
                """;
        Files.writeString(capPath, v2);
        Files.setLastModifiedTime(capPath, FileTime.from(Instant.now().plusSeconds(5)));

        List<MarketCorpusService.Expectation> secondRead = service.influentialExpectations(null);
        assertThat(secondRead).extracting(MarketCorpusService.Expectation::requirement).containsExactly("Version 2 updated requirement");
    }

    @Test
    @DisplayName("Wittgenstein D013: Non-existent or malformed corpus degrades safely without throwing")
    void falsifyWittgensteinAntiMirror_degradesGracefullyOnCorruptOrMissingCorpus(@TempDir Path dir) throws Exception {
        // Missing corpus
        MarketCorpusService missingService = new MarketCorpusService(dir.resolve("non-existent-subfolder").toString());
        assertThat(missingService.isAvailable()).isFalse();
        assertThat(missingService.influentialExpectations(null)).isEmpty();
        assertThat(missingService.detectionKeywords("cap1")).isEmpty();

        // Corrupted JSON
        Path corruptPath = dir.resolve("capabilities.json");
        Files.writeString(corruptPath, "{ broken json content [ unclosed");
        MarketCorpusService corruptService = new MarketCorpusService(dir.toString());
        assertThat(corruptService.influentialExpectations(null)).isEmpty();
    }
}
