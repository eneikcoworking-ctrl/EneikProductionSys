package com.eneik.production.services;

import com.eneik.production.dto.RoleRules;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RoleRulesParser {

    public RoleRules parse(String tag, String markdown) {
        String scope = extractSection(markdown, "Scope", "ПРИОРИТЕТЫ");
        List<String> forbidden = extractForbidden(markdown);
        String refusalCriteria = extractSection(markdown, "КРИТЕРИИ ОТКАЗА", "REFUSAL CRITERIA");
        String deonticStatus = extractSection(markdown, "КРИТЕРИИ РЕВЬЮ", "DEONTIC STATUS");
        String outputFormat = extractSection(markdown, "Output", "Артефакт выхода", "Definition of Done", "КЛАССИФИКАЦИЯ", "CLASSIFICATION");
        String reviewRequiredBy = extractReviewRequiredBy(markdown);

        return new RoleRules(tag, scope, forbidden, outputFormat, reviewRequiredBy, refusalCriteria, deonticStatus);
    }

    // Charter files use three different conventions for the Forbidden list, grown organically across
    // roles written at different times: (a) a "### Forbidden (Запрещено)" sub-header followed by a real
    // bullet list (TAG-00/01/03/07/09/11), (b) a single inline "- **Forbidden (Запрещено)**: ..." bullet
    // under a differently-named parent header (TAG-02/04/05/06/10/12), (c) several scattered inline
    // "- **Forbidden**: ..." bullets under unrelated thematic sub-headers with no "Forbidden" in any
    // header text at all (TAG-08). extractList only ever handled (a). This tries (a) first since it's
    // the richest signal, then falls back to a document-wide scan for the inline-bold style so (b)/(c)
    // don't silently come back empty.
    private List<String> extractForbidden(String markdown) {
        List<String> headerBased = extractList(markdown, "Forbidden", "Запрещено");
        if (!headerBased.isEmpty()) {
            return headerBased;
        }
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*[-*+]\\s*\\*\\*Forbidden[^*]*\\*\\*:?\\s*(.+)$").matcher(markdown);
        while (matcher.find()) {
            result.add(matcher.group(1).trim());
        }
        return result;
    }

    private String extractSection(String markdown, String... keywords) {
        for (String keyword : keywords) {
            // Match a header containing the keyword and everything until the next header of the same or
            // higher level. The lookahead deliberately uses \z (true end of input) rather than $ - under
            // Pattern.MULTILINE, $ matches before EVERY line terminator, not just end of input, which
            // made the lazy [\s\S]*? stop after the section's first line instead of running to the next
            // header - every multi-line section (Forbidden lists, refusal criteria, etc.) was silently
            // truncated to one line for every role.
            Pattern pattern = Pattern.compile("(?im)^#+\\s+.*" + Pattern.quote(keyword) + ".*$(\\R[\\s\\S]*?)(?=\\R#+ |\\z)", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(markdown);
            if (matcher.find()) {
                return matcher.group(0).trim();
            }
        }
        return "";
    }

    private List<String> extractList(String markdown, String... keywords) {
        String section = extractSection(markdown, keywords);
        List<String> result = new ArrayList<>();
        if (!section.isEmpty()) {
            Pattern itemPattern = Pattern.compile("(?m)^\\s*[-*+]\\s+(.*)$");
            Matcher matcher = itemPattern.matcher(section);
            while (matcher.find()) {
                result.add(matcher.group(1).trim());
            }
        }
        return result;
    }

    private String extractReviewRequiredBy(String markdown) {
        String reviewSection = extractSection(markdown, "Review", "синхронизация", "проверка");
        if (!reviewSection.isEmpty()) {
            Pattern pattern = Pattern.compile("BARCAN-TAG-\\d{2}");
            Matcher matcher = pattern.matcher(reviewSection);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "";
    }
}
