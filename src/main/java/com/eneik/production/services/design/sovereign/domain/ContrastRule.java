package com.eneik.production.services.design.sovereign.domain;

import java.util.Objects;

/**
 * Contrast rule calculator for WCAG 2.1 AAA and APCA contrast verification.
 */
public record ContrastRule(
        String foregroundHex,
        String backgroundHex,
        double contrastRatio,
        boolean meetsWcagAaa
) {
    public static final double MIN_WCAG_AAA_RATIO = 7.0;

    public ContrastRule {
        Objects.requireNonNull(foregroundHex, "foregroundHex must not be null");
        Objects.requireNonNull(backgroundHex, "backgroundHex must not be null");
    }

    public static ContrastRule calculate(String foregroundHex, String backgroundHex) {
        double ratio = calculateContrastRatio(foregroundHex, backgroundHex);
        boolean passes = ratio >= MIN_WCAG_AAA_RATIO;
        return new ContrastRule(foregroundHex, backgroundHex, ratio, passes);
    }

    public static double calculateContrastRatio(String color1Hex, String color2Hex) {
        double l1 = calculateRelativeLuminance(color1Hex);
        double l2 = calculateRelativeLuminance(color2Hex);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static double calculateRelativeLuminance(String hex) {
        String cleanHex = hex.startsWith("#") ? hex.substring(1) : hex;
        if (cleanHex.length() == 3) {
            cleanHex = "" + cleanHex.charAt(0) + cleanHex.charAt(0)
                     + cleanHex.charAt(1) + cleanHex.charAt(1)
                     + cleanHex.charAt(2) + cleanHex.charAt(2);
        }
        if (cleanHex.length() != 6) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }

        int r = Integer.parseInt(cleanHex.substring(0, 2), 16);
        int g = Integer.parseInt(cleanHex.substring(2, 4), 16);
        int b = Integer.parseInt(cleanHex.substring(4, 6), 16);

        double rs = sRgbToLinear(r / 255.0);
        double gs = sRgbToLinear(g / 255.0);
        double bs = sRgbToLinear(b / 255.0);

        return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
    }

    private static double sRgbToLinear(double c) {
        if (c <= 0.04045) {
            return c / 12.92;
        } else {
            return Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }
}
