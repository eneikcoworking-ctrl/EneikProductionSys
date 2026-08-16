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

    /**
     * Statuses whose evidence is strong enough to change what gets built. See README.md's table.
     *
     * "derived" was added 2026-08-15 to fix a real defect in the original design, not to weaken it. The
     * first version admitted only measured or legal facts, which quietly conflated two different kinds of
     * claim: an empirical share of a market (what proportion of German shops offer purchase-on-invoice)
     * cannot be known without measuring it, but a structural claim about what a product of a given class
     * must contain to work at all (a shop needs a way to return goods; anything with accounts needs a way
     * back into a locked-out one) is domain reasoning, checkable by inspection and refutable by argument.
     *
     * Treating the second as if it were the first made the corpus structurally incapable of its own
     * purpose. The whole reason it exists is that clients do not know what software of their class must
     * contain and the factory does; ruling that expertise permanently inert left a corpus that could only
     * ever repeat legislation. The firewall that matters is against INVENTED NUMBERS, not against
     * reasoning - so a derived entry may state what is needed and why, and may never state a share, a
     * percentage or an effect size. Those still require measurement.
     */
    private static final List<String> INFLUENTIAL_STATUSES =
            List.of("statutory", "standard", "observed", "derived");

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
     * @param appliesWhenKeywords words that would appear in a plan if that condition actually holds - the
     *                            machine-checkable half of appliesWhen. Empty means unconditional.
     *                            <p>
     *                            Needed because scoping a duty by product kind alone is too coarse. A game
     *                            that sells nothing has no purchase duties, and reporting loot-box odds
     *                            against a classroom game with no purchases is exactly the obvious nonsense
     *                            that gets a check ignored. Note the deliberate asymmetry with profiles:
     *                            failing to detect a PROFILE means the plan could not be classified, so
     *                            every duty still applies, whereas failing to detect a CONDITION means the
     *                            plan does not describe building the thing the duty is about - and a plan
     *                            that builds no purchase flow cannot owe anything about purchase flows.
     * @param detectionKeywords words showing THIS duty is addressed, overriding the capability's own list.
     *                          One capability can carry several duties that are covered by different words:
     *                          disclosing loot-box odds and confirming a purchase deliberately both live
     *                          under purchase transparency, but a plan mentioning confirmation has not
     *                          thereby disclosed any odds. With one shared list the easiest duty to satisfy
     *                          silently marks the hardest one covered.
     */
    public record Expectation(String capabilityId, String requirement, String kano, String market,
                              String status, String source, String note,
                              List<String> appliesToProfiles, String appliesWhen,
                              List<String> appliesWhenKeywords, List<String> detectionKeywords) {
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
                if (hasExpired(expectation, status)) {
                    continue;
                }
                List<String> profiles = new ArrayList<>();
                for (JsonNode p : expectation.path("appliesToProfiles")) {
                    profiles.add(p.asText());
                }
                // The condition may be stated on the expectation or inherited from its capability, so that
                // a capability-wide condition does not have to be repeated on every entry under it.
                List<String> conditionWords = new ArrayList<>();
                JsonNode declared = expectation.has("appliesWhenKeywords")
                        ? expectation.path("appliesWhenKeywords")
                        : capability.path("appliesWhenKeywords");
                for (JsonNode w : declared) {
                    String word = w.asText("");
                    if (!word.isBlank()) {
                        conditionWords.add(word);
                    }
                }
                List<String> coverageWords = new ArrayList<>();
                for (JsonNode w : expectation.path("detectionKeywords")) {
                    String word = w.asText("");
                    if (!word.isBlank()) {
                        coverageWords.add(word);
                    }
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
                        capability.path("appliesWhen").asText(""),
                        conditionWords,
                        coverageWords
                ));
            }
        }
        return result;
    }

    /**
     * True when a market observation has outlived its own stated shelf life.
     *
     * Kano observed that attributes decay: what delights becomes expected, then mandatory. The corpus
     * already recorded WHEN each observation was made, but a date alone changes nothing - a share measured
     * two years ago went on steering decisions with exactly the force of one measured yesterday. Since
     * expectations now move within a year or two rather than a decade, that is the difference between a
     * corpus and a folklore that happens to carry dates.
     *
     * So an "observed" entry may declare validUntil, and past that date it stops influencing anything and
     * reverts to being merely stored - the same standing as a hypothesis - until someone re-measures it.
     * Evidence expiring is not a failure state: it is the corpus refusing to pretend it still knows.
     *
     * Deliberately NOT applied to statutory or standard entries. A law does not lapse because nobody
     * re-read it; it lapses when it is repealed, which is an edit to the corpus, not a timeout. Applying a
     * shelf life there would silently drop real legal duties for the sole reason that no one revisited the
     * file - the exact opposite of what this mechanism is for.
     */
    private boolean hasExpired(JsonNode expectation, String status) {
        if (!"observed".equals(status)) {
            return false;
        }
        String validUntil = expectation.path("validUntil").asText("");
        if (validUntil.isBlank()) {
            return false;
        }
        try {
            if (java.time.LocalDate.parse(validUntil).isBefore(java.time.LocalDate.now())) {
                log.info("MarketCorpusService: observation for '{}' expired on {} - it no longer influences "
                        + "decisions until re-measured", expectation.path("requirement").asText(""), validUntil);
                return true;
            }
            return false;
        } catch (java.time.format.DateTimeParseException e) {
            // An unparseable date must not silently grant an entry immortality: the safe reading of a
            // malformed shelf life is that we do not know it is still valid.
            log.warn("MarketCorpusService: unparseable validUntil '{}' - treating the observation as expired",
                    validUntil);
            return true;
        }
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

    /**
     * Which product profiles a piece of plan text actually shows evidence of, judged by the profiles' own
     * detectionKeywords - the same evidential method used for capability coverage, for the same reason.
     *
     * An empty result means "no evidence either way", NOT "no profile applies", and callers must treat the
     * two differently: narrowing scope on the strength of absent evidence is precisely how an obligation
     * goes missing. It exists because most statutory duties are scoped to a kind of product, and without
     * this a game's age-rating duty would be reported against every shop plan and a shop's withdrawal-rights
     * duty against every internal tool. A check that reports obvious nonsense is a check people stop reading,
     * which costs more than the duty it was meant to catch.
     */
    public List<String> profilesInEvidence(String text) {
        List<String> matched = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return matched;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        for (JsonNode profile : profiles()) {
            for (JsonNode keyword : profile.path("detectionKeywords")) {
                String needle = keyword.asText("").toLowerCase(Locale.ROOT);
                // A blank keyword must never be tested: it would match every text, so a single empty entry
                // in the corpus would match every profile against every plan.
                if (needle.isBlank()) {
                    continue;
                }
                if (mentions(haystack, needle)) {
                    matched.add(profile.path("id").asText(""));
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * Does this text MENTION the keyword, as a word rather than as a fragment of one?
     *
     * 2026-08-16. Every detection in this package used {@code haystack.contains(keyword)}, and the corpus
     * keyword "shop" is a substring of "workshop": a company that runs workshops was classified as an
     * online shop and handed the payment and withdrawal-rights duties that follow. "cart" sits inside
     * "cartography" the same way. That is the fourth appearance this week of one defect - a substring test
     * standing in for a word test - after looksLikeUi (Step 7), EmsMetricsService's "auth" matching
     * "author" (F34), and the compiler's own keyword scan.
     *
     * The rule is exact: a word boundary on both sides, with the common English inflections allowed, so
     * "shopping carts" still matches "cart" while "workshop" and "cartography" do not. Inflection is
     * enumerated rather than approximated by a prefix rule, because a prefix rule is what let "auth" match
     * "author" - and an approximation that fails silently is what this whole class exists to stop.
     *
     * Cached per keyword: this runs over every capability keyword for every plan, and Pattern.compile on a
     * hot path was the kind of quiet cost that gets blamed on something else later.
     */
    public static boolean mentions(String haystack, String keyword) {
        if (haystack == null || haystack.isBlank() || keyword == null || keyword.isBlank()) {
            return false;
        }
        return KEYWORD_PATTERNS
                .computeIfAbsent(keyword.trim().toLowerCase(Locale.ROOT), MarketCorpusService::compileKeyword)
                .matcher(haystack)
                .find();
    }

    private static final ConcurrentHashMap<String, java.util.regex.Pattern> KEYWORD_PATTERNS =
            new ConcurrentHashMap<>();

    /**
     * {@code \bKEYWORD(<doubled final letter>)?(s|es|ing|ed)?\b}.
     *
     * The doubled letter is not decoration: English doubles a final consonant before a suffix, so "shop"
     * becomes "shopping" and a rule built only from the plain suffixes silently stopped matching it. That
     * showed up as a failing test rather than as a wrong classification months later, which is the whole
     * reason the inflections are enumerated instead of approximated - an approximation fails quietly.
     *
     * It is the keyword's OWN last letter that may repeat, never an arbitrary character. "cart" therefore
     * admits "cartt", which no English word is, and still refuses "cartography" because the boundary must
     * close. A wildcard here would have re-opened exactly the hole being closed.
     */
    private static java.util.regex.Pattern compileKeyword(String keyword) {
        String quoted = java.util.regex.Pattern.quote(keyword);
        char last = keyword.charAt(keyword.length() - 1);
        String doubled = Character.isLetter(last)
                ? "(" + java.util.regex.Pattern.quote(String.valueOf(last)) + ")?"
                : "";
        return java.util.regex.Pattern.compile(
                "\\b" + quoted + doubled + "(s|es|ing|ed)?\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    /** Value paths per profile, for the completeness check. Profiles themselves carry no authority yet. */
    public JsonNode profiles() {
        JsonNode root = readJson("profiles.json");
        return root == null ? objectMapper.createObjectNode() : root.path("profiles");
    }

    /**
     * The one acceptance rule (schema v3), deliberately NOT a seventeenth profile chain.
     *
     * Every valuePath states what must be POSSIBLE for the end user. None of them states that anything was
     * ever actually done, by the buyer, on the deployed instance - so the factory could reach DELIVERED on
     * merge counts, which is a claim about what was built rather than about what was shown. The rule is the
     * existing chains under a change of quantifier, which is why one entry covers all sixteen profiles: a
     * chain per profile would be sixteen copies of one idea, and they would drift.
     *
     * Missing node rather than empty object when absent, so a caller can tell "the corpus does not declare
     * this" from "it declares nothing" - the same distinction the whole lattice rests on.
     */
    public JsonNode acceptanceRule() {
        JsonNode root = readJson("profiles.json");
        return root == null ? objectMapper.missingNode() : root.path("acceptanceRule");
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
