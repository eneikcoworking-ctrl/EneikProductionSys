package com.eneik.production.services.design;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BARCAN-TAG-11 CLIENT-PERCEPTION's E(f)/E*(F) predicates, made executable: a screen's visual
 * language (colors, fonts) either traces back to its project's declared design system or it does
 * not, and a set of screens generated under one design-system id either reads as one visual
 * language or it does not. Confirmed live 2026-08-10: three Forge screens generated under the same
 * designSystemId came back in three incompatible color registers - passing the id alone does not
 * guarantee consistency, so this exists to catch that automatically instead of relying on an
 * operator noticing by eye.
 */
@Service
public class DesignConsistencyAuditService {

    public static final double MIN_TRACE_RATIO = 0.9;
    public static final double MIN_CROSS_SCREEN_JACCARD = 0.5;

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern RGB_COLOR = Pattern.compile("rgba?\\([^)]*\\)");
    // Deliberately allows quotes inside the captured group - font names are commonly quoted
    // ('Libre Caslon Text') and the surrounding quote characters are stripped per-token in
    // normalizeFont, not excluded from the match itself (excluding them truncates the capture at
    // the opening quote, leaving nothing to split on commas - caught live by this class's own test).
    private static final Pattern FONT_FAMILY = Pattern.compile("font-family\\s*:\\s*([^;}]+)");

    public record TokenSet(Set<String> colors, Set<String> fonts) {
        public static TokenSet of(List<String> colors, List<String> fonts) {
            return new TokenSet(normalizeColors(colors), normalizeFonts(fonts));
        }

        Set<String> all() {
            Set<String> combined = new LinkedHashSet<>(colors);
            combined.addAll(fonts);
            return combined;
        }
    }

    public enum AuditVerdict {
        ACCEPTED("accepted", "принято"),
        REJECTED("rejected", "отвергнуто"),
        CANNOT_JUDGE("cannot_judge", "не могу судить");

        public static final AuditVerdict UNDECIDABLE = CANNOT_JUDGE;

        private final String code;
        private final String displayName;

        AuditVerdict(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isAccepted() {
            return this == ACCEPTED;
        }

        public boolean isRejected() {
            return this == REJECTED;
        }

        public boolean isCannotJudge() {
            return this == CANNOT_JUDGE;
        }

        public boolean isUndecidable() {
            return this == CANNOT_JUDGE;
        }
    }

    public record ConsistencyReport(double traceRatio, boolean traceAccepted,
                                     double avgCrossScreenJaccard, boolean crossScreenAccepted,
                                     Set<String> offTokenValues,
                                     Set<String> declaredTokens,
                                     Set<String> producerTokens,
                                     AuditVerdict verdict,
                                     String verdictReason) {
        public ConsistencyReport(double traceRatio, boolean traceAccepted,
                                 double avgCrossScreenJaccard, boolean crossScreenAccepted,
                                 Set<String> offTokenValues) {
            this(traceRatio, traceAccepted, avgCrossScreenJaccard, crossScreenAccepted, offTokenValues, Set.of(), Set.of(),
                    traceAccepted ? AuditVerdict.ACCEPTED : AuditVerdict.REJECTED,
                    traceAccepted ? "принято" : "отвергнуто: token_trace_ratio ниже требуемого");
        }

        public ConsistencyReport(double traceRatio, boolean traceAccepted,
                                 double avgCrossScreenJaccard, boolean crossScreenAccepted,
                                 Set<String> offTokenValues,
                                 Set<String> declaredTokens,
                                 Set<String> producerTokens) {
            this(traceRatio, traceAccepted, avgCrossScreenJaccard, crossScreenAccepted, offTokenValues, declaredTokens, producerTokens,
                    traceAccepted ? AuditVerdict.ACCEPTED : AuditVerdict.REJECTED,
                    traceAccepted ? "принято" : "отвергнуто: token_trace_ratio ниже требуемого");
        }

        public boolean isCannotJudge() {
            return verdict == AuditVerdict.CANNOT_JUDGE;
        }

        public boolean isUndecidable() {
            return isCannotJudge();
        }

        public String displayVerdict() {
            return verdict != null ? verdict.getDisplayName() : (traceAccepted ? "принято" : "отвергнуто");
        }
    }

    /** Extracts the distinct colors and font-family values actually present in a screen's HTML/CSS. */
    public TokenSet extractUsedTokens(String html) {
        if (html == null || html.isBlank()) {
            return new TokenSet(Set.of(), Set.of());
        }
        Set<String> colors = new LinkedHashSet<>();
        Matcher hexMatcher = HEX_COLOR.matcher(html);
        while (hexMatcher.find()) {
            colors.add(normalizeColor(hexMatcher.group()));
        }
        Matcher rgbMatcher = RGB_COLOR.matcher(html);
        while (rgbMatcher.find()) {
            colors.add(normalizeColor(rgbMatcher.group()));
        }
        Set<String> fonts = new LinkedHashSet<>();
        Matcher fontMatcher = FONT_FAMILY.matcher(html);
        while (fontMatcher.find()) {
            for (String part : fontMatcher.group(1).split(",")) {
                fonts.add(normalizeFont(part));
            }
        }
        return new TokenSet(colors, fonts);
    }

