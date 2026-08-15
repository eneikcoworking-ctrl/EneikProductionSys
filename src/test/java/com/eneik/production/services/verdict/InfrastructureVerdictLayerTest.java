package com.eneik.production.services.verdict;

import com.eneik.production.services.FactorySelfHealthService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The distinction under test is the one the factory could not draw on 2026-08-15: an unreachable
 * dependency has to be a stated refusal, not an absence of any statement at all.
 *
 * Reachability is exercised against a real socket rather than a mocked HTTP client, because the failure
 * being prevented was infrastructural - a mock would have answered happily on the day the launcher was
 * down, which is precisely the mistake being encoded against.
 */
class InfrastructureVerdictLayerTest {

    /** Privileged and unbound, so the connection is refused at once rather than timing out. */
    private static final String UNREACHABLE = "http://127.0.0.1:1";

    private final FactorySelfHealthService selfHealth = mock(FactorySelfHealthService.class);
    private final UUID projectId = UUID.randomUUID();
    private final List<HttpServer> started = new ArrayList<>();

    @AfterEach
    void stopServers() {
        started.forEach(s -> s.stop(0));
    }

    /** A stand-in for a reachable FastAPI dependency: only /openapi.json, which is what the layer probes. */
    private String fastApiAnswering(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi.json", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        started.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private InfrastructureVerdictLayer layer(String launcherUrl, String mlUrl) {
        return new InfrastructureVerdictLayer(selfHealth, launcherUrl, mlUrl);
    }

    private void databaseIs(boolean healthy) {
        when(selfHealth.inspect()).thenReturn(new FactorySelfHealthService.DatabaseHealth(
                100L, 90L, 1.1, healthy, healthy ? "database is healthy" : "database is bloated 9x"));
    }

    private Judgement about(List<Judgement> judgements, String proposition) {
        return judgements.stream()
                .filter(j -> j.proposition().equals(proposition))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no judgement about: " + proposition));
    }

    @Test
    void anUnreachableLauncherWithholdsRatherThanStayingSilent() {
        databaseIs(true);
        Judgement j = about(layer(UNREACHABLE, UNREACHABLE).judge(projectId),
                InfrastructureVerdictLayer.PROPOSITION_LAUNCHER);

        assertThat(j.verdict())
                .as("the launcher demonstrably is not answering - that is established, not unknown, so it "
                        + "is a refusal and not an abstention")
                .isEqualTo(Verdict.WITHHOLD);
        assertThat(j.reason())
                .as("the reason must name the consequence, since a refusal whose cost is unstated gets "
                        + "read as a formality")
                .contains("no product can be launched or verified")
                .contains("factory rather than the product");
    }

    @Test
    void anUnreachableMlServiceNamesTheSilentAnswersItWouldOtherwiseGive() {
        databaseIs(true);
        Judgement j = about(layer(UNREACHABLE, UNREACHABLE).judge(projectId),
                InfrastructureVerdictLayer.PROPOSITION_ML);

        assertThat(j.verdict()).isEqualTo(Verdict.WITHHOLD);
        assertThat(j.reason())
                .as("MLPredictionServiceClient returns false from bottleneck prediction and null from "
                        + "embed on failure, so an unreachable ML service reads downstream as a positive "
                        + "finding of no bottleneck - the refusal must say so")
                .contains("no bottleneck");
    }

    @Test
    void reachableDependenciesPermit() throws Exception {
        databaseIs(true);
        String url = fastApiAnswering(200);
        List<Judgement> judgements = layer(url, url).judge(projectId);

        assertThat(about(judgements, InfrastructureVerdictLayer.PROPOSITION_LAUNCHER).verdict())
                .isEqualTo(Verdict.PERMIT);
        assertThat(about(judgements, InfrastructureVerdictLayer.PROPOSITION_ML).verdict())
                .isEqualTo(Verdict.PERMIT);
    }

    @Test
    void anySpokenAnswerProvesReachabilityEvenWhenItIsAnError() throws Exception {
        databaseIs(true);
        String url = fastApiAnswering(503);

        assertThat(about(layer(url, url).judge(projectId),
                InfrastructureVerdictLayer.PROPOSITION_LAUNCHER).verdict())
                .as("the declared proposition is reachability, not the dependency's opinion of its own "
                        + "health - conflating the two would let one sick product's 503 block every other "
                        + "project's verdict")
                .isEqualTo(Verdict.PERMIT);
    }

    @Test
    void theProbeIsReadOnly() throws Exception {
        databaseIs(true);
        List<String> methods = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            methods.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        started.add(server);
        String url = "http://127.0.0.1:" + server.getAddress().getPort();

        layer(url, UNREACHABLE).judge(projectId);

        assertThat(methods)
                .as("the launcher's own routes are all POST and two of them (/launch, /teardown) act on "
                        + "real containers - a check that caused the state it reports on would be worse "
                        + "than no check")
                .containsExactly("GET /openapi.json");
    }

    @Test
    void anUnhealthyDatabaseWithholdsAndCarriesItsMeasurement() {
        databaseIs(false);
        Judgement j = about(layer(UNREACHABLE, UNREACHABLE).judge(projectId),
                InfrastructureVerdictLayer.PROPOSITION_DATABASE);

        assertThat(j.verdict()).isEqualTo(Verdict.WITHHOLD);
        assertThat(j.reason()).contains("bloated");
        assertThat(j.evidence())
                .as("a refusal a human cannot re-check is an accusation - the numbers it rests on travel "
                        + "with it")
                .contains("bloat");
    }

    @Test
    void aFailingHealthCheckAbstainsRatherThanBreakingTheWholeLattice() {
        when(selfHealth.inspect()).thenThrow(new IllegalStateException("h2 file locked"));

        Judgement j = about(layer(UNREACHABLE, UNREACHABLE).judge(projectId),
                InfrastructureVerdictLayer.PROPOSITION_DATABASE);

        assertThat(j.verdict())
                .as("being unable to assess health is not the same as finding it bad")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason()).contains("h2 file locked");
    }

    @Test
    void everyJudgedPropositionIsAlsoDeclared() {
        databaseIs(true);
        InfrastructureVerdictLayer layer = layer(UNREACHABLE, UNREACHABLE);

        assertThat(layer.judge(projectId).stream().map(Judgement::proposition))
                .as("a proposition that is judged but never declared cannot be told apart from one that "
                        + "was never considered, which is exactly how a down launcher passed unnoticed")
                .containsExactlyInAnyOrderElementsOf(layer.declaredPropositions(projectId));
    }
}
