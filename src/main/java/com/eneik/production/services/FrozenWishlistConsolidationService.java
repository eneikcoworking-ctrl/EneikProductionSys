package com.eneik.production.services;

import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.WishlistRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WO #14 / Mechanism #16: Non-destructive accumulation and consolidation of wishlists on project unfreeze.
 *
 * <p>Grounded in:
 * <ul>
 *   <li>{@code ELVIN_GOLDMAN_01_RELIABILITY_CHAIN} (D010 Data lineage loss): every consolidated pending
 *       wishlist carries {@code consolidated_from_ids} and an explicit lineage block with source UUIDs,
 *       sources/roles, and {@code created_at}; absorbed originals are preserved in
 *       {@code WishlistStatus.consolidated} with {@code merged_into_wishlist_id}.</li>
 *   <li>{@code NUEL_BELNAP_03_TRUTH_STATUS_TABLE} (D012 Policy contradiction / D002 Invalid state):
 *       explicit states ({@code deferred_frozen}, {@code consolidated}, {@code pending}) and explicit
 *       Belnap {@code BOTH} conflict preservation ({@code consolidationConflict = true}, both sides
 *       and IDs quoted under {@code [CONFLICT DETECTED — REQUIRES RESOLUTION]}).</li>
 * </ul>
 *
 * <p>Runs strictly inside a short DB transaction with zero network I/O (HikariPool-safe).
 */
