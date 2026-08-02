package com.eneik.production.dto.tree;

import java.time.Instant;

/** A real, already-happened autonomous event to narrate on the tree - never a pending decision. */
public record AnnotationDto(
        String type,
        String text,
        Instant occurredAt,
        String prUrl
) {
}
