package com.eneik.production.services.design.sovereign.domain;

import java.util.Objects;

/**
 * Media asset descriptor for layout synthesis.
 */
public record MediaAsset(
        String id,
        String title,
        String url,
        String category,
        String mimeType
) {
    public MediaAsset {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(url, "url must not be null");
    }
}
