package com.eneik.production.services.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The corpus decides what gets built, so the rule that keeps it from becoming folklore - only
 * statutory/standard/observed entries may influence anything - has to be enforced by code, not by
 * everyone remembering it. These tests read the REAL corpus file shipped in the repository, not a
 * fixture: a corpus that silently stopped loading, or that started leaking unverified entries into
 * decisions, would otherwise look exactly like a corpus that works.
 */
class MarketCorpusServiceTest {

    private final MarketCorpusService service = new MarketCorpusService("market-corpus");

    @Test
    void readsTheRealShippedCorpus() {
        assertThat(service.isAvailable())
                .as("the corpus file shipped in market-corpus/ must actually load")
                .isTrue();
        assertThat(service.influentialExpectations("DE")).isNotEmpty();
    }

    @Test
    void neverLetsUnverifiedEntriesInfluenceAnything() {
        List<MarketCorpusService.Expectation> all = service.influentialExpectations("DE");
        all.addAll(service.influentialExpectations("US"));

        assertThat(all)
                .as("hypothesis entries - including anything an AI wrote from general knowledge - must "
                        + "never reach a decision; they exist to be verified or refuted")
                .allSatisfy(e -> assertThat(e.status()).isIn("statutory", "standard", "observed"));
    }

    @Test
    void everyInfluentialEntryCitesItsSource() {
        assertThat(service.influentialExpectations("DE"))
                .as("an entry allowed to change what gets built must say where it came from, or it is "
                        + "indistinguishable from an opinion")
                .allSatisfy(e -> assertThat(e.source()).isNotBlank());
    }

    @Test
    void doesNotLeakOneMarketsRulesIntoTheOther() {
        assertThat(service.influentialExpectations("US"))
                .as("German-only obligations (Impressum, withdrawal instructions) must not be imposed on "
                        + "a US-only product")
                .noneSatisfy(e -> assertThat(e.market()).isEqualTo("DE"));

        assertThat(service.influentialExpectations("DE"))
                .noneSatisfy(e -> assertThat(e.market()).isEqualTo("US"));
    }

    @Test
    void carriesTheGermanObligationsThatDriveAbmahnungRisk() {
        List<String> german = service.influentialExpectations("DE").stream()
                .map(MarketCorpusService.Expectation::requirement)
                .map(String::toLowerCase)
                .toList();

        assertThat(german)
                .as("a missing Impressum is a standard paid cease-and-desist target in Germany, so it "
                        + "cannot depend on a client brief mentioning it")
                .anySatisfy(r -> assertThat(r).contains("impressum"));
    }

    @Test
    void missingCorpusDegradesInsteadOfFailing() {
        MarketCorpusService absent = new MarketCorpusService("market-corpus-does-not-exist");

        assertThat(absent.isAvailable()).isFalse();
        assertThat(absent.influentialExpectations("DE"))
                .as("an absent corpus must leave decomposition working exactly as it did before the "
                        + "corpus existed, never break it")
                .isEmpty();
    }
}
