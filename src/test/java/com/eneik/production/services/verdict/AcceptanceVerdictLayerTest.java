package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ClientAcceptanceTraversalEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.ClientAcceptanceTraversalRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.market.MarketCorpusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dangerous case here is the vacuous one. An acceptance measure defined as a product over declared
 * paths is 1 when there are no declared paths, so every way of ending up with an empty denominator has to
 * resolve as "not established" rather than as approval - otherwise the least-informed project passes most
 * easily, which inverts the whole point.
 */
class AcceptanceVerdictLayerTest {

    private final ClientAcceptanceTraversalRepository traversals = mock(ClientAcceptanceTraversalRepository.class);
    private final WishlistRepository wishlists = mock(WishlistRepository.class);
    private final UUID projectId = UUID.randomUUID();

    private MarketCorpusService corpus;
    private AcceptanceVerdictLayer layer;

    /** A corpus with exactly one profile and one two-link chain, so the arithmetic is checkable by hand. */
    private static final String CORPUS = """
            {
              "schemaVersion": 3,
              "acceptanceRule": {"status": "derived", "requires": ["reachable", "seeded"]},
              "profiles": [
                {
                  "id": "shop",
                  "title": "Online shop",
                  "status": "derived",
                  "detectionKeywords": ["shop"],
                  "valuePaths": [{"actor": "customer", "path": ["find product", "pay"]}]
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("corpus");
        Files.writeString(root.resolve("profiles.json"), CORPUS);
        Files.writeString(root.resolve("capabilities.json"), "{\"capabilities\": []}");
        corpus = new MarketCorpusService(root.toString());
        layer = new AcceptanceVerdictLayer(traversals, wishlists, corpus);
        when(traversals.findByProjectIdOrderByTraversedAtDesc(projectId)).thenReturn(List.of());
    }

    private void clientAsksFor(String content) {
        WishlistEntity w = new WishlistEntity();
        w.setProjectId(projectId);
        w.setSource(WishlistSource.client);
        w.setContent(content);
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of(w));
    }

    private void walked(String walkedBy, String... links) {
        List<ClientAcceptanceTraversalEntity> rows = new ArrayList<>();
        for (String link : links) {
            ClientAcceptanceTraversalEntity t = new ClientAcceptanceTraversalEntity();
            t.setProjectId(projectId);
            t.setProfileId("shop");
            t.setActor("customer");
            t.setLink(link);
            t.setWalkedBy(walkedBy);
            rows.add(t);
        }
        when(traversals.findByProjectIdOrderByTraversedAtDesc(projectId)).thenReturn(rows);
    }

    private Judgement judge() {
        return layer.judge(projectId).get(0);
    }

    @Test
    void aCompleteClientTraversalIsTheOnlyThingThatPermits() {
        clientAsksFor("we need a shop");
        walked(AcceptanceVerdictLayer.WALKED_BY_CLIENT, "find product", "pay");

        assertThat(judge().verdict()).isEqualTo(Verdict.PERMIT);
    }

    @Test
    void aPartialTraversalWitnessesNothing() {
        clientAsksFor("we need a shop");
        walked(AcceptanceVerdictLayer.WALKED_BY_CLIENT, "find product");

        Judgement j = judge();
        assertThat(j.verdict())
                .as("value multiplies along a chain, so a client who got stuck halfway was not shown a "
                        + "working product - half of a chain is zero, not a half")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason()).contains("pay");
    }

    @Test
    void factoryWalksDoNotCountAsAcceptance() {
        clientAsksFor("we need a shop");
        walked("factory", "find product", "pay");

        Judgement j = judge();
        assertThat(j.verdict())
                .as("a factory-side walk witnesses that the path CAN be walked, which is the proposition "
                        + "the valuePath already made - counting it here would let the factory accept its "
                        + "own work")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason())
                .as("the factory walks must be visible in the reason rather than silently discarded, or "
                        + "the refusal looks like nothing was ever tried")
                .contains("factory-side walks");
    }

    @Test
    void noClientBriefAbstainsRatherThanPassingVacuously() {
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());

        Judgement j = judge();
        assertThat(j.verdict())
                .as("with no brief the denominator is empty and the empty product is 1, which would make "
                        + "the least-specified project the easiest to accept")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason()).contains("no client brief");
    }

    @Test
    void factoryWrittenWishlistsCannotSupplyTheDenominator() {
        WishlistEntity generated = new WishlistEntity();
        generated.setProjectId(projectId);
        generated.setSource(WishlistSource.coverage_gap);
        generated.setContent("add a shop basket checkout page");
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of(generated));

        assertThat(judge().reason())
                .as("letting the factory's own wishlists into the haystack would let it choose which "
                        + "chains it owes - the same self-acceptance the walkedBy split prevents")
                .contains("no client brief");
    }

    @Test
    void anUnrecognisedProductKindIsCorpusDebtNotClearance() {
        clientAsksFor("a bespoke telemetry pipeline for wind turbines");

        Judgement j = judge();
        assertThat(j.verdict()).isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason()).contains("not a clearance");
    }

    @Test
    void aFailingRepositoryAbstainsRatherThanBreakingTheLattice() {
        clientAsksFor("we need a shop");
        when(traversals.findByProjectIdOrderByTraversedAtDesc(projectId))
                .thenThrow(new IllegalStateException("db down"));

        Judgement j = judge();
        assertThat(j.verdict()).isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason()).contains("db down");
    }
}
