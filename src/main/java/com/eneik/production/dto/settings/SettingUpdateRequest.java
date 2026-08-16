package com.eneik.production.dto.settings;

public record SettingUpdateRequest(
        String key,
        String value
) {
}
