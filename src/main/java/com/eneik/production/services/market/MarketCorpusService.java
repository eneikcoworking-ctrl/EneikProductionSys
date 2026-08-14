package com.eneik.production.services.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the versioned market corpus - what software of a given kind must contain, independent of what any
 * one client thought to ask for. See market-corpus/README.md.
 *
 * The compiler can only ever build what the brief describes, and clients know their business rather than
 * what products of their class are expected to contain, so briefs arrive with predictable holes and the
 * flow faithfully reproduces them. This service supplies the missing half.
 *
 * Deliberately file-backed and cached by mtime, exactly like {@link com.eneik.production.services.RoleCapabilityLoader}
 * reads the BARCAN-TAG charters: knowledge that decides what gets built must be reviewable in a diff and
 * correctable by a human, not embedded in code or invented per-call by a model.
 *
 * The one rule that keeps this from becoming folklore: every expectation carries a source and a status, and
 * only statutory/standard/observed entries are allowed to influence anything. A "hypothesis" entry - which
 * includes anything an AI wrote from general knowledge - is stored to be verified or refuted, never acted
 * upon. {@link #influentialExpectations} is the only accessor that decisions may use.
 */
@Service
public class MarketCorpusService {
    private static final Logger log = LoggerFactory.getLogger(MarketCorpusService.class);

    /** Statuses whose evidence is strong enough to change what gets built. See README.md's table. */
    private static final List<String> INFLUENTIAL_STATUSES = List.of("statutory", "standard", "observed");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    private final String corpusRoot;

    public MarketCorpusService(@Value("${market-corpus.root:market-corpus}") String corpusRoot) {
        this.corpusRoot = corpusRoot;
    }

    /**
     * @param appliesToProfiles which product profiles this requirement is scoped to, or ["*"] for all.
     *                          Carried through rather than resolved here on purpose: this service does not
     *                          know what kind of product a brief describes, and guessing it would be the
     *                          exact silent assumption the corpus exists to prevent. The scope travels to
     *                          the compiler, which is already reading the brief, and it decides.
     * @param appliesWhen the capability-level condition in plain words, e.g. "the product takes payment"
     */
    public record Expectation(String capabilityId, String requirement, String kano, String market,
                              String status, String source, String note,
                              List<String> appliesToProfiles, String appliesWhen) {
    }

    /**
     * Every expectation that may legitimately influence a decision, for the given market, filtered by
     * status. Entries carrying a market other than the requested one are excluded; entries with no market
     * are universal and always included.
     *
     * Returns an empty list rather than throwing when the corpus is missing or unreadable - an absent
     * corpus must degrade the system to its previous behaviour, never break decomposition.
     */
    public List<Expectation> influentialExpectations(String market) {
        List<Expectation> result = new ArrayList<>();
        JsonNode root = readJson("capabilities.json");
        if (root == null) {
            return result;
        }
        for (JsonNode capability : root.path("capabilities")) {
            String capabilityId = capability.path("id").asText("");
            for (JsonNode expectation : capability.path("expectations")) {
                String status = expectation.path("status").asText("").toLowerCase(Locale.ROOT);
                if (!INFLUENTIAL_STATUSES.contains(status)) {
                    continue;
                }
                String entryMarket = expectation.path("market").asText("");
                if (!entryMarket.isBlank() && market != null && !entryMarket.equalsIgnoreCase(market)) {
                    continue;
                }
                List<String> profiles = new ArrayList<>();
                for (JsonNode p : expectation.path("appliesToProfiles")) {
                    profiles.add(p.asText());
                }
                result.add(new Expectation(
                        capabilityId,
                        expectation.path("requirement").asText(""),
                        expectation.path("kano").asText(""),
                        entryMarket,
                        status,
                        expectation.path("source").asText(""),
                        expectation.path("note").asText(""),
                        profiles.isEmpty() ? List.of("*") : profiles,
                        capability.path("appliesWhen").asText("")
                ));
            }
        }
        return result;
    }

    /**
     * Words a decomposition plan would realistically contain if it covers this capability. Used by
     * MarketComplianceGate to test coverage against evidence rather than by interpreting prose. An empty
     * list means the capability cannot be checked this way - and the gate then says nothing about it,
     * which is the honest outcome rather than a guess in either direction.
     */
    public List<String> detectionKeywords(String capabilityId) {
        JsonNode root = readJson("capabilities.json");
        if (root == null || capabilityId == null) {
            return List.of();
        }
        for (JsonNode capability : root.path("capabilities")) {
            if (capabilityId.equals(capability.path("id").asText(""))) {
                List<String> keywords = new ArrayList<>();
                for (JsonNode k : capability.path("detectionKeywords")) {
                    keywords.add(k.asText());
                }
                return keywords;
            }
        }
        return List.of();
    }

    /** Value paths per profile, for the completeness check. Profiles themselves carry no authority yet. */
    public JsonNode profiles() {
        JsonNode root = readJson("profiles.json");
        return root == null ? objectMapper.createObjectNode() : root.path("profiles");
    }

    /** True when a usable corpus exists - lets callers keep their previous behaviour when it does not. */
    public boolean isAvailable() {
        return readJson("capabilities.json") != null;
    }

    private JsonNode readJson(String fileName) {
        Path path = Paths.get(corpusRoot, fileName);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            Instant mtime = Files.getLastModifiedTime(path).toInstant();
            Cached cached = cache.get(fileName);
            if (cached != null && cached.mtime().equals(mtime)) {
                return cached.node();
            }
            JsonNode parsed = objectMapper.readTree(Files.readString(path));
            cache.put(fileName, new Cached(parsed, mtime));
            return parsed;
        } catch (IOException e) {
            // A corrupt or unreadable corpus must never take decomposition down with it: the flow keeps
            // working exactly as it did before the corpus existed, and the failure is visible in the log.
            log.warn("MarketCorpusService: could not read {}: {}", path, e.getMessage());
            return null;
        }
    }

    private record Cached(JsonNode node, Instant mtime) {
    }
}
