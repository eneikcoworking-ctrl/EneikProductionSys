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
                + "Checkout with strong customer authentication. Sales tax by destination, CCPA opt-out.";

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
