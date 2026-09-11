package com.eneik.production.services.onboarding;

/**
 * Tri-state observation status preserving the category boundary between
 * inspection capability/access and client repository reality
 * (GILBERT_RAYL_03_CATEGORY_ERROR_SCAN, NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D002, D012).
 *
 * <p>Prevents treating factory's inability to inspect a repository as a factual absence
 * (e.g. converting absence of GitHub token or network error into "no tests" / "no CI" findings).
 */
public enum InspectionStatus {
    YES,
    NO,
    UNCHECKED;

    public boolean isYes() {
        return this == YES;
    }

    public boolean isNo() {
        return this == NO;
    }

    public boolean isUnchecked() {
        return this == UNCHECKED;
    }

    public static InspectionStatus of(boolean detected) {
        return detected ? YES : NO;
    }

    public String displayValue() {
        return switch (this) {
            case YES -> "Yes";
            case NO -> "No";
            case UNCHECKED -> "не проверено";
        };
    }
}
