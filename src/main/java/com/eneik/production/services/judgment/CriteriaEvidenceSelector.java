package com.eneik.production.services.judgment;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Law 17 (Evidence Selection Law):
 * No judgment may be made on mechanically truncated evidence.
 * Evidence must be selected by its bearing on the criteria rather than by position or alphabetical order.
 * Diff sections are sliced strictly at file boundaries, keeping whole files, preserving original diff order,
 * and explicitly naming omitted files.
 */
public final class CriteriaEvidenceSelector {

    public record SelectedEvidence(String text, List<String> omitted) {}

    private CriteriaEvidenceSelector() {}

    /**
     * Selects sections of a diff to fit within {@code charLimit}, prioritized by bearing on {@code criteria}.
     * Diff sections that fit whole are returned in their original order.
     * Omitted files are named in {@link SelectedEvidence#omitted()}.
     */
    public static SelectedEvidence select(String diff, String criteria, int charLimit) {
        if (diff == null || diff.length() <= charLimit) {
            return new SelectedEvidence(diff == null ? "" : diff, List.of());
        }
        List<String> sections = splitAtFileBoundaries(diff);
        if (sections.size() <= 1) {
            // A single file too large to carry. Bounded safely rather than throwing.
            return new SelectedEvidence(diff.substring(0, Math.max(0, charLimit)),
                    List.of("(the remainder of a single file too large to carry)"));
        }
        Set<String> vocabulary = vocabularyOf(criteria);
        List<String> byBearing = new ArrayList<>(sections);
        byBearing.sort(Comparator.comparingLong((String section) -> bearingOn(section, vocabulary)).reversed());

        int takenLength = 0;
        Set<String> keptSections = new HashSet<>();
        for (String section : byBearing) {
            if (takenLength + section.length() > charLimit) {
                continue;
            }
            takenLength += section.length();
            keptSections.add(section);
        }

        List<String> omitted = new ArrayList<>();
        StringBuilder inOriginalOrder = new StringBuilder();
        for (String section : sections) {
            if (keptSections.contains(section)) {
                inOriginalOrder.append(section);
            } else {
                omitted.add(fileNameOf(section));
            }
        }
        return new SelectedEvidence(inOriginalOrder.toString(), omitted);
    }

    public static List<String> splitAtFileBoundaries(String diff) {
        List<String> sections = new ArrayList<>();
        if (diff == null || diff.isEmpty()) {
            return sections;
        }
        Matcher boundary = Pattern.compile("(?m)^diff --git ").matcher(diff);
        List<Integer> starts = new ArrayList<>();
        while (boundary.find()) {
            starts.add(boundary.start());
        }
        if (starts.isEmpty()) {
            sections.add(diff);
            return sections;
        }
        if (starts.get(0) > 0) {
            sections.add(diff.substring(0, starts.get(0)));
        }
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : diff.length();
            sections.add(diff.substring(starts.get(i), end));
        }
        return sections;
    }

    public static Set<String> vocabularyOf(String criteria) {
        if (criteria == null || criteria.isBlank()) {
            return Set.of();
        }
        Set<String> words = new HashSet<>();
        for (String word : criteria.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (word.length() > 3) {
                words.add(word);
            }
        }
        return words;
    }

    public static long bearingOn(String section, Set<String> vocabulary) {
        if (vocabulary == null || vocabulary.isEmpty()) {
            return 0;
        }
        String lower = section.toLowerCase(Locale.ROOT);
        return vocabulary.stream().filter(lower::contains).count();
    }

    public static String fileNameOf(String section) {
        Matcher header = Pattern.compile("(?m)^diff --git a/(\\S+)").matcher(section);
        return header.find() ? header.group(1) : "(unnamed section)";
    }

    /**
     * Selects sections of non-diff evidence to fit within {@code charLimit}.
     * When {@code criteria} vocabulary is present, sections (paragraphs) are ranked by their bearing on the criteria.
     * When {@code criteria} vocabulary is absent, the text is bounded at line boundaries.
     */
    public static SelectedEvidence selectNonDiff(String text, String criteria, int charLimit) {
        if (text == null || text.length() <= charLimit) {
            return new SelectedEvidence(text == null ? "" : text, List.of());
        }
        Set<String> vocabulary = vocabularyOf(criteria);
        if (vocabulary.isEmpty()) {
            int cutIdx = text.lastIndexOf('\n', charLimit);
            if (cutIdx <= 0 || cutIdx < charLimit / 2) {
                cutIdx = text.lastIndexOf(' ', charLimit);
            }
            if (cutIdx <= 0) {
                cutIdx = charLimit;
            }
            return new SelectedEvidence(text.substring(0, cutIdx).stripTrailing(), List.of("(remainder omitted: no criteria to rank by)"));
        }

        String[] parts = text.split("\n\n");
        if (parts.length <= 1) {
            int cutIdx = text.lastIndexOf('\n', charLimit);
            if (cutIdx <= 0) cutIdx = charLimit;
            return new SelectedEvidence(text.substring(0, cutIdx).stripTrailing(), List.of("(remainder of single section)"));
        }

        String first = parts[0];
        String last = parts.length > 1 ? parts[parts.length - 1] : "";
        int fixedCost = first.length() + (last.isEmpty() ? 0 : last.length() + 2);
        int budgetForMiddle = Math.max(0, charLimit - fixedCost);

        List<Integer> middleIndices = new ArrayList<>();
        for (int i = 1; i < parts.length - 1; i++) {
            middleIndices.add(i);
        }
        middleIndices.sort(Comparator.comparingLong((Integer idx) -> bearingOn(parts[idx], vocabulary)).reversed());

        Set<Integer> kept = new HashSet<>();
        kept.add(0);
        if (!last.isEmpty()) {
            kept.add(parts.length - 1);
        }

        int middleTaken = 0;
        for (int idx : middleIndices) {
            int cost = parts[idx].length() + 2;
            if (middleTaken + cost <= budgetForMiddle) {
                middleTaken += cost;
                kept.add(idx);
            }
        }

        List<String> omitted = new ArrayList<>();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (kept.contains(i)) {
                if (result.length() > 0) {
                    result.append("\n\n");
                }
                result.append(parts[i]);
            } else {
                String firstLine = parts[i].lines().findFirst().orElse("section " + i).trim();
                if (firstLine.length() > 40) {
                    firstLine = firstLine.substring(0, 37) + "...";
                }
                omitted.add("section " + (i + 1) + " ['" + firstLine + "']");
            }
        }
        return new SelectedEvidence(result.toString(), omitted);
    }
}
