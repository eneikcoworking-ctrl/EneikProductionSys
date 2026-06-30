package com.eneik.production.dto.dashboard;

import java.util.UUID;

public record ExpiredStatDto(
    UUID accountId,
    long count
) {}
