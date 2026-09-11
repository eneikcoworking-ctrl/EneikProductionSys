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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Product value, made countable.
 *
 * Until now the factory's only measure of the running product was one Bernoulli - did the stack boot and
 * answer /health. That is the degenerate case |C| = 1 of the real measure, where C is the set of
 * capabilities the product claims:
 *
 *     V_p = |{ c in C : LCB_0.95(c) >= theta }|
 *
 * <p><b>Where C comes from.</b> The product's own OpenAPI contracts in {@code docs/contracts/},
 * produced by the product's API-contract layer. The denominator is therefore what the product ASSERTS about
 * itself, not what the factory decomposed it into - Charter invariant 8, which requires the counted set to
 * be declared and its exclusions enumerated. Capabilities the contracts do not declare are not counted,
 * and that is visible rather than absorbed.
 *
 * <p><b>Why the lower bound and not the mean.</b> The mean rewards ignorance: a capability observed once,
 * successfully, would score 0.67 having proved nothing. The 95% credible lower bound makes confidence
 * something evidence has to earn, and since LCB &lt; 1 for every finite sample, no capability is ever
 * proven - only not yet refuted. A single failing observation lowers it again and the capability drops out
 * of the count. The measure can fall, which is the whole point.
 *
 * <p><b>Six Sigma.</b> One observation of one capability is an <i>opportunity</i>; a capability that did not
 * work is a <i>defect</i>. The three-layer Factory/Delivery/Product model already exists and already
 * separates correctly - Layer 3 counts only non-dismissed features and deliberately excludes onboarding
 * findings and runtime anomalies as platform noise. What no layer had was a defect drawn from the product
 * RUNNING: quality gates, PR conflicts and code-integrity findings are all records the factory created
 * about its own work. Capability observations are added as a fourth category inside Layer 3
 * ({@code SixSigmaAuditService.computeCapabilityObservationCounts}), never as a second sigma here - the
 * product's sigma stays one number computed in one place.
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

    /**
     * Cache of declared capabilities per project ID. Avoids repeating directory listings and contract fetches
     * when the branch and commit SHA have not changed.
     */
    private final ConcurrentHashMap<UUID, CachedDeclaredCapabilities> capabilityCache = new ConcurrentHashMap<>();

    private record CachedDeclaredCapabilities(String branch, String commitSha, List<DeclaredCapability> capabilities) {}

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

    public void invalidateCache(UUID projectId) {
        if (projectId != null) {
            capabilityCache.remove(projectId);
        }
    }

    public void invalidateAllCaches() {
        capabilityCache.clear();
    }

    /**
     * The capabilities this product declares, read from OpenAPI contracts in {@code docs/contracts/}.
     * The product asserts its own capabilities directly in domain-named contract files (e.g.
     * {@code StrainManagement.openapi.yaml}), NOT through factory-internal feature title translations.
     *
     * <p>When the contracts directory cannot be read (API error, missing token, or directory read failure),
     * declared capabilities are treated as unmeasurable/empty — never guessed from database feature titles.
     *
     * <p>INUS_FACTOR_CHECK (D007): queries the directory once rather than asking N individual 404 questions,
     * and caches known results until the branch or commit SHA advances.
     * BOUNDARY_TOPOLOGY (D006): loads DB entities before network operations so database connections
     * are never held across external HTTP calls.
     * <p>DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT (D009) / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY (D013):
     * Eliminates sense-reference split by discovering physical contract files rather than guessing names.
     */
    public List<DeclaredCapability> declaredCapabilities(ProjectEntity project) {
        return declaredCapabilities(project, null);
    }

    public List<DeclaredCapability> declaredCapabilities(ProjectEntity project, String commitSha) {
        if (project == null || project.getId() == null) {
            return List.of();
        }
        String branch = project.getDefaultBranch();
        CachedDeclaredCapabilities cached = capabilityCache.get(project.getId());
        if (cached != null && Objects.equals(cached.branch(), branch)) {
            if (commitSha == null || Objects.equals(cached.commitSha(), commitSha)) {
                return cached.capabilities();
            }
        }

        // INUS_FACTOR_CHECK (D007): Query docs/contracts directory once instead of asking N individual 404 questions
        Optional<Set<String>> filesInDirectory = gitHubPullRequestService.listDirectoryFiles(project, branch, "docs/contracts");

        List<DeclaredCapability> declared = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (filesInDirectory.isPresent()) {
            Set<String> dirFiles = filesInDirectory.get();
            if (!dirFiles.isEmpty()) {
                // DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT (D009): Derive capabilities from all contracts in docs/contracts
                List<String> contractFiles = dirFiles.stream()
                        .filter(ProductCapabilityService::isContractFile)
                        .sorted()
                        .toList();
                for (String fileName : contractFiles) {
                    String path = "docs/contracts/" + fileName;
                    String content = gitHubPullRequestService
                            .fetchFileContent(project, branch, path)
                            .orElse(null);
                    if (content == null) {
                        continue;
                    }
                    for (String route : getRoutesOf(content)) {
                        String key = "GET " + route;
                        if (seen.add(key)) {
                            declared.add(new DeclaredCapability(key, route, path));
                        }
                    }
                }
            }
        }
        // If filesInDirectory is empty (API error or directory read failure):
        // Rule: unmeasurable / empty — NEVER guess file names from feature titles.

        List<DeclaredCapability> immutableDeclared = List.copyOf(declared);
        capabilityCache.put(project.getId(), new CachedDeclaredCapabilities(branch, commitSha, immutableDeclared));
        return immutableDeclared;
    }

    static boolean isContractFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".openapi.yaml")
                || lower.endsWith(".openapi.yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".yml");
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
        return probeAll(project, baseUrl, null);
    }

    public int probeAll(ProjectEntity project, String baseUrl, String commitSha) {
        List<DeclaredCapability> declared = declaredCapabilities(project, commitSha);
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

    /**
     * Product value: how many declared capabilities are currently not-yet-refuted.
     *
     * 2026-08-21, corrected: this record carried its own `dpmo` field, which was a second product sigma
     * standing beside SixSigmaAuditService's Layer 3 - exactly the parallel truth this codebase keeps
     * paying for. The sigma belongs there and only there, where capability observations are now a fourth
     * defect category alongside quality gates, PR conflicts and code-integrity findings. What lives here is
     * the quantity Six Sigma does NOT express: a count of capabilities whose evidence currently clears the
     * declared confidence threshold. `opportunities` and `defects` remain as the raw population, so a
     * reader can see what the count rests on without recomputing a rate of their own.
     */
    public record ProductValue(
            int declaredCapabilities,
            int workingCapabilities,
            long opportunities,
            long defects) {
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
        return new ProductValue(perCapability.size(), working, opportunities, defects);
    }

    private static final class BetaCounts {
        private int successes;
        private int failures;
    }
}
