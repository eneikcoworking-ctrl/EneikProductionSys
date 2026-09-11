package com.eneik.production.dto.settings;

public record SettingUpdateRequest(
        String key,
        String value,
        String reason
) {
    public SettingUpdateRequest(String key, String value) {
        this(key, value, null);
    }
}
