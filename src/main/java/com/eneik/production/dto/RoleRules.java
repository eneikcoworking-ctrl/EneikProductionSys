package com.eneik.production.dto;

import java.util.List;

public record RoleRules(
    String tag,
    String scope,
    List<String> forbidden,
    String outputFormat,
    String reviewRequiredBy,
    String refusalCriteria,
    String deonticStatus
) {}
