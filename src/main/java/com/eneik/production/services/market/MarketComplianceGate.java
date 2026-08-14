package com.eneik.production.services.market;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks a finished decomposition plan against the market corpus and reports statutory requirements the
 * plan does not appear to cover.
 *
 * Deliberately limited to statutory entries, and that limit is the whole design. The plan for a gate was
 * to start where being wrong is impossible: a legal obligation does not become optional because a sample
 * was small, so no threshold, no confidence and no measured share is needed to justify acting on it.
 * Observed requirements can join later, once there is enough measurement to set a threshold honestly.
 *
 * It reports rather than blocks, for now. Coverage is judged by matching the words a plan would really
 * contain (capability detectionKeywords in the corpus) against the plan's own text - evidence, not prose
 * interpretation, but still an approximation: a plan can cover a duty using words nobody listed. Blocking
 * on an approximation would destroy real work to prevent a hypothetical omission, and a wrongly blocked
 * plan is invisible while a wrongly passed one shows up in the report. Once the false-positive rate is
 * measured against real plans, blocking becomes a decision with evidence behind it instead of a guess.
 */
@Service
public class MarketComplianceGate {
    private static final Logger log = LoggerFactory.getLogger(MarketComplianceGate.class);

    private final MarketCorpusService corpus;

    public MarketComplianceGate(MarketCorpusService corpus) {
        this.corpus = corpus;
    }

    /**
     * @param capabilityId which corpus capability looks uncovered
     * @param requirement  the obligation itself
     * @param source       the act or standard it comes from - so a human can check rather than trust
     */
    public record Finding(String capabilityId, String requirement, String source, String market) {
    }

    /**
     * @param planText   the decomposition plan flattened to text (epic titles, requirements, slice criteria)
     * @param markets    which markets this product serves; unknown markets mean every market's rules are
     *                   considered, because assuming a narrower market is how an obligation goes missing
     */
    public List<Finding> uncoveredStatutoryRequirements(String planText, List<String> markets) {
        List<Finding> findings = new ArrayList<>();
        if (planText == null || planText.isBlank() || !corpus.isAvailable()) {
            return findings;
        }
        String haystack = planText.toLowerCase(Locale.ROOT);

        // Most statutory duties are scoped to a kind of product, so the plan is first read for evidence of
        // WHICH kind it describes. No evidence at all is not the same as evidence of none: in that case
        // every duty is considered, because silently narrowing scope is how an obligation goes missing.
        List<String> profiles = corpus.profilesInEvidence(planText);

        Set<String> seen = new LinkedHashSet<>();
        for (String market : markets) {
            for (MarketCorpusService.Expectation expectation : corpus.influentialExpectations(market)) {
                if (!"statutory".equals(expectation.status())) {
                    continue;
                }
                if (!appliesToAnyOf(expectation, profiles)) {
                    continue;
                }
                if (!seen.add(expectation.capabilityId() + "|" + expectation.requirement())) {
                    continue;
                }
                List<String> keywords = corpus.detectionKeywords(expectation.capabilityId());
                if (keywords.isEmpty()) {
                    // No way to test this one by evidence, so no claim is made about it either way -
                    // silence is honest here, a guess would not be.
                    continue;
                }
                boolean covered = keywords.stream().anyMatch(k -> haystack.contains(k.toLowerCase(Locale.ROOT)));
                if (!covered) {
                    findings.add(new Finding(expectation.capabilityId(), expectation.requirement(),
                            expectation.source(), expectation.market()));
                }
            }
        }
        if (!findings.isEmpty()) {
            log.warn("MarketComplianceGate: decomposition plan shows no sign of covering {} statutory "
                    + "requirement(s): {}", findings.size(),
                    findings.stream().map(Finding::capabilityId).toList());
        }
        return findings;
    }

    /**
     * Universal duties always apply. A profile-scoped duty applies when the plan shows evidence of one of
     * its profiles - or when the plan showed evidence of no profile whatsoever, since an unreadable plan
     * must not be quietly exempted from the law.
     */
    private boolean appliesToAnyOf(MarketCorpusService.Expectation expectation, List<String> profilesInEvidence) {
        List<String> scope = expectation.appliesToProfiles();
        if (scope == null || scope.isEmpty() || scope.contains("*")) {
            return true;
        }
        if (profilesInEvidence.isEmpty()) {
            return true;
        }
        return scope.stream().anyMatch(profilesInEvidence::contains);
    }

    /** Flattens a parsed compiler plan into the text the check reads. */
    public String flatten(JsonNode planRoot) {
        if (planRoot == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode epic : planRoot.path("epics")) {
            text.append(epic.path("title").asText("")).append(' ')
                    .append(epic.path("jtbd").asText("")).append(' ');
            for (JsonNode requirement : epic.path("requirements")) {
                text.append(requirement.asText("")).append(' ');
            }
            for (JsonNode slice : epic.path("slices")) {
                text.append(slice.path("title").asText("")).append(' ')
                        .append(slice.path("jtbd").asText("")).append(' ')
                        .append(slice.path("acceptanceCriteria").asText("")).append(' ');
            }
        }
        return text.toString();
    }
}
