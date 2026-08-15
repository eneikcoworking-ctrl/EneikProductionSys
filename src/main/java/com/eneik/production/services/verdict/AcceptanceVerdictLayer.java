package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ClientAcceptanceTraversalEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.ClientAcceptanceTraversalRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.market.MarketCorpusService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Has the person who PAID seen the thing they bought, working?
 *
 * Closes F30. Every valuePath in the market corpus traces the END USER's journey and states what must be
 * POSSIBLE. None traces the buyer's, and a possibility claim is not witnessed by another possibility claim -
 * so the factory reached DELIVERED on merge counts, which is a claim about what was built standing in for a
 * claim about what was shown.
 *
 * The acceptance rule is not a seventeenth chain. It is the existing chains under a change of quantifier:
 * where a valuePath says every link MUST BE walkable, acceptance says at least one complete traversal HAS
 * BEEN walked, by the client, on the deployed instance, against real content.
 *
 * <pre>
 *     witnessed(P) = product over declared paths of (client-walked links / declared links)
 * </pre>
 *
 * A product and not a sum, and with no threshold. Value multiplies along a chain - the corpus already holds
 * this, and it is why a missing link is fatal rather than costly - so demonstration multiplies too: one
 * unwalked link makes the whole product zero, because a client who got stuck halfway was not shown a
 * working product. A threshold here would be the same error removed three times already this week, a proxy
 * measure standing in for the property.
 */
@Component
public class AcceptanceVerdictLayer implements VerdictLayer {

    static final String PROPOSITION_ACCEPTED =
            "the client has walked every declared value chain, complete, on the deployed instance";

    /** The rule requires the CLIENT walked it; a factory walk witnesses a different proposition. */
    public static final String WALKED_BY_CLIENT = "client";

    private final ClientAcceptanceTraversalRepository traversalRepository;
    private final WishlistRepository wishlistRepository;
    private final MarketCorpusService marketCorpusService;

    public AcceptanceVerdictLayer(ClientAcceptanceTraversalRepository traversalRepository,
                                  WishlistRepository wishlistRepository,
                                  MarketCorpusService marketCorpusService) {
        this.traversalRepository = traversalRepository;
        this.wishlistRepository = wishlistRepository;
        this.marketCorpusService = marketCorpusService;
    }

    @Override
    public String layerName() {
        return "acceptance";
    }

    @Override
    public List<String> declaredPropositions(UUID projectId) {
        return List.of(PROPOSITION_ACCEPTED);
    }

    @Override
    public List<Judgement> judge(UUID projectId) {
        try {
            return List.of(judgeAcceptance(projectId));
        } catch (RuntimeException e) {
            return List.of(Judgement.abstain(layerName(), PROPOSITION_ACCEPTED,
                    "could not read the acceptance record: " + e.getMessage()));
        }
    }

    private Judgement judgeAcceptance(UUID projectId) {
        String clientWords = clientBriefText(projectId);
        if (clientWords.isBlank()) {
            // Not PERMIT. With no brief there is no way to say WHICH chains this product owes, and a
            // denominator of zero would make the empty product 1 - a vacuous acceptance of everything.
            return Judgement.abstain(layerName(), PROPOSITION_ACCEPTED,
                    "the project has no client brief, so which value chains it owes is undeclared and "
                            + "acceptance has nothing to be measured against");
        }
        List<String> kinds = marketCorpusService.profilesInEvidence(clientWords);
        if (kinds.isEmpty()) {
            // An unrecognised product kind excuses nothing - it means the corpus cannot yet say what this
            // product must demonstrate, which is the corpus's debt and not the product's clearance.
            return Judgement.abstain(layerName(), PROPOSITION_ACCEPTED,
                    "the corpus recognises no product kind in the client's own words, so the chains that "
                            + "would have to be walked are not declared - a gap in the corpus, not a "
                            + "clearance for the product");
        }

        List<ClientAcceptanceTraversalEntity> traversals =
                traversalRepository.findByProjectIdOrderByTraversedAtDesc(projectId);
        Set<String> clientWalked = new LinkedHashSet<>();
        int factoryWalks = 0;
        for (ClientAcceptanceTraversalEntity t : traversals) {
            if (WALKED_BY_CLIENT.equalsIgnoreCase(t.getWalkedBy())) {
                clientWalked.add(key(t.getProfileId(), t.getActor(), t.getLink()));
            } else {
                factoryWalks++;
            }
        }

        double witnessed = 1.0;
        int declaredLinks = 0;
        int walkedLinks = 0;
        List<String> unwalked = new ArrayList<>();
        for (JsonNode profile : marketCorpusService.profiles()) {
            String profileId = profile.path("id").asText("");
            if (!kinds.contains(profileId)) {
                continue;
            }
            for (JsonNode path : profile.path("valuePaths")) {
                String actor = path.path("actor").asText("user");
                int links = 0;
                int walked = 0;
                for (JsonNode link : path.path("path")) {
                    String text = link.asText("");
                    if (text.isBlank()) {
                        continue;
                    }
                    links++;
                    if (clientWalked.contains(key(profileId, actor, text))) {
                        walked++;
                    } else if (unwalked.size() < 12) {
                        unwalked.add(profileId + "/" + actor + ": " + text);
                    }
                }
                if (links == 0) {
                    continue;
                }
                declaredLinks += links;
                walkedLinks += walked;
                witnessed *= (double) walked / links;
            }
        }

        if (declaredLinks == 0) {
            return Judgement.abstain(layerName(), PROPOSITION_ACCEPTED,
                    "the recognised product kinds " + kinds + " declare no value paths, so there is "
                            + "nothing to have been walked - again a gap in the corpus, not a clearance");
        }

        String evidence = "witnessed=" + witnessed + ", " + walkedLinks + "/" + declaredLinks
                + " links walked by the client across kinds " + kinds
                + (factoryWalks > 0 ? ", plus " + factoryWalks + " factory-side walks not counted" : "");

        if (witnessed >= 1.0) {
            return Judgement.permit(layerName(), PROPOSITION_ACCEPTED, evidence);
        }
        // ABSTAIN rather than WITHHOLD, per F30: while acceptance is undecided the gate owes an abstention.
        // An absent record cannot refute the client having walked the product - they may simply not have
        // told us - so the honest claim is that nothing is established, not that it failed. Abstention
        // blocks just as firmly; it is not the softer answer, only the true one.
        return Judgement.abstain(layerName(), PROPOSITION_ACCEPTED,
                "acceptance is not established: " + evidence
                        + (factoryWalks > 0
                                ? ". Factory-side walks show the path CAN be walked, which is a different "
                                        + "proposition - counting them here would let the factory accept "
                                        + "its own work"
                                : "")
                        + ". Unwalked: " + unwalked);
    }

    private String key(String profileId, String actor, String link) {
        return (profileId + "|" + actor + "|" + link).toLowerCase(Locale.ROOT).trim();
    }

    /**
     * The client's OWN words, not the factory's.
     *
     * Deliberately restricted to {@code WishlistSource.client}: the factory's own generated wishlists carry
     * plenty of product vocabulary, and letting them into the haystack would let the factory decide which
     * chains it owes - the same self-acceptance the walkedBy distinction exists to prevent, one level up.
     */
    private String clientBriefText(UUID projectId) {
        StringBuilder text = new StringBuilder();
        for (WishlistEntity w : wishlistRepository.findByProjectId(projectId)) {
            if (w.getSource() != WishlistSource.client) {
                continue;
            }
            if (w.getContent() != null) {
                text.append(w.getContent()).append('\n');
            }
        }
        return text.toString();
    }
}
