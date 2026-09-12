package com.eneik.production.services.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GROUPING_PROXIMITY_GATE (D011 - Perception failure) & FALSIFICATION_HARNESS (D008):
 *
 * Machine audit of layout geometry and mobile accessibility:
 * 1. Overlap detection (collision): overlapping elements (e.g. navigation menus overlapping content
 *    on mobile resolutions) represent negative spatial distance - a critical Gestalt failure.
 * 2. Gestalt proximity ratio: related elements must sit spatially closer to each other than to
 *    unrelated elements (intra-group distance < inter-group distance).
 * 3. Mobile viewport scalability: user-scalable=no or maximum-scale=1.0 violates mobile accessibility
 *    (WCAG 1.4.4 / 1.4.10) and prevents users from zoom recovery when menus collide.
 */
@Service
public class LayoutGeometryAuditService {

    private static final Logger log = LoggerFactory.getLogger(LayoutGeometryAuditService.class);

    private static final Pattern VIEWPORT_META = Pattern.compile("<meta\\s+[^>]*name=[\"']viewport[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTR = Pattern.compile("content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_SCALABLE_NO = Pattern.compile("user-scalable\\s*=\\s*(?:no|0)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAXIMUM_SCALE_ONE = Pattern.compile("maximum-scale\\s*=\\s*1(?:\\.0+)?\\b", Pattern.CASE_INSENSITIVE);

    // Bounding box patterns in markup:
    // 1. <rect ... x="10" y="20" width="100" height="50" ...>
    private static final Pattern SVG_RECT = Pattern.compile("<rect\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE);
    // 2. data-rect="x,y,w,h" or data-box="x,y,w,h"
    private static final Pattern DATA_RECT_ELEM = Pattern.compile("<([a-zA-Z0-9_-]+)\\b([^>]*data-(?:rect|box)=[\"']([0-9.,\\s-]+)[\"'][^>]*)>", Pattern.CASE_INSENSITIVE);
    // 3. inline style with left, top, width, height
    private static final Pattern HTML_TAG_WITH_STYLE = Pattern.compile("<([a-zA-Z0-9_-]+)\\b([^>]*style=[\"']([^\"']*)[\"'][^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_ATTR = Pattern.compile("style=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public LayoutGeometryAuditService() {
        this.objectMapper = new ObjectMapper();
    }

    public LayoutGeometryAuditService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public record BoundingBox(String id, String group, double left, double top, double width, double height) {
        public double right() {
            return left + width;
        }

        public double bottom() {
            return top + height;
        }

        public double centerX() {
            return left + width / 2.0;
        }

        public double centerY() {
            return top + height / 2.0;
        }

        public boolean overlaps(BoundingBox other) {
            if (other == null || other == this) {
                return false;
            }
            // Strict bounding box intersection with positive area
            return this.left < other.right() && this.right() > other.left
                    && this.top < other.bottom() && this.bottom() > other.top;
        }

        public double overlapArea(BoundingBox other) {
            if (!overlaps(other)) {
                return 0.0;
            }
            double xOverlap = Math.min(right(), other.right()) - Math.max(left, other.left);
            double yOverlap = Math.min(bottom(), other.bottom()) - Math.max(top, other.top);
            return xOverlap * yOverlap;
        }

        public double distanceTo(BoundingBox other) {
            if (other == null) {
                return Double.MAX_VALUE;
            }
            return Math.hypot(centerX() - other.centerX(), centerY() - other.centerY());
        }
    }

    public record Collision(String elementA, String elementB, double overlapArea, String description) {}

    public record ViewportScalabilityResult(boolean scalable, String violation) {}

    public enum GeometryVerdict {
        PASSED("прошло"),
        FAILED("отказ"),
        CANNOT_JUDGE("геометрия не выводима");

        private final String displayName;

        GeometryVerdict(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record LayoutAuditResult(
            boolean passed,
            boolean scalable,
            List<Collision> collisions,
            double proximityRatio,
            GeometryVerdict verdict,
            String verdictReason) {

        public LayoutAuditResult(
                boolean passed,
                boolean scalable,
                List<Collision> collisions,
                double proximityRatio,
                String verdictReason) {
            this(passed, scalable, collisions, proximityRatio,
                    passed ? GeometryVerdict.PASSED : (hasCollisions(collisions) || !scalable || (verdictReason != null && verdictReason.contains("rejected")) ? GeometryVerdict.FAILED : GeometryVerdict.CANNOT_JUDGE),
                    verdictReason);
        }

        private static boolean hasCollisions(List<Collision> collisions) {
            return collisions != null && !collisions.isEmpty();
        }

        public boolean hasCollisions() {
            return hasCollisions(collisions);
        }

        public boolean isCannotJudge() {
            return verdict == GeometryVerdict.CANNOT_JUDGE;
        }

        public boolean isFailed() {
            return verdict == GeometryVerdict.FAILED;
        }
    }

    /**
     * Audits viewport scalability in HTML/markup.
     * Rejects user-scalable=no and maximum-scale=1.0.
     */
    public ViewportScalabilityResult auditViewportScalability(String html) {
        if (html == null || html.isBlank()) {
            return new ViewportScalabilityResult(true, null);
        }

        Matcher metaMatcher = VIEWPORT_META.matcher(html);
        while (metaMatcher.find()) {
            String tag = metaMatcher.group();
            Matcher contentMatcher = CONTENT_ATTR.matcher(tag);
            if (contentMatcher.find()) {
                String content = contentMatcher.group(1);
                if (USER_SCALABLE_NO.matcher(content).find()) {
                    return new ViewportScalabilityResult(false, "viewport scalability prohibited: user-scalable=no or 0 detected in <meta name=\"viewport\">");
                }
                if (MAXIMUM_SCALE_ONE.matcher(content).find()) {
                    return new ViewportScalabilityResult(false, "viewport scalability prohibited: maximum-scale=1.0 restricts mobile zoom recovery in <meta name=\"viewport\">");
                }
            }
        }
        return new ViewportScalabilityResult(true, null);
    }

    /**
     * Parses a JSON array node of bounding boxes.
     */
    public List<BoundingBox> parseBoxArray(JsonNode root) {
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<BoundingBox> list = new ArrayList<>();
        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            String id = node.has("id") ? node.get("id").asText() : "elem-" + i;
            String group = node.has("group") ? node.get("group").asText() : null;
            double left = node.has("left") ? node.get("left").asDouble() : (node.has("x") ? node.get("x").asDouble() : 0.0);
            double top = node.has("top") ? node.get("top").asDouble() : (node.has("y") ? node.get("y").asDouble() : 0.0);
            double width = node.has("width") ? node.get("width").asDouble() : (node.has("w") ? node.get("w").asDouble() : 0.0);
            double height = node.has("height") ? node.get("height").asDouble() : (node.has("h") ? node.get("h").asDouble() : 0.0);
            if (width > 0 && height > 0) {
                list.add(new BoundingBox(id, group, left, top, width, height));
            }
        }
        return list;
    }

    /**
     * Extracts bounding boxes from HTML markup or JSON layout definitions.
     */
    public List<BoundingBox> extractBoundingBoxes(String markup) {
        if (markup == null || markup.isBlank()) {
            return List.of();
        }

        String trimmed = markup.trim();
        // Check if input is a JSON array of bounding boxes
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                if (root.isArray()) {
                    List<BoundingBox> list = parseBoxArray(root);
                    if (!list.isEmpty()) {
                        return list;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Check if input is a JSON object with boxes/elements/rectangles
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                for (String key : List.of("boxes", "elements", "rectangles", "items", "components")) {
                    if (root.has(key) && root.get(key).isArray()) {
                        List<BoundingBox> list = parseBoxArray(root.get(key));
                        if (!list.isEmpty()) {
                            return list;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        List<BoundingBox> boxes = new ArrayList<>();
        int counter = 0;

        // 1. Check data-rect / data-box attributes: data-rect="x,y,w,h"
        Matcher dataRectMatcher = DATA_RECT_ELEM.matcher(markup);
        while (dataRectMatcher.find()) {
            counter++;
            String tag = dataRectMatcher.group(1);
            String attrs = dataRectMatcher.group(2);
            String rectStr = dataRectMatcher.group(3);

            String id = extractAttr(attrs, "id");
            if (id == null) id = tag + "-" + counter;
            String group = extractAttr(attrs, "data-group");
            if (group == null) group = extractAttr(attrs, "class");

            String[] parts = rectStr.split("[,\\s]+");
            if (parts.length >= 4) {
                try {
                    double left = Double.parseDouble(parts[0].trim());
                    double top = Double.parseDouble(parts[1].trim());
                    double width = Double.parseDouble(parts[2].trim());
                    double height = Double.parseDouble(parts[3].trim());
                    if (width > 0 && height > 0) {
                        boxes.add(new BoundingBox(id, group, left, top, width, height));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Check SVG <rect x="..." y="..." width="..." height="..." />
        Matcher svgRectMatcher = SVG_RECT.matcher(markup);
        while (svgRectMatcher.find()) {
            counter++;
            String attrs = svgRectMatcher.group(1);
            Double x = extractDoubleAttr(attrs, "x");
            Double y = extractDoubleAttr(attrs, "y");
            Double w = extractDoubleAttr(attrs, "width");
            Double h = extractDoubleAttr(attrs, "height");
            if (w != null && h != null && w > 0 && h > 0) {
                String id = extractAttr(attrs, "id");
                if (id == null) id = "svg-rect-" + counter;
                String group = extractAttr(attrs, "data-group");
                if (group == null) group = extractAttr(attrs, "class");
                boxes.add(new BoundingBox(id, group, x != null ? x : 0.0, y != null ? y : 0.0, w, h));
            }
        }

        // 3. Check HTML elements with inline style: style="left: ...; top: ...; width: ...; height: ..."
        Matcher styleMatcher = HTML_TAG_WITH_STYLE.matcher(markup);
        boolean foundStyleBoxes = false;
        while (styleMatcher.find()) {
            String tag = styleMatcher.group(1);
            if ("rect".equalsIgnoreCase(tag)) {
                continue;
            }
            String attrs = styleMatcher.group(2);
            String style = styleMatcher.group(3);

            Double left = extractCssPixel(style, "left");
            Double top = extractCssPixel(style, "top");
            Double width = extractCssPixel(style, "width");
            Double height = extractCssPixel(style, "height");
            if (left != null && top != null && width != null && height != null && width > 0 && height > 0) {
                counter++;
                String id = extractAttr(attrs, "id");
                if (id == null) id = tag + "-" + counter;
                String group = extractAttr(attrs, "data-group");
                if (group == null) group = extractAttr(attrs, "class");
                boxes.add(new BoundingBox(id, group, left, top, width, height));
                foundStyleBoxes = true;
            }
        }

        if (!foundStyleBoxes) {
            Matcher rawStyleMatcher = STYLE_ATTR.matcher(markup);
            while (rawStyleMatcher.find()) {
                String style = rawStyleMatcher.group(1);
                Double left = extractCssPixel(style, "left");
                Double top = extractCssPixel(style, "top");
                Double width = extractCssPixel(style, "width");
                Double height = extractCssPixel(style, "height");
                if (left != null && top != null && width != null && height != null && width > 0 && height > 0) {
                    counter++;
                    boxes.add(new BoundingBox("elem-" + counter, null, left, top, width, height));
                }
            }
        }

        return boxes;
    }

    /**
     * Audits layout geometry: detects overlapping elements and checks Gestalt proximity.
     * Returns three-valued outcome: PASSED, FAILED, or CANNOT_JUDGE.
     */
    public LayoutAuditResult auditBoxes(List<BoundingBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return new LayoutAuditResult(false, true, List.of(), 1.0, GeometryVerdict.CANNOT_JUDGE, "геометрия не выводима: нет элементов для аудита");
        }

        List<Collision> collisions = new ArrayList<>();
        // Pairwise collision check
        for (int i = 0; i < boxes.size(); i++) {
            BoundingBox a = boxes.get(i);
            for (int j = i + 1; j < boxes.size(); j++) {
                BoundingBox b = boxes.get(j);
                if (a.overlaps(b)) {
                    double area = a.overlapArea(b);
                    String desc = String.format("layout collision: '%s' and '%s' overlap by %.1f px² at [%.1f, %.1f] vs [%.1f, %.1f]",
                            a.id(), b.id(), area, a.left(), a.top(), b.left(), b.top());
                    collisions.add(new Collision(a.id(), b.id(), area, desc));
                }
            }
        }

        if (!collisions.isEmpty()) {
            String reason = "rejected: " + collisions.size() + " layout collision(s) detected (" + collisions.get(0).description() + ")";
            return new LayoutAuditResult(false, true, collisions, 0.0, GeometryVerdict.FAILED, reason);
        }

        // Grouping proximity check (GROUPING_PROXIMITY_GATE):
        // Cluster by group attribute
        Map<String, List<BoundingBox>> groups = new LinkedHashMap<>();
        for (BoundingBox box : boxes) {
            if (box.group() != null && !box.group().isBlank()) {
                groups.computeIfAbsent(box.group(), k -> new ArrayList<>()).add(box);
            }
        }

        double maxProximityRatio = 0.0;
        List<String> proximityViolations = new ArrayList<>();

        for (Map.Entry<String, List<BoundingBox>> entry : groups.entrySet()) {
            String groupName = entry.getKey();
            List<BoundingBox> members = entry.getValue();
            if (members.size() < 2) {
                continue;
            }

            // Average intra-group distance
            double intraSum = 0;
            int intraPairs = 0;
            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    intraSum += members.get(i).distanceTo(members.get(j));
                    intraPairs++;
                }
            }
            double avgIntra = intraPairs > 0 ? intraSum / intraPairs : 0;

            // Distance to nearest non-member element
            double minInter = Double.MAX_VALUE;
            for (BoundingBox member : members) {
                for (BoundingBox other : boxes) {
                    if (!members.contains(other)) {
                        minInter = Math.min(minInter, member.distanceTo(other));
                    }
                }
            }

            if (minInter < Double.MAX_VALUE && minInter > 0) {
                double ratio = avgIntra / minInter;
                maxProximityRatio = Math.max(maxProximityRatio, ratio);
                // In Gestalt proximity, related elements should be closer to each other than to external elements (ratio < 1.0)
                if (ratio >= 1.0) {
                    proximityViolations.add(String.format("group '%s': intra-group distance (%.1f) >= nearest external element distance (%.1f), ratio=%.2f",
                            groupName, avgIntra, minInter, ratio));
                }
            }
        }

        if (!proximityViolations.isEmpty()) {
            String reason = "rejected: Gestalt proximity gate violated (" + String.join("; ", proximityViolations) + ")";
            return new LayoutAuditResult(false, true, collisions, maxProximityRatio, GeometryVerdict.FAILED, reason);
        }

        String reason = "accepted: layout geometry verified (0 collisions, proximity ratio=" + String.format(Locale.ROOT, "%.2f", maxProximityRatio) + ")";
        return new LayoutAuditResult(true, true, collisions, maxProximityRatio, GeometryVerdict.PASSED, reason);
    }

    /**
     * Audits multi-resolution JSON definitions (e.g. {"desktop": [...], "mobile": [...]}).
     */
    private LayoutAuditResult auditMultiResolutionJson(JsonNode root) {
        List<Collision> allCollisions = new ArrayList<>();
        double worstProximityRatio = 0.0;
        int auditedResolutions = 0;

        for (String resKey : List.of("desktop", "mobile", "tablet")) {
            if (root.has(resKey)) {
                JsonNode resNode = root.get(resKey);
                List<BoundingBox> boxes = parseBoxArray(resNode);
                if (!boxes.isEmpty()) {
                    auditedResolutions++;
                    LayoutAuditResult resAudit = auditBoxes(boxes);
                    if (resAudit.hasCollisions()) {
                        allCollisions.addAll(resAudit.collisions());
                    }
                    if (resAudit.proximityRatio() > worstProximityRatio) {
                        worstProximityRatio = resAudit.proximityRatio();
                    }
                    if (resAudit.isFailed() && !resAudit.hasCollisions()) {
                        return resAudit;
                    }
                }
            }
        }

        if (auditedResolutions == 0) {
            return new LayoutAuditResult(false, true, List.of(), 1.0, GeometryVerdict.CANNOT_JUDGE, "геометрия не выводима: нет элементов для аудита в разрешениях");
        }

        if (!allCollisions.isEmpty()) {
            return new LayoutAuditResult(false, true, allCollisions, worstProximityRatio, GeometryVerdict.FAILED, allCollisions.get(0).description());
        }

        return new LayoutAuditResult(true, true, List.of(), worstProximityRatio, GeometryVerdict.PASSED, "accepted: layout geometry verified across resolutions");
    }

    /**
     * Full audit of markup or layout JSON: combines viewport scalability with layout geometry.
     */
    public LayoutAuditResult auditLayout(String markup) {
        if (markup == null || markup.isBlank()) {
            return new LayoutAuditResult(false, true, List.of(), 1.0, GeometryVerdict.CANNOT_JUDGE, "геометрия не выводима: пустой контент");
        }

        ViewportScalabilityResult viewportResult = auditViewportScalability(markup);
        if (!viewportResult.scalable()) {
            return new LayoutAuditResult(
                    false,
                    false,
                    List.of(),
                    1.0,
                    GeometryVerdict.FAILED,
                    "rejected: " + viewportResult.violation()
            );
        }

        String trimmed = markup.trim();
        // Check if content is a JSON with multiple resolutions (e.g. {"desktop": [...], "mobile": [...]})
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                if (root.has("desktop") || root.has("mobile")) {
                    return auditMultiResolutionJson(root);
                }
            } catch (Exception ignored) {}
        }

        List<BoundingBox> boxes = extractBoundingBoxes(markup);
        if (boxes.isEmpty()) {
            return new LayoutAuditResult(false, true, List.of(), 1.0, GeometryVerdict.CANNOT_JUDGE, "геометрия не выводима: нет элементов для аудита");
        }

        return auditBoxes(boxes);
    }

    private static String extractAttr(String attrs, String name) {
        Pattern p = Pattern.compile("\\b" + name + "=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(attrs);
        return m.find() ? m.group(1).trim() : null;
    }

    private static Double extractDoubleAttr(String attrs, String name) {
        String val = extractAttr(attrs, name);
        if (val == null) return null;
        try {
            return Double.parseDouble(val.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double extractCssPixel(String style, String property) {
        Pattern p = Pattern.compile("\\b" + property + "\\s*:\\s*([0-9.]+)\\s*(?:px)?\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(style);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
