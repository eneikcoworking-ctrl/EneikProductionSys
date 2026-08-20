package com.eneik.production.services.runtime;

import com.eneik.production.models.persistence.CapabilityObservationEntity;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.CapabilityObservationRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Product value, made countable.
 *
 * Until now the factory's only measure of the running product was one Bernoulli - did the stack boot and
 * answer /health. That is the degenerate case |C| = 1 of the real measure, where C is the set of
 * capabilities the product claims:
 *
 *     V_p = |{ c in C : LCB_0.95(c) >= theta }|
 *
 * <p><b>Where C comes from.</b> The product's own OpenAPI contract, {@code docs/contracts/<feature>.openapi.yaml},
 * produced by BARCAN-TAG-12 at stage 27. The denominator is therefore what the product ASSERTS about
 * itself, not what the factory decomposed it into - Charter invariant 8, which requires the counted set to
 * be declared and its exclusions enumerated. Capabilities the contract does not declare are not counted,
 * and that is visible rather than absorbed.
 *
 * <p><b>Why the lower bound and not the mean.</b> The mean rewards ignorance: a capability observed once,
 * successfully, would score 0.67 having proved nothing. The 95% credible lower bound makes confidence
 * something evidence has to earn, and since LCB &lt; 1 for every finite sample, no capability is ever
 * proven - only not yet refuted. A single failing observation lowers it again and the capability drops out
 * of the count. The measure can fall, which is the whole point.
 *
 * <p><b>Six Sigma.</b> One observation of one capability is an <i>opportunity</i>; a capability that did not
 * work is a <i>defect</i>. That is the product-layer opportunity `SixSigmaAuditService` never had - its
 * "Product" number counts quality gates, PR conflicts and code-integrity findings, which are facts about
 * how the work was made rather than defects a user could experience. DPMO over capability observations is
 * the same arithmetic applied to the right population.
 *
 * <p><b>Cost.</b> Probing happens inside the live-preview window the observation cycle already opens, so
 * the expensive part - `docker compose up` - is already paid. Each probe is one local HTTP call, which is
 * why every declared capability is probed in one window rather than one per cycle: round-robin would
 * stretch the evidence over |C| launches for no saving.
 */
