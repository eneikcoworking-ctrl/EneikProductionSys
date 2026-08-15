package com.eneik.production.services.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                .as("a hypothesis must never reach a decision - it exists to be verified or refuted. "
                        + "'derived' is admitted because reasoned structural knowledge is not a guess, but "
                        + "it pays for that with its own rules: it must state why, and may carry no number")
                .allSatisfy(e -> assertThat(e.status())
                        .isIn("statutory", "standard", "observed", "derived"));
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

    /**
     * Consumer-sales duties must never be silently imposed on an internal tool. This service cannot judge
     * that - it has not read the brief - so it must carry the scope outward rather than drop it. A dropped
     * scope is invisible: the requirement still renders, just without the condition that limits it.
     */
    @Test
    void carriesApplicabilityScopeInsteadOfDroppingIt() {
        List<MarketCorpusService.Expectation> german = service.influentialExpectations("DE");

        assertThat(german)
                .as("every entry must state which product kinds it covers, even if that is all of them")
                .allSatisfy(e -> assertThat(e.appliesToProfiles()).isNotEmpty());

        assertThat(german)
                .filteredOn(e -> e.requirement().toLowerCase().contains("withdrawal"))
                .as("the 14-day withdrawal duty is a consumer-sales rule and must arrive scoped to selling "
                        + "products, not as a blanket requirement an internal tool would inherit")
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.appliesToProfiles()).doesNotContain("*"));
    }

    /**
     * What people expect moves within a year or two now, not a decade. A dated observation that keeps
     * steering decisions with undiminished force is a corpus pretending it still knows something.
     */
    @Test
    void marketObservationsStopInfluencingOnceTheyExpire(@TempDir java.nio.file.Path dir) throws Exception {
        writeCorpus(dir, """
                {"capabilities":[{"id":"c","appliesWhen":"always","expectations":[
                  {"requirement":"a share measured long ago","kano":"performance","appliesToProfiles":["*"],
                   "status":"observed","source":"survey","validUntil":"2020-01-01"},
                  {"requirement":"a share measured recently","kano":"performance","appliesToProfiles":["*"],
                   "status":"observed","source":"survey","validUntil":"2999-01-01"}]}]}
                """);
        MarketCorpusService service = new MarketCorpusService(dir.toString());

        assertThat(service.influentialExpectations("DE"))
                .extracting(MarketCorpusService.Expectation::requirement)
                .as("stale evidence must stop steering decisions until someone re-measures it")
                .containsExactly("a share measured recently");
    }

    @Test
    void aLawDoesNotLapseBecauseNobodyRevisitedTheFile(@TempDir java.nio.file.Path dir) throws Exception {
        writeCorpus(dir, """
                {"capabilities":[{"id":"c","appliesWhen":"always","expectations":[
                  {"requirement":"a statutory duty","kano":"must-be","appliesToProfiles":["*"],
                   "status":"statutory","source":"an act","validUntil":"2020-01-01"}]}]}
                """);
        MarketCorpusService service = new MarketCorpusService(dir.toString());

        assertThat(service.influentialExpectations("DE"))
                .as("a shelf life applies to market observations only - a legal duty ends when it is "
                        + "repealed, which is an edit here, never a timeout")
                .hasSize(1);
    }

    @Test
    void treatsAnUnreadableShelfLifeAsExpiredRatherThanImmortal(@TempDir java.nio.file.Path dir) throws Exception {
        writeCorpus(dir, """
                {"capabilities":[{"id":"c","appliesWhen":"always","expectations":[
                  {"requirement":"a share with a broken date","kano":"performance","appliesToProfiles":["*"],
                   "status":"observed","source":"survey","validUntil":"soon"}]}]}
                """);
        MarketCorpusService service = new MarketCorpusService(dir.toString());

        assertThat(service.influentialExpectations("DE"))
                .as("the safe reading of a malformed shelf life is that we do not know it still holds")
                .isEmpty();
    }

    @Test
    void anObservationWithNoStatedShelfLifeStillCounts(@TempDir java.nio.file.Path dir) throws Exception {
        writeCorpus(dir, """
                {"capabilities":[{"id":"c","appliesWhen":"always","expectations":[
                  {"requirement":"an undated share","kano":"performance","appliesToProfiles":["*"],
                   "status":"observed","source":"survey"}]}]}
                """);
        MarketCorpusService service = new MarketCorpusService(dir.toString());

        assertThat(service.influentialExpectations("DE"))
                .as("expiry is opt-in: silence means no shelf life was claimed, not that it lapsed")
                .hasSize(1);
    }

    private void writeCorpus(java.nio.file.Path dir, String json) throws Exception {
        java.nio.file.Files.writeString(dir.resolve("capabilities.json"), json);
    }

    /**
     * The corpus exists because clients do not know what software of their class must contain and this
     * factory does. A corpus whose every entry is inert can only repeat legislation, which was the defect
     * this status was added to fix.
     */
    @Test
    void reasonedExpertiseActuallyReachesDecisions() {
        List<MarketCorpusService.Expectation> all = service.influentialExpectations("DE");

        assertThat(all)
                .filteredOn(e -> "derived".equals(e.status()))
                .as("structural knowledge about what a product needs must be allowed to influence, or the "
                        + "corpus cannot do the one job it was built for")
                .isNotEmpty();
    }

    @Test
    void derivedEntriesNeverSmuggleInANumber(@TempDir java.nio.file.Path dir) throws Exception {
        writeCorpus(dir, """
                {"capabilities":[{"id":"c","appliesWhen":"always","expectations":[
                  {"requirement":"a reasoned structural need","kano":"must-be","appliesToProfiles":["*"],
                   "status":"derived","source":"reasoning","reasoning":"because the object must pass through it"}]}]}
                """);
        MarketCorpusService svc = new MarketCorpusService(dir.toString());

        assertThat(svc.influentialExpectations("DE"))
                .as("a derived entry states what is needed and why; shares and effect sizes stay empirical")
                .allSatisfy(e -> assertThat(e.requirement() + " " + e.source())
                        .doesNotContainPattern("\\d+\\s?%"));
    }

    @Test
    void realCorpusKeepsNumbersOutOfReasonedEntries() {
        List<MarketCorpusService.Expectation> derived = service.influentialExpectations("DE").stream()
                .filter(e -> "derived".equals(e.status()))
                .toList();

        assertThat(derived)
                .as("the shipped corpus must obey its own rule: reasoning may not carry a percentage, "
                        + "because the moment a claim needs a number it needs measurement instead")
                .allSatisfy(e -> assertThat(e.requirement()).doesNotContainPattern("\\d+\\s?%"));
    }

    /**
     * Closes F16, and pins the reason rather than the wording.
     *
     * Every link in these chains up to the last one describes work somebody is DOING. Staleness is the one
     * state a document reaches by nobody doing anything, so a chain assembled from actions cannot represent
     * it - which is exactly how it went missing from three profiles at once rather than one. The operator
     * found it by asking why a knowledge base had no reports.
     */
    @Test
    void collectionProfilesSayHowStalenessBecomesKnown() {
        for (String profileId : List.of("content-management", "document-flow", "learning")) {
            assertThat(linksOf(profileId))
                    .as("%s holds content that decays while nothing happens to it; 'update or unpublish' "
                            + "is an action nobody performs until something tells them the text no longer "
                            + "holds", profileId)
                    .anySatisfy(link -> assertThat(link).containsAnyOf("stale", "superseded"));
        }
    }

    @Test
    void collectionProfilesLetSomebodyAskWhatTheCollectionHolds() {
        for (String profileId : List.of("content-management", "document-flow")) {
            assertThat(linksOf(profileId))
                    .as("a separate path, because it breaks independently: the editorial workflow can be "
                            + "perfect while nobody can answer what exists. GQM telemetry does not "
                            + "substitute - it measures whether the SYSTEM works, not what the COLLECTION "
                            + "holds (%s)", profileId)
                    .anySatisfy(link -> assertThat(link).contains("what the collection holds"));
        }
    }

    private List<String> linksOf(String profileId) {
        List<String> links = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode profile : service.profiles()) {
            if (!profileId.equals(profile.path("id").asText(""))) {
                continue;
            }
            for (com.fasterxml.jackson.databind.JsonNode path : profile.path("valuePaths")) {
                for (com.fasterxml.jackson.databind.JsonNode link : path.path("path")) {
                    links.add(link.asText(""));
                }
            }
        }
        assertThat(links).as("profile %s must exist in the shipped corpus", profileId).isNotEmpty();
        return links;
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
