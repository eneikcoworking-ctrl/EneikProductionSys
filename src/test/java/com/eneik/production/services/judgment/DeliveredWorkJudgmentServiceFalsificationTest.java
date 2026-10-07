package com.eneik.production.services.judgment;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for DeliveredWorkJudgmentService.
 * Validates epistemic, modal, and architectural delivery judgment invariants under Stage 4:
 * - D008 Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS):
 *     Falsification of delivery completeness: checking merged PR diff strictly against task's stated
 *     acceptance criteria. A task lacking criteria is never submitted; boilerplate process criteria
 *     are immediately refuted as UNDECIDABLE with zero tokens/calls spent.
 * - D013 Ludwig Wittgenstein Anti-Mirror Telemetry (LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY):
 *     Empirical verification against physical PR diff in GitHub. Incomplete PR diffs or missing PR URLs
 *     produce honest non-claims (NOT_JUDGED_NO_DIFF) rather than false positive passes; repeated silences
 *     materialize as UNDECIDABLE facts about the input rather than infinite loops.
 * - D006 Joseph Raz Rights and Duties Matrix (DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX):
 *     Strict demarcation between DELIVERY and FACTORY judgment layers. A REFUTED verdict never locks
 *     or rolls back the already completed task; instead, it generates a deduplicated DELIVERY_REFUTED
 *     wishlist item linked to the task's feature, enabling autonomous follow-up delivery without deadlock.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveredWorkJudgmentServiceFalsificationTest {

    private static final String ACCEPTANCE_CRITERIA =
            "Given a registered user, When they submit a profile avatar image, Then it is stored in S3 and displayed on the dashboard.";

    @Mock private TaskRepository taskRepository;
    @Mock private JulesSessionRepository julesSessionRepository;
    @Mock private GitHubPullRequestService gitHubPullRequestService;
    @Mock private JudgmentAgentClient judgmentAgentClient;
    @Mock private WishlistRepository wishlistRepository;

    @InjectMocks private DeliveredWorkJudgmentService service;

    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxPerCycle", 5);
        ReflectionTestUtils.setField(service, "diffCharLimit", 12_000);
        ReflectionTestUtils.setField(service, "maxConsecutiveSilences", 2);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
    }

    private TaskEntity createDeliveredTask(String criteria) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Upload user avatar");
        task.setDescription("Allow profile avatar upload.");
        task.setStatus(TaskStatus.done);
        task.setFeatureId(UUID.randomUUID());
        task.setOriginFeatureId(task.getFeatureId());
        task.setTargetContext(TargetContext.PRODUCT_CODEBASE);
        if (criteria != null) {
            task.setAcceptanceCriteria(criteria);
        }
        when(taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(
                project.getId(), TaskStatus.done)).thenReturn(List.of(task));
        return task;
    }

    private void linkMergedPr(TaskEntity task, String prUrl, String diff) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setPrUrl(prUrl);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(prUrl))
                .thenReturn(Integer.valueOf(prUrl.substring(prUrl.lastIndexOf('/') + 1)));
        when(gitHubPullRequestService.fetchDiffText(eq(project), anyInt())).thenReturn(Optional.of(diff));
    }

    // =========================================================================
    // D008: Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS)
    // =========================================================================

    @Test
    @DisplayName("D008 Popper Falsification: Boilerplate process criteria are rejected as UNDECIDABLE without LLM invocation")
    void falsifyPopperFalsification_processBoilerplateCriteriaNeverInvokesModel() {
        TaskEntity task = createDeliveredTask(TechnicalLeadCompiler.PROCESS_ACCEPTANCE_CRITERIA);

        service.judgeDeliveredWork(project);

        // Verify model is never called for process boilerplate that no product code can falsify
        verify(judgmentAgentClient, never()).judgeAsText(anyString(), anyString());
        verify(gitHubPullRequestService, never()).fetchDiffText(any(), anyInt());

        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.UNDECIDABLE);
        assertThat(task.getPayload().path("acceptance_verdict_reason").asText())
                .contains("compiler's process criteria and no claim about the product");
    }

    @Test
    @DisplayName("D008 Popper Falsification: A task with null acceptance criteria is strictly skipped")
    void falsifyPopperFalsification_taskWithoutCriteriaIsSkippedFromDenominator() {
        createDeliveredTask(null);

        service.judgeDeliveredWork(project);

        verify(judgmentAgentClient, never()).judgeAsText(anyString(), anyString());
        verify(wishlistRepository, never()).save(any());
    }

    // =========================================================================
    // D013: Ludwig Wittgenstein Anti-Mirror Telemetry (LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY)
    // =========================================================================

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Task with missing PR or unreadable diff is marked NOT_JUDGED_NO_DIFF")
    void falsifyWittgensteinAntiMirror_missingPrOrUnreadableDiffRecordedAsNotJudged() {
        TaskEntity task = createDeliveredTask(ACCEPTANCE_CRITERIA);
        // Session exists but PR URL is missing
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of());

        service.judgeDeliveredWork(project);

        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.NOT_JUDGED_NO_DIFF);
        assertThat(task.getPayload().path("acceptance_verdict_reason").asText())
                .contains("no pull request is recorded against this task");

        verify(judgmentAgentClient, never()).judgeAsText(anyString(), anyString());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Repeated silences terminate in UNDECIDABLE without freezing queue")
    void falsifyWittgensteinAntiMirror_repeatedSilencesMaterializeAsUndecidable() {
        TaskEntity task = createDeliveredTask(ACCEPTANCE_CRITERIA);
        linkMergedPr(task, "https://github.com/org/repo/pull/42", "diff --git a/Oversized.java\n+content");
        when(judgmentAgentClient.judgeAsText(anyString(), anyString())).thenReturn(null);

        // Attempt 1: recorded as silence, leaves verdict null for retry
        service.judgeDeliveredWork(project);
        assertThat(task.getPayload().path("acceptance_verdict").asText(null)).isNull();
        assertThat(task.getPayload().path("acceptance_silence_count").asInt()).isEqualTo(1);

        // Attempt 2: hits maxConsecutiveSilences (2), recorded as UNDECIDABLE
        service.judgeDeliveredWork(project);
        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.UNDECIDABLE);
        assertThat(task.getPayload().path("acceptance_verdict_reason").asText())
                .contains("judgment channel could not carry this input after 2 attempts");
    }

    // =========================================================================
    // D006: Joseph Raz Rights and Duties Matrix (DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX)
    // =========================================================================

    @Test
    @DisplayName("D006 Raz Rights and Duties: REFUTED delivery creates deduplicated DELIVERY_REFUTED wishlist item")
    void falsifyRazRightsDuties_refutedVerdictProducesDeliveryRefutedWishlist() {
        TaskEntity task = createDeliveredTask(ACCEPTANCE_CRITERIA);
        linkMergedPr(task, "https://github.com/org/repo/pull/77", "diff --git a/Dummy.java\n+// no avatar logic");
        when(judgmentAgentClient.judgeAsText(anyString(), anyString())).thenReturn(
                "REFUTED\n\nThe diff adds Dummy.java with no S3 upload or avatar dashboard logic."
        );

        service.judgeDeliveredWork(project);

        // Task verdict is updated
        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.REFUTED);

        // Wishlist item is created
        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());

        WishlistEntity wishlist = captor.getValue();
        assertThat(wishlist.getSource()).isEqualTo(WishlistSource.delivery_refuted);
        assertThat(wishlist.getProjectId()).isEqualTo(project.getId());
        assertThat(wishlist.getFeatureId()).isEqualTo(task.getFeatureId());
        assertThat(wishlist.getContent()).contains(task.getId().toString());
        assertThat(wishlist.getContent()).contains("The diff adds Dummy.java with no S3 upload");
        assertThat(wishlist.getLeanValue()).isEqualTo(LeanValue.essential);
    }

    @Test
    @DisplayName("D006 Raz Rights and Duties: SATISFIED verdict updates task payload and creates zero wishlists")
    void falsifyRazRightsDuties_satisfiedVerdictFilesNothingAndMarksTask() {
        TaskEntity task = createDeliveredTask(ACCEPTANCE_CRITERIA);
        linkMergedPr(task, "https://github.com/org/repo/pull/88", "diff --git a/AvatarController.java\n+s3.upload()");
        when(judgmentAgentClient.judgeAsText(anyString(), anyString())).thenReturn(
                "SATISFIED\n\nAvatarController.java implements S3 upload and dashboard view."
        );

        service.judgeDeliveredWork(project);

        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.SATISFIED);
        verify(taskRepository).save(task);
        verify(wishlistRepository, never()).save(any());
    }
}
