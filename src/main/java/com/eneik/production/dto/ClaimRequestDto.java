package com.eneik.production.dto;

import java.util.List;
import java.util.UUID;

public record ClaimRequestDto(
    UUID accountId,
    List<String> capableTags
) {}
