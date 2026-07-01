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
        List<String> forbidden = extractList(markdown, "Forbidden", "Запрещено");
        String outputFormat = extractSection(markdown, "Output", "Артефакт выхода", "Definition of Done");
        String reviewRequiredBy = extractReviewRequiredBy(markdown);

        return new RoleRules(tag, scope, forbidden, outputFormat, reviewRequiredBy);
    }

    private String extractSection(String markdown, String... keywords) {
        for (String keyword : keywords) {
            // Match a header containing the keyword and everything until the next header of the same or higher level
            // We use \R for line breaks and ensure we match the content after the header
            Pattern pattern = Pattern.compile("(?im)^#+\\s+.*" + Pattern.quote(keyword) + ".*$(\\R[\\s\\S]*?)(?=\\R#+ |$)", Pattern.MULTILINE);
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
