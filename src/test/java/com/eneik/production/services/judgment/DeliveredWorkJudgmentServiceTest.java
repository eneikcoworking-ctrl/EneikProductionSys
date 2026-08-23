package com.eneik.production.services.judgment;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveredWorkJudgmentServiceTest {

    private static final String CRITERION =
            "Given a document is uploaded, When the search page is opened, Then it lists that document.";

    @Mock private TaskRepository taskRepository;
    @Mock private JulesSessionRepository julesSessionRepository;
    @Mock private GitHubPullRequestService gitHubPullRequestService;
    @Mock private JudgmentAgentClient judgmentAgentClient;
    @Mock private WishlistRepository wishlistRepository;

    @InjectMocks private DeliveredWorkJudgmentService service;

    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        // Mockito does not inject @Value fields. Without these three lines `enabled` is false, the service
        // returns at its first statement, and every assertion below passes without it ever running - which
        // is exactly how two green tests proved nothing on 2026-08-21.
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxPerCycle", 5);
        ReflectionTestUtils.setField(service, "diffCharLimit", 60_000);
        ReflectionTestUtils.setField(service, "maxConsecutiveSilences", 2);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("a refuted criterion becomes scope carrying the criterion and the judgment's own words")
    void refutedCriterionIsFiledAsScope() {
        TaskEntity task = deliveredTask(CRITERION);
        givenMergedPullRequest(task, "https://github.com/acme/app/pull/50", "diff --git a/README.md");
        when(judgmentAgentClient.judgeAsText(anyString(), anyString()))
                .thenReturn("REFUTED\n\nThe diff adds a README section and no list is rendered anywhere.");

        service.judgeDeliveredWork(project);

        ArgumentCaptor<WishlistEntity> filed = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(filed.capture());
        assertThat(filed.getValue().getSource()).isEqualTo(WishlistSource.delivery_refuted);
        assertThat(filed.getValue().getContent())
                .contains(task.getId().toString())
                .contains(CRITERION)
                .contains("no list is rendered anywhere");
        assertThat(filed.getValue().getAcceptanceCriteria()).isNotBlank();
    }

    @Test
    @DisplayName("a satisfied criterion files nothing")
    void satisfiedCriterionFilesNothing() {
        TaskEntity task = deliveredTask(CRITERION);
        givenMergedPullRequest(task, "https://github.com/acme/app/pull/51", "diff --git a/List.svelte");
        when(judgmentAgentClient.judgeAsText(anyString(), anyString()))
                .thenReturn("SATISFIED\n\nList.svelte renders the uploaded documents.");

        service.judgeDeliveredWork(project);

        verify(wishlistRepository, never()).save(any());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("a task carrying no criterion is never sent to be judged")
    void taskWithoutCriteriaIsNotJudged() {
        deliveredTask(null);

        service.judgeDeliveredWork(project);

        verify(judgmentAgentClient, never()).judgeAsText(anyString(), anyString());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("one silence records no verdict, so the task is judged again next tick")
    void sidecarSilenceLeavesTheTaskUnjudged() {
        TaskEntity task = deliveredTask(CRITERION);
        givenMergedPullRequest(task, "https://github.com/acme/app/pull/52", "diff --git a/App.java");
        when(judgmentAgentClient.judgeAsText(anyString(), anyString())).thenReturn(null);

        service.judgeDeliveredWork(project);

        // Updated 2026-08-23 with F11. A single silence is still read as a fact about the instrument, so no
        // verdict is written and the task stays in the queue - that part is unchanged and is what this test
        // has always pinned. What changed is that the silence is now COUNTED, because a silence that keeps
        // repeating is a fact about the input instead, and the old contract of writing nothing at all is
        // exactly what let one oversized task retry for ever and crash the shared sidecar each time.
        assertThat(task.getPayload().path("acceptance_verdict").asText(null)).isNull();
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("a closed task with no pull request is recorded as unjudged, never as verified")
    void taskWithoutDiffIsRecordedAsNotJudged() {
        TaskEntity task = deliveredTask(CRITERION);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of());

        service.judgeDeliveredWork(project);

        verify(taskRepository).save(task);
        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.NOT_JUDGED_NO_DIFF);
        verify(wishlistRepository, never()).save(any());
        verify(judgmentAgentClient, never()).judgeAsText(anyString(), anyString());
    }

    @Test
    @DisplayName("a task already carrying a verdict is not judged a second time")
    void alreadyJudgedTaskIsSkipped() {
        TaskEntity task = deliveredTask(CRITERION);
        task.setAcceptanceCriteria(CRITERION);
        ((com.fasterxml.jackson.databind.node.ObjectNode) task.getPayload())
                .put("acceptance_verdict", DeliveredWorkJudgmentService.SATISFIED);

        service.judgeDeliveredWork(project);

        verify(judgmentAgentClient, never()).judgeAsText(anyString(), anyString());
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("a criterion that is only the compiler's process boilerplate is undecidable, and costs nothing")
    void fabricatedCriterionIsUndecidableAndNeverReachesTheSidecar() {
        // D3, 2026-08-23. Until today englishMetadata replaced the client's stated criterion with three
        // process statements whenever it was written in the client's own language - on this project, every
        // one. None of them can be false of a product that does nothing the client asked for, so a
        // SATISFIED against them is a verdict about tidiness. Decided before any diff is fetched: an
        // unfalsifiable input is not worth a network call, let alone a paid one.
        TaskEntity task = deliveredTask(
                com.eneik.production.services.compiler.TechnicalLeadCompiler.PROCESS_ACCEPTANCE_CRITERIA);

        service.judgeDeliveredWork(project);

        verify(judgmentAgentClient, never()).judgeAsText(anyString(), anyString());
        verify(gitHubPullRequestService, never()).fetchDiffText(any(), anyInt());
        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.UNDECIDABLE);
    }

    @Test
    @DisplayName("silence that repeats becomes a fact about the input, not another retry of it")
    void repeatedSilenceStopsBeingTreatedAsAnUnavailableInstrument() {
        // F11, 2026-08-23. Measured: task d94c75cb killed the shared sidecar three times out of three with
        // `spawn E2BIG`, because its diff does not fit the argument the sidecar spawns. Every null was read
        // as "the instrument is down", so the input that breaks it was retried for ever - the absorbing
        // state at the head of the queue that JudgmentAgentClient's own javadoc warns against.
        TaskEntity task = deliveredTask(CRITERION);
        givenMergedPullRequest(task, "https://github.com/acme/app/pull/60", "diff --git a/Huge.java");
        when(judgmentAgentClient.judgeAsText(anyString(), anyString())).thenReturn(null);

        service.judgeDeliveredWork(project);

        // First silence: still a fact about the instrument, so no verdict and the task stays in the queue.
        assertThat(task.getPayload().path("acceptance_verdict").asText(null)).isNull();
        assertThat(task.getPayload().path("acceptance_silence_count").asInt()).isEqualTo(1);

        service.judgeDeliveredWork(project);

        // Second: the input is what cannot be carried, and saying so is what lets the queue move on.
        assertThat(task.getPayload().path("acceptance_verdict").asText())
                .isEqualTo(DeliveredWorkJudgmentService.UNDECIDABLE);
        assertThat(task.getPayload().path("acceptance_verdict_reason").asText())
                .contains("could not carry this input");
    }

    private TaskEntity deliveredTask(String criterion) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Render the uploaded documents");
        task.setDescription("Show what the server actually holds.");
        task.setStatus(TaskStatus.done);
        if (criterion != null) {
            task.setAcceptanceCriteria(criterion);
        }
        when(taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(
                project.getId(), TaskStatus.done)).thenReturn(List.of(task));
        return task;
    }

    private void givenMergedPullRequest(TaskEntity task, String prUrl, String diff) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setPrUrl(prUrl);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(prUrl))
                .thenReturn(Integer.valueOf(prUrl.substring(prUrl.lastIndexOf('/') + 1)));
        when(gitHubPullRequestService.fetchDiffText(eq(project), anyInt())).thenReturn(Optional.of(diff));
    }
}
