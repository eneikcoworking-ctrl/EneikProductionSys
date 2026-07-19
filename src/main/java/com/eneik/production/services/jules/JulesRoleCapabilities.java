package com.eneik.production.services.jules;

import java.util.List;

public final class JulesRoleCapabilities {
    public static final List<String> ALL_ROLE_TAGS = List.of(
            "BARCAN-TAG-00",
            "BARCAN-TAG-01",
            "BARCAN-TAG-02",
            "BARCAN-TAG-03",
            "BARCAN-TAG-04",
            "BARCAN-TAG-05",
            "BARCAN-TAG-06",
            "BARCAN-TAG-07",
            "BARCAN-TAG-08",
            "BARCAN-TAG-09",
            "BARCAN-TAG-10",
            "BARCAN-TAG-11",
            "BARCAN-TAG-12"
    );

    public static final String ALL_CAPABILITIES = String.join(",", ALL_ROLE_TAGS);

    private JulesRoleCapabilities() {
    }

    public static String canonicalCapabilities() {
        return ALL_CAPABILITIES;
    }

    public static boolean isKnownRole(String tag) {
        return ALL_ROLE_TAGS.contains(tag);
    }
}
