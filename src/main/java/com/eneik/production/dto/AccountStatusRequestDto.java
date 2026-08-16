package com.eneik.production.dto;

import com.eneik.production.models.persistence.AccountStatus;

public record AccountStatusRequestDto(
        AccountStatus status
) {
}
