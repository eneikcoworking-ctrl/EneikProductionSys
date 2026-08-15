package com.eneik.production.services.verdict;

import com.eneik.production.services.FactorySelfHealthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Is the factory itself in a state where its answers about a product mean anything?
 *
 * Closes F6. `runtime-launcher` was down and nothing said so - and without it no product can be launched
 * or verified, while the TOC subordination in FalsificationCycleService gates on launchability. So
 * philosophy would either have been skipped or run blind, and the operator noticed, not the factory. It is
 * worth recording why that was possible: of the three dependencies judged here, `runtime-launcher` is the
 * one with no `healthcheck:` block in docker-compose.yml at all.
 *
 * The point is not to monitor infrastructure for its own sake. It is that a missing dependency silently
 * converts every downstream answer into one about the FACTORY rather than about the product: "the product
 * does not launch" and "nothing was able to try" are different claims, and a system that cannot tell them
 * apart will report the second as the first. The same shape is already visible in MLPredictionServiceClient,
 * where a failed call returns `false` from bottleneck prediction and `null` from embed - so an unreachable
 * ML service reads downstream as "there is no bottleneck" and "this work has no semantic neighbours".
 * Declaring these propositions makes each absence a withholding rather than a silence.
 *
 * Each proposition is deliberately about REACHABILITY, not about the dependency's opinion of its own
 * health. Any answer at all settles reachability; conflating the two would let one sick product's 503
 * block every other project's verdict.
 */
@Component
public class InfrastructureVerdictLayer implements VerdictLayer {

    static final String PROPOSITION_LAUNCHER =
            "the runtime launcher is reachable, so a product CAN be observed";
    static final String PROPOSITION_ML =
            "the ML service is reachable, so bottleneck and semantic judgements are grounded";
    static final String PROPOSITION_DATABASE =
            "the orchestrator's own database is healthy";

    /**
     * Both dependencies are FastAPI apps whose own routes are POST-only (the launcher publishes exactly
     * /launch, /healthcheck, /fetch and /teardown, all POST). `/openapi.json` is FastAPI's built-in GET,
     * always present and free of side effects, which is what a reachability probe must be: asking a
     * dependency to DO something in order to find out whether it is there would make the check itself a
     * cause of the state it reports on.
     */
    private static final String PROBE_PATH = "/openapi.json";

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private final FactorySelfHealthService selfHealthService;
    private final String launcherUrl;
    private final String mlServiceUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT)
            .build();

    public InfrastructureVerdictLayer(
            FactorySelfHealthService selfHealthService,
            @Value("${runtime.launcher.url:http://localhost:8091}") String launcherUrl,
            @Value("${ml.service.url:http://localhost:8000}") String mlServiceUrl) {
        this.selfHealthService = selfHealthService;
        this.launcherUrl = launcherUrl;
        this.mlServiceUrl = mlServiceUrl;
    }

    @Override
    public String layerName() {
        return "infrastructure";
    }

    @Override
    public List<String> declaredPropositions(UUID projectId) {
        return List.of(PROPOSITION_LAUNCHER, PROPOSITION_ML, PROPOSITION_DATABASE);
    }

    @Override
    public List<Judgement> judge(UUID projectId) {
        List<Judgement> judgements = new ArrayList<>();
        judgements.add(judgeReachable(PROPOSITION_LAUNCHER, launcherUrl,
                "no product can be launched or verified"));
        judgements.add(judgeReachable(PROPOSITION_ML, mlServiceUrl,
                "bottleneck prediction silently answers 'no bottleneck' and embedding silently answers "
                        + "'no neighbours'"));
        judgements.add(judgeDatabase());
        return judgements;
    }

    private Judgement judgeReachable(String proposition, String baseUrl, String consequence) {
        String probe = baseUrl + PROBE_PATH;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(probe))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return Judgement.permit(layerName(), proposition,
                    probe + " answered HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            // We were shut down mid-check, which says nothing about the dependency. ABSTAIN, and restore
            // the flag rather than swallowing it - clearing another caller's interrupt to tidy up this
            // method is exactly the kind of silent side effect this layer exists to stop.
            Thread.currentThread().interrupt();
            return Judgement.abstain(layerName(), proposition,
                    "the reachability check was interrupted before it could answer");
        } catch (Exception e) {
            // WITHHOLD, not ABSTAIN: this is established rather than unknown. The dependency demonstrably
            // is not answering, and that fact is what makes any downstream verdict a claim about the
            // factory instead of about the product.
            return Judgement.withhold(layerName(), proposition,
                    "not answering (" + e.getMessage() + "), so " + consequence + " and any verdict formed "
                            + "now would describe the factory rather than the product",
                    probe);
        }
    }

    private Judgement judgeDatabase() {
        try {
            FactorySelfHealthService.DatabaseHealth health = selfHealthService.inspect();
            String evidence = "file " + health.fileSizeBytes() + "B, live " + health.liveDataBytes()
                    + "B, bloat " + health.bloatRatio();
            return health.healthy()
                    ? Judgement.permit(layerName(), PROPOSITION_DATABASE, evidence)
                    : Judgement.withhold(layerName(), PROPOSITION_DATABASE, health.assessment(), evidence);
        } catch (RuntimeException e) {
            // Being unable to assess health is not the same as finding it bad.
            return Judgement.abstain(layerName(), PROPOSITION_DATABASE,
                    "could not assess database health: " + e.getMessage());
        }
    }
}
