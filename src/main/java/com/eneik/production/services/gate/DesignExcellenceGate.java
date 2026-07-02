package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DesignExcellenceGate implements GateCheck {
    private static final Set<String> UI_TAGS = Set.of("BARCAN-TAG-03", "BARCAN-TAG-11");
    private static final String CHECK_NAME = "design_excellence";

    @Override
    public GateResult check(TaskEntity task) {
        String roleTag = task.getRole().getTag();
        if (!UI_TAGS.contains(roleTag)) {
            return new GateResult(true, CHECK_NAME, List.of("not applicable to this role"));
        }

        List<String> failureReasons = new ArrayList<>();
        int score = 0;

        JsonNode payload = task.getPayload();
        JsonNode screenshotUrls = (payload != null) ? payload.get("screenshotUrls") : null;

        boolean hasDesktop = false;
        boolean hasMobile = false;
        long desktopSize = -1;
        long mobileSize = -1;

        if (screenshotUrls != null && screenshotUrls.isArray()) {
            for (JsonNode screenshot : screenshotUrls) {
                String url = screenshot.has("url") ? screenshot.get("url").asText() : "";
                long size = screenshot.has("size") ? screenshot.get("size").asLong() : 0;

                if (url.contains("1440")) {
                    hasDesktop = true;
                    desktopSize = size;
                } else if (url.contains("375")) {
                    hasMobile = true;
                    mobileSize = size;
                }
            }
        }

        // 1. has_both_screenshots (desktop 1440px + mobile 375px): вес 30
        if (hasDesktop && hasMobile) {
            score += 30;
        } else {
            if (!hasDesktop) failureReasons.add("missing desktop screenshot (1440px)");
            if (!hasMobile) failureReasons.add("missing mobile screenshot (375px)");
        }

        // 2. responsive_ok (оба скриншота присутствуют и не идентичны по размеру файла): вес 40
        if (hasDesktop && hasMobile && desktopSize != mobileSize) {
            score += 40;
        } else if (hasDesktop && hasMobile && desktopSize == mobileSize) {
            failureReasons.add("responsive check failed: screenshots have identical file sizes");
        }

        // 3. visual_qa_ok (скриншот не пустой и не error-page — простая проверка размера файла > 1KB): вес 30
        boolean visualQaOk = true;
        if (hasDesktop && desktopSize <= 1024) {
            visualQaOk = false;
            failureReasons.add("desktop screenshot is too small or empty");
        }
        if (hasMobile && mobileSize <= 1024) {
            visualQaOk = false;
            failureReasons.add("mobile screenshot is too small or empty");
        }

        if (visualQaOk && (hasDesktop || hasMobile)) {
            score += 30;
        }

        boolean passed = score >= 70;
        return new GateResult(passed, CHECK_NAME, failureReasons);
    }
}
