package com.eneik.production.dto.dashboard;

import java.util.List;
import java.util.Map;

public record ClientDeliveryDto(
    List<Map<String, Object>> requested,
    List<Map<String, Object>> delivered,
    List<String> screenshots,
    List<String> prLinks,
    String testSummary
) {}
