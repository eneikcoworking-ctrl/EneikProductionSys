package com.eneik.production.services;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts the two classification axes the epistemic entrenchment formula needs - Cynefin domain and Kano
 * class - from the free text a wishlist item actually carries, for the compile path that has no explicitly
 * classified эпик content to read (FeatureService.resolveOrCreateFeatureId).
 *
 * <p>Why this exists at all (2026-08-27, Phase 1 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md): that path used
 * to call {@code calculateEpistemicEntrenchment(null, null)}, and the null-defaults inside the formula
 * ({@code complex}/{@code one-dimensional}) made every feature minted by the main compile path score the
 * same constant. A constant score is not a preorder: AGM contraction had nothing to order beliefs by, and
 * {@code EpistemicLayerInvariantGate} - which only inspects PERIPHERY features - could never fire, because
 * the constant landed just above the CONTRACT threshold.
 *
 * <p>Deterministic keyword scoring, not an LLM call and not an embedding lookup: this runs on every task
 * compiled, it must be reproducible from the stored row, and it must cost nothing. The keyword sets are
 * bilingual (English and Russian) because client wishes in this factory are measured to arrive in both -
 * an English-only matcher would silently classify every Russian wish as unextractable.
 *
 * <p>The honest part: "no keyword matched" returns null for that axis rather than guessing a middle value.
 * A null is what makes the feature score low and land in PERIPHERY, which is the correct treatment for a
 * feature whose domain the factory genuinely does not know - the exact case that used to be dressed up as
 * "complex/one-dimensional" and scored as if it had been classified.
 */
@Service
public class EpistemicMetadataClassifier {

    /**
     * Both axes are nullable, independently: text may pin the Cynefin domain and say nothing about Kano,
     * or the reverse. A null means "not extractable", never "middle of the range".
     */
    public record Classification(String cynefinDomain, String kanoClass) {
        public boolean anythingExtracted() {
            return cynefinDomain != null || kanoClass != null;
        }
    }

    // Ordered most-severe-first: on a tie in hit count, the lower-certainty domain wins, because
    // over-stating how well-understood a piece of work is, is the failure mode this whole phase exists to
    // remove. LinkedHashMap keeps that order explicit rather than incidental.
    private static final Map<String, List<String>> CYNEFIN_MARKERS = new LinkedHashMap<>();
    private static final Map<String, List<String>> KANO_MARKERS = new LinkedHashMap<>();

    static {
        CYNEFIN_MARKERS.put("chaotic", List.of(
                "outage", "incident", "hotfix", "urgent", "critical failure", "data loss", "crash",
                "авари", "срочно", "инцидент", "сломан", "падает", "утеч", "потеря данных"));
        CYNEFIN_MARKERS.put("complex", List.of(
                "research", "experiment", "prototype", "spike", "explore", "investigate", "recommendation",
                "machine learning", "recommender", "heuristic", "unknown",
                "исследов", "эксперимент", "прототип", "разведк", "рекомендатель", "машинн", "неизвестн"));
        CYNEFIN_MARKERS.put("complicated", List.of(
                "integrat", "api", "migration", "refactor", "architecture", "pipeline", "authentication",
                "authorization", "scaling", "concurren", "transaction", "schema",
                "интеграц", "миграц", "рефактор", "архитектур", "конвейер", "аутентифик", "авторизац",
                "масштаб", "транзакц", "схем"));
        CYNEFIN_MARKERS.put("clear", List.of(
                "crud", "form", "button", "page", "listing", "layout", "styling", "copy text", "label",
                "static", "landing",
                "форм", "кнопк", "страниц", "список", "вёрстк", "верстк", "стил", "лендинг", "статичн"));

        KANO_MARKERS.put("must-be", List.of(
                "login", "sign in", "auth", "security", "password", "payment", "checkout", "backup",
                "gdpr", "compliance", "audit trail", "error handling", "validation", "access control",
                "вход", "авториз", "аутентифик", "безопасн", "парол", "оплат", "платеж", "резервн",
                "соответстви", "валидац", "обработка ошибок", "доступ"));
        KANO_MARKERS.put("one-dimensional", List.of(
                "performance", "latency", "speed", "search", "filter", "sort", "report", "export",
                "pagination", "throughput",
                "производительн", "скорост", "задержк", "поиск", "фильтр", "сортиров", "отчет", "отчёт",
                "экспорт", "пагинац", "пропускн"));
        KANO_MARKERS.put("attractive", List.of(
                "recommend", "personaliz", "animation", "dashboard", "notification", "gamif", "theme",
                "dark mode", "onboarding tour", "delight",
                "рекоменд", "персонализ", "анимац", "дашборд", "уведомлен", "геймиф", "тем оформлен",
                "тёмная тема", "темная тема"));
        KANO_MARKERS.put("indifferent", List.of(
                "internal note", "housekeeping", "cleanup", "cosmetic", "rename",
                "внутренн замет", "уборк", "косметич", "переименов"));
    }

    /**
     * Classifies the concatenation of whatever text fragments the caller has (content, JTBD, acceptance
     * criteria, ...). Nulls and blanks among them are simply skipped, so a caller never has to pre-filter.
     */
    public Classification classify(String... textFragments) {
        String haystack = normalize(textFragments);
        if (haystack.isEmpty()) {
            return new Classification(null, null);
        }
        return new Classification(bestMatch(CYNEFIN_MARKERS, haystack), bestMatch(KANO_MARKERS, haystack));
    }

    private String normalize(String[] textFragments) {
        if (textFragments == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String fragment : textFragments) {
            if (fragment != null && !fragment.isBlank()) {
                sb.append(' ').append(fragment);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Highest distinct-marker count wins; a tie goes to whichever category is declared first, which is the
     * more conservative one (see CYNEFIN_MARKERS' comment). Zero matches anywhere returns null.
     */
    private String bestMatch(Map<String, List<String>> markers, String haystack) {
        String best = null;
        int bestHits = 0;
        for (Map.Entry<String, List<String>> entry : markers.entrySet()) {
            int hits = 0;
            for (String marker : entry.getValue()) {
                if (haystack.contains(marker)) {
                    hits++;
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = entry.getKey();
            }
        }
        return best;
    }
}