@Service
public class FrozenWishlistConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(FrozenWishlistConsolidationService.class);

    private static final double JACCARD_THRESHOLD = 0.25;
    private static final double OVERLAP_THRESHOLD = 0.50;

    private static final Set<String> STOPWORDS = Set.of(
            // English
            "the", "and", "for", "with", "from", "into", "that", "this", "must", "should", "will",
            "when", "then", "given", "want", "need", "have", "has", "are", "was", "were", "been",
            "not", "all", "any", "only", "very", "fully", "completely", "also", "more", "less",
            "item", "items", "wishlist", "requirement", "requirements", "task", "tasks",
            // Russian
            "для", "что", "как", "или", "при", "под", "над", "без", "про", "все", "всё", "всех",
            "это", "этот", "эта", "эти", "его", "её", "их", "надо", "нужно", "должен", "должна",
            "должны", "быть", "будет", "полностью", "совсем", "только", "также", "еще", "ещё",
            "пожелание", "требование", "задача", "задачи"
    );

    // Multi-word negative phrases checked BEFORE single-word positive markers so "не использовать X"
    // or "do not enable X" is classified strictly as NEGATIVE (never falsely POSITIVE).
    private static final List<String> NEGATIVE_PHRASES = List.of(
            "не использовать", "не добавлять", "не включать", "не разрешать", "не оставлять",
            "не хранить", "не показывать", "не выводить", "не применять", "отказаться от",
            "do not use", "don't use", "do not add", "don't add", "do not enable", "don't enable",
            "do not allow", "don't allow", "do not keep", "don't keep", "must not", "never use",
            "turn off", "switch off", "get rid of", "drop support"
    );

    private static final Set<String> NEGATIVE_TOKENS = Set.of(
            // Russian
            "удалить", "удали", "удаление", "убрать", "убери", "отключить", "отключи", "отключение",
            "выключить", "выключи", "запретить", "запрети", "запрет", "исключить", "исключи",
            "деактивировать", "избавиться", "уничтожить", "упразднить", "снести", "выпилить",
            // English
            "remove", "delete", "disable", "forbid", "prohibit", "drop", "exclude", "ban",
            "deactivate", "eliminate", "strip", "purge", "deprecate", "decommission"
    );

    private static final List<String> POSITIVE_PHRASES = List.of(
            "turn on", "switch on", "must have", "must support", "keep using", "continue using"
    );

    private static final Set<String> POSITIVE_TOKENS = Set.of(
            // Russian
            "добавить", "добавь", "добавление", "включить", "включи", "включение", "разрешить",
            "разреши", "оставить", "оставь", "сохранить", "сохрани", "использовать", "используй",
            "подключить", "подключи", "активировать", "активируй", "вернуть", "реализовать",
            "внедрить", "поддерживать", "поддержать",
            // English
            "add", "enable", "allow", "keep", "retain", "use", "include", "activate",
            "implement", "require", "support", "introduce", "restore", "preserve"
    );

    private static final Pattern KEY_VALUE_PARAM_PATTERN = Pattern.compile(
            "\\b(port|timeout|limit|max|min|ttl|replicas|threads|workers|interval|порт|таймаут|лимит)\\s*[:=]?\\s*(\\d+)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final WishlistRepository wishlistRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public FrozenWishlistConsolidationService(WishlistRepository wishlistRepository, ObjectMapper objectMapper) {
        this.wishlistRepository = wishlistRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public FrozenWishlistConsolidationService(WishlistRepository wishlistRepository) {
        this(wishlistRepository, new ObjectMapper());
    }

    public record ConsolidationReport(
            int candidateCount,
            int consolidatedGroupCount,
            int conflictGroupCount,
            List<UUID> consolidatedWishlistIds
    ) {
        public static ConsolidationReport noOp() {
            return new ConsolidationReport(0, 0, 0, List.of());
        }
    }

    enum Polarity {
        POSITIVE, NEGATIVE, NEUTRAL
    }

    record ConflictPair(WishlistEntity sideA, WishlistEntity sideB, String reason) {}

    /**
     * Safety-net entry point invoked by {@code ProjectFlowService.executeOrchestrate} strictly before
     * any decomposition. Triggers consolidation only if {@code deferred_frozen} items are present, so
     * ordinary active-project {@code pending} wishlists are never unnecessarily re-wrapped.
     */
    @Transactional
    public ConsolidationReport consolidateDeferredBeforeOrchestrate(UUID projectId) {
        if (projectId == null) {
            return ConsolidationReport.noOp();
        }
        long deferredCount = wishlistRepository.countByProjectIdAndStatus(projectId, WishlistStatus.deferred_frozen);
        if (deferredCount <= 0) {
            return ConsolidationReport.noOp();
        }
        return consolidateForProject(projectId);
    }

    /**
     * Consolidates all {@code deferred_frozen} wishlists and any uncompiled root {@code pending}
     * wishlists ({@code originWishlistId == null}, {@code mergedIntoWishlistId == null},
     * {@code consolidatedFromIds == null}) for a project on unfreeze.
     *
     * <p>Strictly idempotent: re-running after consolidation finds 0 candidates and returns no-op.
     */
    @Transactional
    public ConsolidationReport consolidateForProject(UUID projectId) {
        if (projectId == null) {
            return ConsolidationReport.noOp();
        }
        List<WishlistEntity> all = wishlistRepository.findByProjectId(projectId);
        if (all == null || all.isEmpty()) {
            return ConsolidationReport.noOp();
        }

        List<WishlistEntity> candidates = all.stream()
                .filter(this::isConsolidationCandidate)
                .sorted(Comparator
                        .comparing(WishlistEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(WishlistEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (candidates.isEmpty()) {
            return ConsolidationReport.noOp();
        }

        List<List<WishlistEntity>> groups = groupCandidates(candidates);
        List<UUID> createdIds = new ArrayList<>(groups.size());
        int conflictGroups = 0;

        for (List<WishlistEntity> group : groups) {
            List<ConflictPair> conflicts = detectConflicts(group);
            boolean hasConflict = !conflicts.isEmpty();
            if (hasConflict) {
                conflictGroups++;
            }

            WishlistEntity consolidated = buildConsolidatedWishlist(projectId, group, conflicts);
            consolidated = wishlistRepository.save(consolidated);
            createdIds.add(consolidated.getId());

            for (WishlistEntity original : group) {
                original.setStatus(WishlistStatus.consolidated);
                original.setMergedIntoWishlistId(consolidated.getId());
                wishlistRepository.save(original);
            }

            log.info("FrozenWishlistConsolidationService: project={} consolidated {} source wishlist(s) into {} (conflict={})",
                    projectId, group.size(), consolidated.getId(), hasConflict);
        }

        return new ConsolidationReport(candidates.size(), groups.size(), conflictGroups, List.copyOf(createdIds));
    }

    private boolean isConsolidationCandidate(WishlistEntity w) {
        if (w == null) {
            return false;
        }
        if (w.getMergedIntoWishlistId() != null) {
            return false;
        }
        if (w.getConsolidatedFromIds() != null && !w.getConsolidatedFromIds().isBlank()) {
            return false;
        }
        if (w.getStatus() == WishlistStatus.deferred_frozen) {
            return true;
        }
        return w.getStatus() == WishlistStatus.pending
                && w.getCompiledByRole() == null
                && w.getOriginWishlistId() == null;
    }

    private List<List<WishlistEntity>> groupCandidates(List<WishlistEntity> candidates) {
        int n = candidates.size();
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (int i = 0; i < n; i++) {
            adj.put(i, new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (areSemanticallyGroupable(candidates.get(i), candidates.get(j))) {
                    adj.get(i).add(j);
                    adj.get(j).add(i);
                }
            }
        }

        boolean[] visited = new boolean[n];
        List<List<WishlistEntity>> groups = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                List<WishlistEntity> component = new ArrayList<>();
                Queue<Integer> queue = new ArrayDeque<>();
                queue.add(i);
                visited[i] = true;
                while (!queue.isEmpty()) {
                    int curr = queue.poll();
                    component.add(candidates.get(curr));
                    for (int next : adj.get(curr)) {
                        if (!visited[next]) {
                            visited[next] = true;
                            queue.add(next);
                        }
                    }
                }
                component.sort(Comparator
                        .comparing(WishlistEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(WishlistEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                groups.add(component);
            }
        }
        return groups;
    }

    static boolean areSemanticallyGroupable(WishlistEntity a, WishlistEntity b) {
        // Hard guard 1: never merge distinct task-scoped findings
        UUID taskA = a.getSourceTaskId();
        UUID taskB = b.getSourceTaskId();
        if ((taskA != null || taskB != null) && !Objects.equals(taskA, taskB)) {
            return false;
        }
        // Hard guard 2: never merge sibling slices of the same already-compiled brief
        UUID originA = a.getOriginWishlistId();
        if (originA != null && originA.equals(b.getOriginWishlistId())) {
            return false;
        }
        // Hard guard 3: compatible TargetContext (UNDETERMINED is compatible with any)
        TargetContext ctxA = a.getTargetContext();
        TargetContext ctxB = b.getTargetContext();
        if (ctxA != null && ctxB != null
                && ctxA != TargetContext.UNDETERMINED
                && ctxB != TargetContext.UNDETERMINED
                && ctxA != ctxB) {
            return false;
        }

        String textA = a.getContent();
        String textB = b.getContent();
        if (textA == null || textB == null || textA.isBlank() || textB.isBlank()) {
            return false;
        }

        Set<String> topicA = extractTopicTokens(textA);
        Set<String> topicB = extractTopicTokens(textB);
        if (topicA.isEmpty() || topicB.isEmpty()) {
            return false;
        }

        Set<String> intersection = new HashSet<>(topicA);
        intersection.retainAll(topicB);
        if (intersection.isEmpty()) {
            return false;
        }

        Set<String> union = new HashSet<>(topicA);
        union.addAll(topicB);
        double jaccard = (double) intersection.size() / union.size();
        int minSize = Math.min(topicA.size(), topicB.size());
        double overlap = (double) intersection.size() / minSize;

        if (jaccard >= JACCARD_THRESHOLD) {
            return true;
        }
        if (overlap >= OVERLAP_THRESHOLD && intersection.size() >= 2) {
            return true;
        }
        // Short polar directives about the same subject (e.g., "включить X" vs "полностью отключить и удалить X")
        Polarity polA = classifyPolarity(textA);
        Polarity polB = classifyPolarity(textB);
        if (isOppositePolarity(polA, polB) && overlap >= OVERLAP_THRESHOLD) {
            return true;
        }
        return false;
    }

    static List<ConflictPair> detectConflicts(List<WishlistEntity> group) {
        if (group == null || group.size() < 2) {
            return List.of();
        }
        List<ConflictPair> conflicts = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                WishlistEntity a = group.get(i);
                WishlistEntity b = group.get(j);
                String reason = detectPairConflictReason(a.getContent(), b.getContent());
                if (reason != null) {
                    conflicts.add(new ConflictPair(a, b, reason));
                }
            }
        }
        return conflicts;
    }

    static String detectPairConflictReason(String textA, String textB) {
        if (textA == null || textB == null) {
            return null;
        }
        Polarity polA = classifyPolarity(textA);
        Polarity polB = classifyPolarity(textB);
        if (isOppositePolarity(polA, polB)) {
            Set<String> topicA = extractTopicTokens(textA);
            Set<String> topicB = extractTopicTokens(textB);
            topicA.retainAll(topicB);
            if (!topicA.isEmpty()) {
                return "Polar directive contradiction (" + polA + " vs " + polB + ") on shared subject " + topicA;
            }
        }

        Map<String, String> paramsA = extractNumericParams(textA);
        Map<String, String> paramsB = extractNumericParams(textB);
        for (Map.Entry<String, String> entry : paramsA.entrySet()) {
            String key = entry.getKey();
            if (paramsB.containsKey(key) && !paramsB.get(key).equals(entry.getValue())) {
                return "Mutually exclusive numeric target for '" + key + "': "
                        + entry.getValue() + " vs " + paramsB.get(key);
            }
        }
        return null;
    }

    static Polarity classifyPolarity(String text) {
        if (text == null || text.isBlank()) {
            return Polarity.NEUTRAL;
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        String masked = lower;
        boolean hasNegative = false;
        boolean hasPositive = false;

        for (String phrase : NEGATIVE_PHRASES) {
            if (masked.contains(phrase)) {
                hasNegative = true;
                masked = masked.replace(phrase, " ");
            }
        }
        for (String phrase : POSITIVE_PHRASES) {
            if (masked.contains(phrase)) {
                hasPositive = true;
                masked = masked.replace(phrase, " ");
            }
        }

        String[] words = masked.replaceAll("[^a-zа-я0-9\\s]", " ").split("\\s+");
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (NEGATIVE_TOKENS.contains(word)) {
                hasNegative = true;
            } else if (POSITIVE_TOKENS.contains(word)) {
                hasPositive = true;
            }
        }

        if (hasNegative && !hasPositive) {
            return Polarity.NEGATIVE;
        }
        if (hasPositive && !hasNegative) {
            return Polarity.POSITIVE;
        }
        // If a single text contains both negative and positive verbs (e.g. "отключить и удалить X"),
        // both are negative so hasPositive is false. If it genuinely mixes both (e.g. "убрать A и добавить B"),
        // treat as NEUTRAL at whole-text level unless numeric conflict fires.
        return Polarity.NEUTRAL;
    }

    private static boolean isOppositePolarity(Polarity a, Polarity b) {
        return (a == Polarity.POSITIVE && b == Polarity.NEGATIVE)
                || (a == Polarity.NEGATIVE && b == Polarity.POSITIVE);
    }

    static Set<String> extractTopicTokens(String text) {
        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (String phrase : NEGATIVE_PHRASES) {
            lower = lower.replace(phrase, " ");
        }
        for (String phrase : POSITIVE_PHRASES) {
            lower = lower.replace(phrase, " ");
        }
        String cleaned = lower.replaceAll("[^a-zа-я0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        Set<String> tokens = new HashSet<>();
        if (cleaned.isEmpty()) {
            return tokens;
        }
        String[] rawTokens = cleaned.split(" ");
        for (String raw : rawTokens) {
            if (raw.isBlank()) {
                continue;
            }
            if (STOPWORDS.contains(raw) || NEGATIVE_TOKENS.contains(raw) || POSITIVE_TOKENS.contains(raw)) {
                continue;
            }
            String stem = normalizeToken(raw);
            if (stem.length() >= 2 || isShortIdentifier(raw, rawTokens.length)) {
                tokens.add(stem);
            }
        }
        return tokens;
    }

    private static boolean isShortIdentifier(String token, int totalTokens) {
        // Keep single-char subject identifiers like "X" in short directives ("включить X" / "удалить X")
        return token.length() == 1 && totalTokens <= 8 && Character.isLetterOrDigit(token.charAt(0));
    }

    private static String normalizeToken(String token) {
        if (token.endsWith("s") && token.length() > 3 && token.matches("[a-z]+")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static Map<String, String> extractNumericParams(String text) {
        Map<String, String> params = new HashMap<>();
        Matcher matcher = KEY_VALUE_PARAM_PATTERN.matcher(text);
        while (matcher.find()) {
            params.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
        }
        return params;
    }

    private WishlistEntity buildConsolidatedWishlist(UUID projectId,
                                                     List<WishlistEntity> group,
                                                     List<ConflictPair> conflicts) {
        WishlistEntity consolidated = new WishlistEntity();
        consolidated.setProjectId(projectId);
        consolidated.setStatus(WishlistStatus.pending);

        boolean hasClient = group.stream().anyMatch(w -> w.getSource() == WishlistSource.client);
        if (hasClient) {
            consolidated.setSource(WishlistSource.client);
            consolidated.setSourceRoleTag(null);
        } else {
            WishlistSource primarySource = group.get(0).getSource() != null
                    ? group.get(0).getSource()
                    : WishlistSource.client;
            consolidated.setSource(primarySource);
            consolidated.setSourceRoleTag(mergeCanonicalRoleTags(group));
        }

        consolidated.setTargetContext(resolveTargetContext(group));
        consolidated.setSourceTaskId(resolveCommonSourceTaskId(group));

        List<String> sourceIds = group.stream()
                .map(WishlistEntity::getId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .toList();
        consolidated.setConsolidatedFromIds(serializeIds(sourceIds));

        boolean hasConflict = !conflicts.isEmpty();
        consolidated.setConsolidationConflict(hasConflict);
        consolidated.setConsolidationNotes(buildConsolidationNotes(group, conflicts));
        consolidated.setContent(buildConsolidatedContent(group, conflicts));
        return consolidated;
    }

    private String mergeCanonicalRoleTags(List<WishlistEntity> group) {
        Set<String> uniqueTags = new LinkedHashSet<>();
        for (WishlistEntity w : group) {
            if (w.getSourceRoleTag() == null || w.getSourceRoleTag().isBlank()) {
                continue;
            }
            for (String part : w.getSourceRoleTag().split(",")) {
                String token = part.trim();
                if (!token.isEmpty()) {
                    try {
                        String validated = WishlistEntity.validateCanonicalSourceRoleTag(token);
                        if (validated != null) {
                            uniqueTags.add(validated);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Skip non-canonical legacy tags rather than failing consolidation
                    }
                }
            }
        }
        if (uniqueTags.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (String tag : uniqueTags) {
            int addedLen = joined.isEmpty() ? tag.length() : tag.length() + 2;
            if (joined.length() + addedLen > WishlistEntity.MAX_SOURCE_ROLE_TAG_LENGTH) {
                break;
            }
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append(tag);
        }
        return WishlistEntity.validateCanonicalSourceRoleTag(joined.toString());
    }

    private TargetContext resolveTargetContext(List<WishlistEntity> group) {
        TargetContext resolved = TargetContext.UNDETERMINED;
        for (WishlistEntity w : group) {
            TargetContext ctx = w.getTargetContext();
            if (ctx != null && ctx != TargetContext.UNDETERMINED) {
                if (resolved == TargetContext.UNDETERMINED) {
                    resolved = ctx;
                } else if (resolved != ctx) {
                    return TargetContext.UNDETERMINED;
                }
            }
        }
        return resolved;
    }

    private UUID resolveCommonSourceTaskId(List<WishlistEntity> group) {
        UUID first = group.get(0).getSourceTaskId();
        if (first == null) {
            return null;
        }
        for (WishlistEntity w : group) {
            if (!first.equals(w.getSourceTaskId())) {
                return null;
            }
        }
        return first;
    }

    private String serializeIds(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]";
        }
    }

    private String buildConsolidationNotes(List<WishlistEntity> group, List<ConflictPair> conflicts) {
        StringBuilder notes = new StringBuilder();
        notes.append("Consolidated ").append(group.size()).append(" accumulated wishlist item(s) on unfreeze.");
        if (!conflicts.isEmpty()) {
            notes.append(" Belnap BOTH conflict(s) detected: ").append(conflicts.size()).append(".");
            for (ConflictPair c : conflicts) {
                notes.append(" [").append(c.sideA().getId()).append(" vs ").append(c.sideB().getId())
                        .append(": ").append(c.reason()).append("]");
            }
        }
        return notes.toString();
    }

    private String buildConsolidatedContent(List<WishlistEntity> group, List<ConflictPair> conflicts) {
        StringBuilder sb = new StringBuilder();
        if (conflicts.isEmpty()) {
            sb.append("[CONSOLIDATED WISHLIST — ").append(group.size()).append(" source item(s)]\n");
        } else {
            sb.append("[CONSOLIDATED WISHLIST — ").append(group.size())
                    .append(" source item(s) | BELNAP CONFLICT FLAGGED]\n");
        }

        Set<String> uniqueRequirements = new LinkedHashSet<>();
        for (WishlistEntity item : group) {
            if (item.getContent() != null && !item.getContent().isBlank()) {
                uniqueRequirements.add(item.getContent().trim());
            }
        }
        int idx = 1;
        for (String req : uniqueRequirements) {
            sb.append(idx++).append(". ").append(req).append("\n");
        }

        if (!conflicts.isEmpty()) {
            sb.append("\n[CONFLICT DETECTED — REQUIRES RESOLUTION]\n");
            for (ConflictPair conflict : conflicts) {
                sb.append("- ").append(conflict.reason()).append("\n");
                sb.append("  * Side A ").append(formatLineageMeta(conflict.sideA())).append(": ")
                        .append(safeTrim(conflict.sideA().getContent())).append("\n");
                sb.append("  * Side B ").append(formatLineageMeta(conflict.sideB())).append(": ")
                        .append(safeTrim(conflict.sideB().getContent())).append("\n");
            }
        }

        sb.append("\nLineage / Source Wishlists:\n");
        for (WishlistEntity item : group) {
            sb.append("- ").append(formatLineageMeta(item)).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatLineageMeta(WishlistEntity item) {
        Instant createdAt = item.getCreatedAt();
        String rolePart = item.getSourceRoleTag() != null ? ", role=" + item.getSourceRoleTag() : "";
        return "[id=" + item.getId()
                + ", source=" + item.getSource()
                + rolePart
                + ", createdAt=" + (createdAt != null ? createdAt.toString() : "unknown")
                + "]";
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }
}
