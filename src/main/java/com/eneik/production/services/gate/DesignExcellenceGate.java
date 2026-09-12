package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 2026-08-03 (Charter Pattern #12 - independent verification, not self-attestation): this gate used to
 * read task.payload.screenshotUrls, expecting url/size fields the implementer would self-report - grepping
 * the whole codebase found no production writer for that field at all, and the standard task brief
 * explicitly tells every implementer NOT to commit screenshots, directly contradicting what this gate
 * expected. Once a project leaves build phase (see isBuildPhaseExempt), this gate would have failed every
 * UI task unconditionally, forever, for a platform gap rather than a real implementer shortcoming.
 *
 * Fixed by reusing the same real-screenshot-commit pattern already proven live by the philosophical
 * falsification track (FalsificationCycleService's screenshotDir convention): UI-tagged tasks now get an
 * explicit brief carve-out (see JulesDispatchService's UI branch in buildRoleContext) asking for exactly
 * two real Playwright screenshots committed to a fixed report-only path. This gate then verifies those
 * files actually landed in the PR's real diff (not a claim) and reads their real byte size via GitHub's
 * Contents API (not a number the implementer wrote) - same trust model as BackendContractGate.
 */
@Service
@Order(200)
public class DesignExcellenceGate implements GateCheck {
    public static final Set<String> UI_TAGS = Set.of("BARCAN-TAG-03", "BARCAN-TAG-11");
    public static final String CHECK_NAME = "design_excellence";
    public static final long MIN_SCREENSHOT_BYTES = 1024;
    public static final String DESKTOP_SCREENSHOT = "desktop-1440.png";
    public static final String MOBILE_SCREENSHOT = "mobile-375.png";
    public static final String LAYOUT_CHECK_JSON = "layout-check.json";

    private final JulesSessionRepository julesSessionRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.services.design.LayoutGeometryAuditService layoutGeometryAuditService;

    @Autowired
    public DesignExcellenceGate(JulesSessionRepository julesSessionRepository,
                                 GitHubPullRequestService gitHubPullRequestService,
                                 com.eneik.production.services.design.LayoutGeometryAuditService layoutGeometryAuditService) {
        this.julesSessionRepository = julesSessionRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.layoutGeometryAuditService = layoutGeometryAuditService != null ? layoutGeometryAuditService : new com.eneik.production.services.design.LayoutGeometryAuditService();
    }

    @Override
    public GateStage stage() {
        return GateStage.IMPLEMENTATION_RESULT;
    }

    @Override
    public boolean supports(TaskEntity task) {
        return task != null
                && task.getRole() != null
                && UI_TAGS.contains(task.getRole().getTag());
    }

    @Override
    public boolean isBuildPhaseExempt() {
        return true;
    }

    @Override
    public GateResult check(TaskEntity task) {
        // 2026-08-22 (ACP-105): the "not applicable -> passed" branch was removed. GateOrchestrator
        // already filters by supports(), so it was unreachable through the only caller - but it encoded
        // the idea that an unasked check is a passed one, which is the Evidence Algebra inverted: the
        // absence of a check is 0, never 5. Nothing is lost; supports() does this work.
        JulesSessionEntity session = resolveSessionWithPr(task);
        Integer pullNumber = session != null ? gitHubPullRequestService.parsePullNumber(session.getPrUrl()) : null;
        if (task.getProject() == null || pullNumber == null) {
            return new GateResult(false, CHECK_NAME, List.of("no PR found to verify design screenshots against"));
        }

        List<String> changedFiles = gitHubPullRequestService.fetchDiffText(task.getProject(), pullNumber)
                .map(GitHubPullRequestService::changedFilePathsFromDiff)
                .orElse(List.of());
        String headRef = gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), pullNumber)
                .map(GitHubPullRequestService.GitHubPullRequest::headRef)
                .orElse(null);

        String designCheckDir = designCheckDir(task);
        String desktopPath = designCheckDir + "desktop-1440.png";
        String mobilePath = designCheckDir + "mobile-375.png";

        long desktopSize = realFileSize(task, headRef, desktopPath, changedFiles);
        long mobileSize = realFileSize(task, headRef, mobilePath, changedFiles);
        boolean hasDesktop = desktopSize >= 0;
        boolean hasMobile = mobileSize >= 0;

        List<String> failureReasons = new ArrayList<>();
        int score = 0;

        // 1. has_both_screenshots (real files, confirmed present in the PR's own diff + fetched from
        // GitHub at the PR's real head ref): weight 30.
        if (hasDesktop && hasMobile) {
            score += 30;
        } else {
            if (!hasDesktop) failureReasons.add("missing real desktop screenshot at " + desktopPath);
            if (!hasMobile) failureReasons.add("missing real mobile screenshot at " + mobilePath);
        }

        // 2. responsive_ok (both present, real byte sizes genuinely differ, AND layout geometry verified): weight 40.
        // GROUPING_PROXIMITY_GATE (D011) & FALSIFICATION_HARNESS (D008):
        // File sizes differing is necessary but not sufficient:
        // - Viewport scalability must not be prohibited (user-scalable=no, maximum-scale=1.0)
        // - Markup and layout geometry must have 0 collisions (no overlapping menus/elements)
        boolean responsiveOk = false;
        if (hasDesktop && hasMobile) {
            if (desktopSize == mobileSize) {
                failureReasons.add("responsive check failed: screenshots have identical file sizes");
            } else {
                responsiveOk = true;
            }
        }

        boolean hasGeometryViolations = false;
        boolean hasVerifiableGeometryPassed = false;
        List<String> geometryFailureReasons = new ArrayList<>();

        for (String changedPath : changedFiles) {
            if (isMarkupOrLayoutPath(changedPath)) {
                java.util.Optional<String> contentOpt = gitHubPullRequestService.fetchFileContent(task.getProject(), headRef, changedPath);
                if (contentOpt.isPresent()) {
                    String content = contentOpt.get();
                    com.eneik.production.services.design.LayoutGeometryAuditService.LayoutAuditResult layoutResult =
                            layoutGeometryAuditService.auditLayout(content);
                    if (!layoutResult.scalable()) {
                        hasGeometryViolations = true;
                        geometryFailureReasons.add("viewport scalability prohibited in " + changedPath + ": user-scalable=no or maximum-scale=1 detected (violates mobile accessibility and prevents zoom recovery)");
                    }
                    if (layoutResult.hasCollisions()) {
                        hasGeometryViolations = true;
                        for (com.eneik.production.services.design.LayoutGeometryAuditService.Collision col : layoutResult.collisions()) {
                            geometryFailureReasons.add("responsive check failed: layout collision detected in " + changedPath + ": " + col.description());
                        }
                    }
                    if (layoutResult.isFailed() && !layoutResult.hasCollisions() && layoutResult.scalable()) {
                        hasGeometryViolations = true;
                        geometryFailureReasons.add("responsive check failed: " + layoutResult.verdictReason() + " in " + changedPath);
                    }
                    if (layoutResult.passed()) {
                        hasVerifiableGeometryPassed = true;
                    }
                }
            }
        }

        boolean layoutGeometryPassed = false;
        if (hasGeometryViolations) {
            failureReasons.addAll(geometryFailureReasons);
        } else if (hasVerifiableGeometryPassed) {
            layoutGeometryPassed = true;
        } else {
            // Epistemic three-value status: CANNOT_JUDGE ("геометрия не выводима")
            // When no bounding boxes exist in PR (e.g. layout-check.json missing), responsiveness cannot be judged.
            // 40 points are NOT awarded, preventing false green on diff file sizes alone.
            failureReasons.add("responsive check cannot be judged: геометрия не выводима (layout-check.json отсутствует или не содержит элементов для аудита); 40 баллов за отзывчивость не начислены");
        }

        if (responsiveOk && layoutGeometryPassed) {
            score += 40;
        }

        // 3. visual_qa_ok (real size > 1KB, not an empty/error placeholder): weight 30.
        boolean visualQaOk = true;
        if (hasDesktop && desktopSize <= MIN_SCREENSHOT_BYTES) {
            visualQaOk = false;
            failureReasons.add("desktop screenshot is too small or empty");
        }
        if (hasMobile && mobileSize <= MIN_SCREENSHOT_BYTES) {
            visualQaOk = false;
            failureReasons.add("mobile screenshot is too small or empty");
        }
        if (visualQaOk && (hasDesktop || hasMobile)) {
            score += 30;
        }

        boolean passed = score >= 70;
        return new GateResult(passed, CHECK_NAME, failureReasons);
    }

    public static String designCheckDir(TaskEntity task) {
        return ".eneik/records/design-check-" + task.getId() + "/";
    }

    public static String layoutCheckPath(TaskEntity task) {
        return designCheckDir(task) + LAYOUT_CHECK_JSON;
    }

    // -1 unless the path both appears as a real changed file in the PR's own diff AND is actually
    // fetchable from GitHub at the PR's head ref - a path merely mentioned in the diff header (e.g. a
    // rename or delete) without real fetchable bytes must not count as "present".
    private long realFileSize(TaskEntity task, String headRef, String path, List<String> changedFiles) {
        if (headRef == null || !changedFiles.contains(path)) {
            return -1;
        }
        return gitHubPullRequestService.fetchFileBytes(task.getProject(), headRef, path)
                .map(bytes -> (long) bytes.length)
                .orElse(-1L);
    }

    // Same implementer-session lookup used elsewhere (BackendContractGate, JulesDispatchService.
    // applyReviewVerdictToTask): prefer the session sitting at "pr_opened", else fall back to any session
    // that already has a PR.
    private JulesSessionEntity resolveSessionWithPr(TaskEntity task) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
        return sessions.stream()
                .filter(s -> "pr_opened".equals(s.getStatus()))
                .findFirst()
                .orElseGet(() -> sessions.stream().filter(s -> s.getPrUrl() != null).findFirst().orElse(null));
    }

    private boolean isMarkupOrLayoutPath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".svelte") || lower.endsWith(".vue")
                || lower.endsWith(".jsx") || lower.endsWith(".tsx")
                || lower.endsWith("layout.json") || lower.endsWith("layout-check.json");
    }
}
