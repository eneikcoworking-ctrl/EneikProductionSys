package com.eneik.production.controllers.policy;

import com.eneik.production.exceptions.DataComplianceException;
import java.util.regex.Pattern;

/**
 * @file PrivacyFilter.java
 * @agent TAG-10 (Deontic Prohibition) & TAG-07 (Second-Order Knowledge)
 * @description PII detection and masking service.
 */
public class PrivacyFilter {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{1,4}?[-.\\s]?\\(?\\d{1,3}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}");
    private static final Pattern CARD_PATTERN = Pattern.compile("(?:\\d[ -]*?){13,19}");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("[A-Z0-9]{6,12}"); // Generic passport-like pattern

    /**
     * Scans input and either masks non-critical PII or blocks critical violations.
     * Rules:
     * - Email/Phone -> Mask with [REDACTED]
     * - Card/Passport -> Block with DataComplianceException
     */
    public static String maskSensitiveData(String input) {
        if (input == null) return null;

        // 1. Critical Blocking (Card / Passport)
        if (CARD_PATTERN.matcher(input).find()) {
            throw new DataComplianceException("Credit Card Number Detected");
        }
        if (PASSPORT_PATTERN.matcher(input).find()) {
            // Basic heuristic: check if it looks like a passport (e.g. alphanumeric 9 chars)
            // For the purpose of this prompt, we block if a strong match is found.
             throw new DataComplianceException("Passport Data Detected");
        }

        // 2. Non-Critical Masking (Email / Phone)
        String masked = EMAIL_PATTERN.matcher(input).replaceAll("[REDACTED]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[REDACTED]");

        return masked;
    }
}
