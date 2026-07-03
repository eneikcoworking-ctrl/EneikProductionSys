package com.eneik.production.dto.settings;

public record SettingDto(
        String key,
        Boolean enabled,
        String maskedValue,
        String source
) {
}
