package com.eneik.production.services.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate reads the real corpus shipped in the repository, for the same reason the corpus tests do: a
 * check that silently stopped seeing requirements would look identical to one that finds nothing wrong.
 */
class MarketComplianceGateTest {

    private final MarketCorpusService corpus = new MarketCorpusService("market-corpus");
    private final MarketComplianceGate gate = new MarketComplianceGate(corpus);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reportsStatutoryDutiesAPlanShowsNoSignOfCovering() {
        String plan = "Build a product catalogue with search and a shopping basket";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(plan, List.of("DE", "US"));

        assertThat(findings)
                .as("a plan that mentions only catalogue and basket covers none of the legal duties")
                .isNotEmpty();
        assertThat(findings)
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .contains("de-site-disclosures", "accessibility");
    }

    @Test
    void everyFindingCarriesTheActItComesFrom() {
        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements("A basket and a checkout", List.of("DE"));

        assertThat(findings)
                .as("a finding a human cannot verify is an accusation, not evidence")
                .allSatisfy(f -> assertThat(f.source()).isNotBlank());
    }

    @Test
    void staysSilentWhenThePlanDoesAddressTheDuties() {
        String plan = "Epic: legal pages - Impressum, withdrawal rights, terms. Prices shown inkl. MwSt. "
                + "Epic: consent management for cookies and tracking with opt-in. "
                + "Epic: GDPR data subject export and erasure. "
                + "Epic: nightly backup with verified restore. "
                + "Accessibility: keyboard operability and 4.5:1 contrast on every screen, WCAG 2.1 AA. "
                + "Checkout with strong customer authentication. Sales tax by destination, CCPA opt-out. "
                + "Epic: order review - an explicit confirmation step before any charge is taken.";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(plan, List.of("DE", "US"));

        assertThat(findings)
                .as("a plan that visibly addresses each duty must not be flagged - false alarms are how a "
                        + "check gets ignored")
                .isEmpty();
    }

    @Test
    void neverReportsAnythingUnverified() {
        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements("nothing at all", List.of("DE", "US"));

        assertThat(findings)
                .as("only statutory duties may be acted on; hypothesis entries such as German payment "
                        + "habits or account recovery must never surface as a compliance gap")
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .doesNotContain("account-recovery");
    }

    @Test
    void doesNotReportGameDutiesAgainstAShop() {
        String plan = "Product catalogue with search, a basket and a checkout with card payment";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(plan, List.of("DE", "US"));

        assertThat(findings)
                .as("a shop is not a game - reporting age ratings and loot box odds here is the kind of "
                        + "obvious nonsense that gets the whole check ignored")
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .doesNotContain("youth-protection");
    }

    @Test
    void reportsGameDutiesAgainstAGame() {
        String plan = "Epic: gameplay loop - the player completes a level and earns rewards. "
                + "Epic: multiplayer lobby with a leaderboard.";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(plan, List.of("DE", "US"));

        assertThat(findings)
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .as("multiplayer and chat are precisely the online functions §24a JuSchG attaches "
                        + "precautions to, and this plan says nothing about any of them")
                .contains("youth-protection")
                .as("but it sells nothing, so it owes nothing about the honesty of purchases - reporting "
                        + "that here would be the false alarm that gets the whole check ignored")
                .doesNotContain("purchase-transparency");
    }

    @Test
    void doesNotExemptAPlanItCannotClassify() {
        // Absence of evidence about the kind of product is not evidence that no duty applies. Note the
        // deliberate limit: this rescues duties scoped by PROFILE, not duties gated on a CONDITION. A plan
        // this empty describes no purchases and no publishing, so it genuinely owes nothing about either -
        // what it must still owe is everything that applies to any product with an interface at all.
        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements("build the thing we discussed", List.of("DE"));

        assertThat(findings)
                .as("an unclassifiable plan must not be quietly exempted from the duties every product "
                        + "carries")
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .contains("accessibility", "data-subject-rights", "de-site-disclosures");
    }

    /**
     * The real case this was built for: children writing a game in a lesson. It sells nothing, collects
     * nothing and is not published, so it owes none of the commercial duties - and being told to disclose
     * loot-box odds it does not have is how a person learns to ignore the whole report.
     */
    @Test
    void aGameThatSellsNothingOwesNothingAboutSelling() {
        String plan = "Epic: gameplay - the hero jumps between platforms and collects stars. "
                + "Epic: three levels with increasing difficulty and a restart button.";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(plan, List.of("DE", "US"));

        assertThat(findings)
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .as("no purchases in the plan means no purchase duties, and no publishing or accounts "
                        + "means no age-labelling or parental-consent duties either")
                .doesNotContain("purchase-transparency", "youth-protection");
    }

    @Test
    void aGameThatDoesSellStillOwesThem() {
        String plan = "Epic: gameplay loop with multiplayer matches. "
                + "Epic: in-app purchase of cosmetic items using coins bought with real money.";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(plan, List.of("DE", "US"));

        assertThat(findings)
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .as("the moment the plan describes selling inside the game, the duties it was exempt from "
                        + "must come back")
                .contains("purchase-transparency", "youth-protection");
    }

    @Test
    void anInternalToolIsNotToldAboutConsumerSalesDuties() {
        String plan = "Internal tool: a back office task tracker replacing a spreadsheet, with assignment "
                + "and status reporting for the team.";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(plan, List.of("DE", "US"));

        assertThat(findings)
                .extracting(MarketComplianceGate.Finding::requirement)
                .as("14-day withdrawal and destination sales tax are consumer-sales rules; an internal "
                        + "tool that sells nothing must never inherit them")
                .noneSatisfy(r -> assertThat(r.toLowerCase()).contains("withdrawal"))
                .noneSatisfy(r -> assertThat(r.toLowerCase()).contains("sales tax"));
    }

    @Test
    void flattensAPlanIntoTheTextItChecks() throws Exception {
        String json = """
                {"epics":[{"title":"Legal pages","jtbd":"When I buy...","requirements":["R1: Impressum"],
                "slices":[{"title":"Footer links","jtbd":"...","acceptanceCriteria":"Given ... Impressum"}]}]}
                """;

        String flattened = gate.flatten(mapper.readTree(json));

        assertThat(flattened).contains("Legal pages", "Impressum", "Footer links");
    }

    @Test
    void saysNothingWhenThereIsNoPlanToCheck() {
        assertThat(gate.uncoveredStatutoryRequirements("", List.of("DE"))).isEmpty();
        assertThat(gate.uncoveredStatutoryRequirements(null, List.of("DE"))).isEmpty();
    }
}
