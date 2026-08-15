package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The referent test is the whole point of this layer, so it is pinned directly.
 *
 * The failure it prevents is measured, not hypothetical: on 2026-08-15 the factory repaired the product
 * twice (10:38, 12:00) and never re-observed, so a launch verdict taken at 10:21 kept philosophy
 * subordinated for the rest of the day. Age alone could not catch that - the observation was recent when
 * the first repair landed. Only asking whether the thing observed still exists can.
 */
class RuntimeVerdictLayerTest {

    private final ClientRuntimeObservationRepository observations = mock(ClientRuntimeObservationRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final GitHubPullRequestService github = mock(GitHubPullRequestService.class);
    private final RuntimeVerdictLayer layer = new RuntimeVerdictLayer(observations, projects, github);

    private final UUID projectId = UUID.randomUUID();

    private ClientRuntimeObservationEntity observation(boolean success, Instant at) {
        ClientRuntimeObservationEntity o = new ClientRuntimeObservationEntity();
        o.setId(UUID.randomUUID());
        o.setProjectId(projectId);
        o.setLaunchSuccess(success);
        o.setObservedAt(at);
        o.setErrorText(success ? null : "docker compose up failed");
        return o;
    }

    private void withProjectAndLastCommit(Instant lastCommit) {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(github.latestCommitTime(any(), anyString())).thenReturn(Optional.ofNullable(lastCommit));
    }

    @Test
    void aFailureIsNoLongerAuthoritativeOnceTheProductHasChanged() {
        Instant observedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId))
                .thenReturn(List.of(observation(false, observedAt)));
        withProjectAndLastCommit(observedAt.plus(5, ChronoUnit.MINUTES));

        Judgement j = layer.judge(projectId).get(0);

        assertThat(j.verdict())
                .as("the repair landed after the observation, so the failure describes a product that no "
                        + "longer exists - and the observation is only 30 minutes old, so no age-based "
                        + "rule could have caught this")
                .isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason()).contains("no longer exists");
    }

    @Test
    void aFailureStandsWhileTheProductIsUnchanged() {
        Instant observedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId))
                .thenReturn(List.of(observation(false, observedAt)));
        withProjectAndLastCommit(observedAt.minus(2, ChronoUnit.HOURS));

        Judgement j = layer.judge(projectId).get(0);

        assertThat(j.verdict())
                .as("nothing has changed since it was observed, so the failure is still a claim about the "
                        + "product that exists")
                .isEqualTo(Verdict.WITHHOLD);
        assertThat(j.reason()).contains("docker compose up failed");
    }

    @Test
    void successIsAlsoInvalidatedByALaterCommit() {
        Instant observedAt = Instant.now().minus(10, ChronoUnit.MINUTES);
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId))
                .thenReturn(List.of(observation(true, observedAt)));
        withProjectAndLastCommit(observedAt.plus(1, ChronoUnit.MINUTES));

        assertThat(layer.judge(projectId).get(0).verdict())
                .as("the rule is about the referent, not about which answer we would prefer - a stale "
                        + "success is exactly as uninformative as a stale failure")
                .isEqualTo(Verdict.ABSTAIN);
    }

    @Test
    void neverObservedIsDebtRatherThanApproval() {
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId)).thenReturn(List.of());

        Judgement j = layer.judge(projectId).get(0);

        assertThat(j.verdict()).isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason())
                .as("the reason must say WHICH kind of absence this is - an abstention with an unstated "
                        + "reason is indistinguishable from a layer that never ran")
                .contains("no runtime observation");
    }

    @Test
    void anUnreadableHistoryDegradesToCautionRatherThanToSilence() {
        Instant observedAt = Instant.now().minus(9, ChronoUnit.HOURS);
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId))
                .thenReturn(List.of(observation(true, observedAt)));
        withProjectAndLastCommit(null);

        assertThat(layer.judge(projectId).get(0).verdict())
                .as("an unverifiable claim about whether the product changed must not resolve as 'it did "
                        + "not' - the fallback bound is declared precisely so unreadability blocks")
                .isEqualTo(Verdict.ABSTAIN);
    }

    @Test
    void aFailingRepositoryDoesNotBreakTheLayer() {
        when(observations.findByProjectIdOrderByObservedAtDesc(projectId))
                .thenThrow(new IllegalStateException("db down"));

        Judgement j = layer.judge(projectId).get(0);

        assertThat(j.verdict()).isEqualTo(Verdict.ABSTAIN);
        assertThat(j.reason()).contains("db down");
    }
}
