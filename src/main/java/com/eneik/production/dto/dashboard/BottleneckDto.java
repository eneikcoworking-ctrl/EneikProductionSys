package com.eneik.production.dto.dashboard;

import java.util.UUID;

public record BottleneckDto(
    String type,
    String tag,
    UUID accountId,
    Long queuedCount,
    Long waitingMinutes,
    Long expiredCount24h,
    String reason
) {
    public BottleneckDto(String type, String tag, Long queuedCount, Long waitingMinutes, String reason) {
        this(type, tag, null, queuedCount, waitingMinutes, null, reason);
    }

    public BottleneckDto(String type, UUID accountId, Long expiredCount24h, String reason) {
        this(type, null, accountId, null, null, expiredCount24h, reason);
    }
}