@Service
public class ProductCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(ProductCapabilityService.class);

    /** Confidence a capability must earn before it counts toward V_p. Declared, not discovered. */
    @Value("${client-runtime-observability.capability-confidence-threshold:0.5}")
    private double confidenceThreshold;

    /** A contract can declare many routes; probing is cheap but not free, and a runaway contract must not
     * turn one observation window into thousands of calls. */
    @Value("${client-runtime-observability.max-capabilities-per-observation:40}")
    private int maxCapabilitiesPerObservation;

    private final FeatureRepository featureRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final RuntimeLauncherClient launcherClient;
    private final CapabilityObservationRepository observationRepository;

    public ProductCapabilityService(FeatureRepository featureRepository,
                                     GitHubPullRequestService gitHubPullRequestService,
                                     RuntimeLauncherClient launcherClient,
                                     CapabilityObservationRepository observationRepository) {
        this.featureRepository = featureRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.launcherClient = launcherClient;
        this.observationRepository = observationRepository;
    }

    /** One capability the product declares, and where it declared it. */
    public record DeclaredCapability(String key, String path, String sourceContract) {
    }

    /**
     * The capabilities this product declares, read from the contracts its own API-contract role produced.
     * The path is derived, never guessed at by listing a directory: TechnicalLeadCompiler writes
     * {@code docs/contracts/<featureName>.openapi.yaml} for BARCAN-TAG-12, so the feature title determines
     * the path exactly.
     */
    public List<DeclaredCapability> declaredCapabilities(ProjectEntity project) {
        List<DeclaredCapability> declared = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FeatureEntity feature : featureRepository.findByProjectId(project.getId())) {
            String title = feature.getTitle();
            if (title == null || title.isBlank()) {
                continue;
            }
            String path = "docs/contracts/" + title.toLowerCase(Locale.ROOT).replace(' ', '-') + ".openapi.yaml";
            String content = gitHubPullRequestService
                    .fetchFileContent(project, project.getDefaultBranch(), path)
                    .orElse(null);
            if (content == null) {
                continue; // this feature declared no contract - excluded from the denominator, visibly
            }
            for (String route : getRoutesOf(content)) {
                String key = "GET " + route;
                if (seen.add(key)) {
                    declared.add(new DeclaredCapability(key, route, path));
                }
            }
        }
        return declared;
    }

    /**
     * Extracts the GET routes an OpenAPI document declares. Deliberately a narrow, deterministic reader
     * rather than a full parser or a model call: only paths whose declared operations include `get` are
     * taken, because only those can be probed without inventing a request body - and a capability we cannot
     * check without inventing something is not evidence, it is a guess.
     */
    static List<String> getRoutesOf(String openApiYaml) {
        List<String> routes = new ArrayList<>();
        if (openApiYaml == null || openApiYaml.isBlank()) {
            return routes;
        }
        String[] lines = openApiYaml.split("\\r?\\n");
        boolean inPaths = false;
        String currentPath = null;
        int pathIndent = -1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (!inPaths) {
                if (trimmed.startsWith("paths:")) {
                    inPaths = true;
                }
                continue;
            }
            if (indent == 0) {
                break; // left the paths block
            }
            if (trimmed.endsWith(":") && trimmed.startsWith("/")) {
                currentPath = trimmed.substring(0, trimmed.length() - 1).trim();
                pathIndent = indent;
                continue;
            }
            if (currentPath != null && indent > pathIndent && trimmed.startsWith("get:")) {
                routes.add(currentPath);
                currentPath = null;
            }
        }
        return routes;
    }

    /**
     * Probes every declared capability against a running instance and records one observation each.
     * Returns how many were satisfied. A capability with a path parameter is skipped rather than probed
     * with an invented value - see getRoutesOf.
     */
    public int probeAll(ProjectEntity project, String baseUrl) {
        List<DeclaredCapability> declared = declaredCapabilities(project);
        if (declared.isEmpty()) {
            return 0;
        }
        int satisfied = 0;
        int probed = 0;
        for (DeclaredCapability capability : declared) {
            if (probed >= maxCapabilitiesPerObservation) {
                log.warn("ProductCapabilityService: project {} declares more than {} capabilities; probing "
                                + "stopped at the cap so one observation window cannot run away",
                        project.getId(), maxCapabilitiesPerObservation);
                break;
            }
            if (capability.path().contains("{")) {
                continue; // a templated path needs an invented value - not evidence
            }
            probed++;
            RuntimeLauncherClient.FetchResult result = launcherClient.fetchHtml(baseUrl + capability.path());
            boolean ok = result.statusCode() != null && result.statusCode() >= 200 && result.statusCode() < 300;
            if (ok) {
                satisfied++;
            }
            CapabilityObservationEntity row = new CapabilityObservationEntity();
            row.setProjectId(project.getId());
            row.setCapabilityKey(capability.key());
            row.setSourceContract(capability.sourceContract());
            row.setSatisfied(ok);
            row.setStatusCode(result.statusCode());
            row.setDetail(result.error() == null ? null : result.error().substring(0, Math.min(2000, result.error().length())));
            observationRepository.save(row);
        }
        log.info("ProductCapabilityService: project {} - {} of {} declared capabilities satisfied on this "
                        + "observation ({} skipped as templated)",
                project.getId(), satisfied, probed, declared.size() - probed);
        return satisfied;
    }

    /** Product value and the Six Sigma population it feeds. */
    public record ProductValue(
            int declaredCapabilities,
            int workingCapabilities,
            long opportunities,
            long defects,
            double dpmo) {
    }

    /**
     * V_p and its Six Sigma population, computed from recorded observations only - never from a live probe,
     * so reading the measure can never change it.
     */
    public ProductValue currentValue(UUID projectId) {
        Map<String, BetaCounts> perCapability = new LinkedHashMap<>();
        long opportunities = 0;
        long defects = 0;
        for (CapabilityObservationEntity row : observationRepository.findByProjectIdOrderByObservedAtDesc(projectId)) {
            BetaCounts counts = perCapability.computeIfAbsent(row.getCapabilityKey(), key -> new BetaCounts());
            opportunities++;
            if (row.isSatisfied()) {
                counts.successes++;
            } else {
                counts.failures++;
                defects++;
            }
        }
        int working = 0;
        for (BetaCounts counts : perCapability.values()) {
            BetaPosterior posterior = new BetaPosterior(1.0 + counts.successes, 1.0 + counts.failures);
            if (posterior.credibleIntervalLowerBound() >= confidenceThreshold) {
                working++;
            }
        }
        double dpmo = opportunities == 0 ? 0.0 : ((double) defects / opportunities) * 1_000_000.0;
        return new ProductValue(perCapability.size(), working, opportunities, defects, dpmo);
    }

    private static final class BetaCounts {
        private int successes;
        private int failures;
    }
}