    /**
     * E(f): TraceRatio(f) = |Used(f) ∩ Tokens(f)| / |Used(f)|.
     * Empty Used(f) or empty Tokens(f) yields 0.0 - cannot judge / no tokens trace back.
     * See Prescription 28 (FALSIFICATION_HARNESS / D008 + LEVEL_OF_ABSTRACTION_LOCK / D010).
     */
    public double traceRatio(TokenSet used, TokenSet declared) {
        Set<String> usedAll = used != null ? used.all() : Set.of();
        if (usedAll.isEmpty()) {
            return 0.0;
        }
        if (declared == null) {
            return 0.0;
        }
        Set<String> declaredAll = declared.all();
        if (declaredAll.isEmpty()) {
            return 0.0;
        }
        long intersecting = usedAll.stream().filter(declaredAll::contains).count();
        return (double) intersecting / usedAll.size();
    }

    /** Jaccard(f_i, f_j) = |Used(f_i) ∩ Used(f_j)| / |Used(f_i) ∪ Used(f_j)|. Two empty sets are trivially identical (1.0). */
    public double jaccard(TokenSet a, TokenSet b) {
        Set<String> setA = a.all();
        Set<String> setB = b.all();
        if (setA.isEmpty() && setB.isEmpty()) {
            return 1.0;
        }
        Set<String> union = new LinkedHashSet<>(setA);
        union.addAll(setB);
        long intersectionSize = setA.stream().filter(setB::contains).count();
        return union.isEmpty() ? 1.0 : (double) intersectionSize / union.size();
    }

    /**
     * Full audit for one screen against its declared design system, plus its pairwise similarity to
     * sibling screens from the same design-system session (may be empty if this is the first screen).
     */
    public ConsistencyReport audit(String html, TokenSet declaredTokens, List<String> siblingHtmlDrafts) {
        return audit(html, declaredTokens, siblingHtmlDrafts, null);
    }

    public ConsistencyReport audit(String html, TokenSet declaredTokens, List<String> siblingHtmlDrafts, TokenSet producerTokens) {
        TokenSet used = extractUsedTokens(html);
        Set<String> declaredAll = declaredTokens != null ? declaredTokens.all() : Set.of();
        Set<String> producerAll = producerTokens != null ? producerTokens.all() : Set.of();

        // Level of Abstraction / Truth Status Table (Prescription 28: D010 + D012):
        // 1. If the delivered HTML has no visual tokens (e.g. SPA skeleton/shell without inline styles),
        //    the static server response cannot be judged against the design system at this abstraction layer.
        //    Returning 1.0 or true would be a false green (D008); returning "0 tokens / rejected" would be a category error (D002).
        //    The outcome is strictly CANNOT_JUDGE ("не могу судить").
        if (used.all().isEmpty()) {
            return new ConsistencyReport(
                    0.0, false, 0.0, false,
                    Set.of(), declaredAll, producerAll,
                    AuditVerdict.CANNOT_JUDGE,
                    "не могу судить: нет визуальных токенов в HTML/CSS (HTML-оболочка SPA без стилей)"
            );
        }

        // 2. If no design system baseline was declared, no comparison can take place.
        if (declaredAll.isEmpty()) {
            return new ConsistencyReport(
                    0.0, false, 0.0, false,
                    Set.of(), declaredAll, producerAll,
                    AuditVerdict.CANNOT_JUDGE,
                    "не могу судить: отсутствует эталон дизайн-системы"
            );
        }

        double trace = traceRatio(used, declaredTokens);
        Set<String> offToken = new LinkedHashSet<>(used.all());
        offToken.removeAll(declaredAll);

        double avgJaccard = 1.0;
        if (siblingHtmlDrafts != null && !siblingHtmlDrafts.isEmpty()) {
            double sum = 0.0;
            for (String siblingHtml : siblingHtmlDrafts) {
                sum += jaccard(used, extractUsedTokens(siblingHtml));
            }
            avgJaccard = sum / siblingHtmlDrafts.size();
        }

        boolean traceAccepted = trace >= MIN_TRACE_RATIO;
        boolean crossScreenAccepted = avgJaccard >= MIN_CROSS_SCREEN_JACCARD;
        AuditVerdict verdict = traceAccepted ? AuditVerdict.ACCEPTED : AuditVerdict.REJECTED;
        String reason = traceAccepted
                ? "принято: visual language traces to declared tokens (traceRatio=" + trace + ")"
                : "отвергнуто: token_trace_ratio=" + trace + " below required " + MIN_TRACE_RATIO;

        return new ConsistencyReport(
                trace, traceAccepted,
                avgJaccard, crossScreenAccepted,
                offToken, declaredAll, producerAll,
                verdict, reason
        );
    }

    private static String normalizeColor(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String normalizeFont(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("['\"]", "");
    }

    private static Set<String> normalizeColors(List<String> colors) {
        Set<String> result = new LinkedHashSet<>();
        for (String color : colors) {
            result.add(normalizeColor(color));
        }
        return result;
    }

    private static Set<String> normalizeFonts(List<String> fonts) {
        Set<String> result = new LinkedHashSet<>();
        for (String font : fonts) {
            result.add(normalizeFont(font));
        }
        return result;
    }
}
