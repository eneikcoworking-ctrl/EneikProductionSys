package com.eneik.production.services.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falsification tests for MarketComplianceGate anchored in Barcan philosopher patterns:
 * 1. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006 Authorization ambiguity, Raz):
 *    Turns legal prohibitions into an executable denial/audit path; filters strictly for
 *    statutory legal duties, ignoring hypotheses or unverified opinions.
 * 2. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010 Data lineage loss, Goldman):
 *    Every reported finding carries an explicit, verifiable legal source citation (e.g. GDPR, BGB, TMG),
 *    never reporting unsupported accusations.
 * 3. AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY (D006 Boundary topology, Varzi):
 *    Enforces topological boundaries on obligation scope: profile boundaries, condition boundaries,
 *    and market boundaries. Unclassified plans are never quietly excused from baseline duties.
 */
class MarketComplianceGateFalsificationTest {

    private final MarketCorpusService corpus = new MarketCorpusService("market-corpus");
    private final MarketComplianceGate gate = new MarketComplianceGate(corpus);

    @Test
    @DisplayName("Raz D006: Gate strictly evaluates statutory obligations as executable code, ignoring opinions")
    void falsifyRazProhibitionAsCode_statutoryObligationsEnforced() {
        String barePlan = "Build a user profile dashboard and photo sharing feed";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(barePlan, List.of("DE"));

        assertThat(findings)
                .as("Bare plan with no legal compliance disclosures must trigger statutory findings")
                .isNotEmpty();

        // Ensure all findings are derived from statutory requirements
        assertThat(findings)
                .allSatisfy(f -> assertThat(f.capabilityId()).isIn(
                        "accessibility", "data-subject-rights", "consent-management",
                        "backup-restore", "de-site-disclosures", "youth-protection",
                        "purchase-transparency", "us-privacy-and-tax"
                ));
    }

    @Test
    @DisplayName("Goldman D010: Every generated finding carries a non-blank statutory source citation")
    void falsifyGoldmanReliabilityChain_findingsAlwaysCiteLegalSource() {
        String partialPlan = "Shopping basket and payment flow";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(partialPlan, List.of("DE", "US"));

        assertThat(findings).isNotEmpty();
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.source())
                    .as("Finding for '%s' must cite a valid legal act/source", f.requirement())
                    .isNotBlank();
            assertThat(f.capabilityId()).isNotBlank();
            assertThat(f.requirement()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Varzi D006: Unclassifiable plans do not escape universal baseline statutory duties")
    void falsifyVarziBoundaryTopology_unclassifiablePlanRetainsUniversalDuties() {
        String minimalPlan = "Just implement the basic draft";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(minimalPlan, List.of("DE"));

        // Baseline universal statutory duties (accessibility, data subject rights, legal notice) must apply
        assertThat(findings)
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .contains("accessibility", "data-subject-rights", "de-site-disclosures");
    }

    @Test
    @DisplayName("Varzi D006: Condition boundary prevents false positive duties when condition is absent")
    void falsifyVarziBoundaryTopology_conditionAbsenceExemptsConditionalDuties() {
        // Plan has content and chat, but zero purchase, sale, or commerce keywords
        String readOnlyBlog = "Epic: blog reader - users can view articles, search tags, and read comments.";

        List<MarketComplianceGate.Finding> findings =
                gate.uncoveredStatutoryRequirements(readOnlyBlog, List.of("DE", "US"));

        assertThat(findings)
                .extracting(MarketComplianceGate.Finding::capabilityId)
                .as("A read-only blog without commercial transactions must not trigger purchase duties")
                .doesNotContain("purchase-transparency");
    }

    @Test
    @DisplayName("Varzi D006: Market boundaries prevent jurisdiction rule leakage")
    void falsifyVarziBoundaryTopology_marketIsolation() {
        String plan = "Internal CRM and project management board";

        List<MarketComplianceGate.Finding> usFindings =
                gate.uncoveredStatutoryRequirements(plan, List.of("US"));

        assertThat(usFindings)
                .extracting(MarketComplianceGate.Finding::requirement)
                .as("German-specific Impressum (§5 TMG) must not leak into a US-only market check")
                .noneSatisfy(req -> assertThat(req.toLowerCase()).contains("impressum"));
    }
}
