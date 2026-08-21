package com.eneik.production.services.judgment;

import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.InvariantStatusChangeEntity;
import com.eneik.production.repositories.InvariantStatusChangeRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The properties that make this a judgment layer rather than a cron job that spends money.
 *
 * Chiefly: no refutation, no call. Confirmations are unbounded and free of information, so a cycle that
 * finds no transition must cost one query and nothing else. The rest pin that a transition is never
 * marked judged unless it actually was - a failed call leaves the row for a retry, because losing a
 * refutation to a network blip is precisely the failure this table was created to end.
 */
class FactoryJudgmentServiceTest {

    private InvariantStatusChangeRepository repository;
    private JudgmentAgentClient client;
    private KaizenService kaizenService;
    private SystemSettingsService settings;
    private FactoryJudgmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(InvariantStatusChangeRepository.class);
        client = mock(JudgmentAgentClient.class);
        kaizenService = mock(KaizenService.class);
        settings = mock(SystemSettingsService.class);
        when(settings.effectiveBoolean("judgment_agent_enabled")).thenReturn(true);
        when(repository.findByInvariantKeyOrderByObservedAtDesc(anyString())).thenReturn(List.of());

        service = new FactoryJudgmentService(repository, client, kaizenService, settings);
        ReflectionTestUtils.setField(service, "maxPerCycle", 5);
        ReflectionTestUtils.setField(service, "contextHistoryLimit", 6);
    }

    private JudgmentAgentClient.Ruling ruling(JudgmentAgentClient.Outcome outcome, String reason,
                                              String title, String action) {
        return new JudgmentAgentClient.Ruling(outcome, reason, title, action);
    }

    private JudgmentAgentClient.Ruling unavailable() {
        return new JudgmentAgentClient.Ruling(JudgmentAgentClient.Outcome.UNAVAILABLE, "endpoint down", "", "");
    }

    private InvariantStatusChangeEntity transition(String key, String from, String to) {
        InvariantStatusChangeEntity entity = new InvariantStatusChangeEntity();
        entity.setId(UUID.randomUUID());
        entity.setInvariantKey(key);
        entity.setPreviousStatus(from);
        entity.setStatus(to);
        entity.setStatement(key + " must hold");
        entity.setEvidence("evidence for " + key);
        entity.setObservedAt(Instant.now());
        return entity;
    }

    @Test
    void noRefutationMeansNoCallAtAll() {
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of());

        service.judgeOutstandingRefutations();

        // The whole cost of a quiet cycle: one indexed query. Not a token, not a model invocation.
        verifyNoInteractions(client);
        verifyNoInteractions(kaizenService);
    }

    @Test
    void aBaselineRegistrationIsNotARefutationAndNeverReachesTheAgent() {
        // Found by the judgment layer on its first live cycle, 2026-08-21: all five rows it was given
        // were `null -> pass|warn` and it answered ABSTAIN five times with one reason - a baseline entry
        // is not a transition away from an asserted property. Five rulings at roughly $0.30 each on rows
        // that were knowably uninformative. The queue is what filters them, so the queue is what is
        // pinned here: the service asks a query that cannot return them at all.
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc())
                .thenReturn(List.of());

        service.judgeOutstandingRefutations();

        // The old query no longer exists on the repository at all - the exclusion is in the type, not in
        // a filter someone must remember to apply. Deleting it is what makes this unforgettable.
        verifyNoInteractions(client);
    }

    @Test
    void aDisabledAgentDoesNotEvenQuery() {
        when(settings.effectiveBoolean("judgment_agent_enabled")).thenReturn(false);

        service.judgeOutstandingRefutations();

        verifyNoInteractions(repository);
        verifyNoInteractions(client);
    }

    @Test
    void anAbstainMarksTheTransitionJudgedAndFilesNothing() {
        InvariantStatusChangeEntity entity = transition("done_is_not_delivery", "pass", "warn");
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(entity));
        when(client.judge(anyString()))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.ABSTAIN, "explained by the open migration", "", ""));

        service.judgeOutstandingRefutations();

        verifyNoInteractions(kaizenService);
        assertThat(entity.getJudgedAt()).isNotNull();
        verify(repository).save(entity);
    }

    @Test
    void aFindingIsFiledAgainstTheInvariantNotAgainstTheWholeSystem() {
        InvariantStatusChangeEntity entity = transition("delivered_requires_evidence", "pass", "fail");
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(entity));
        when(client.judge(anyString()))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.FINDING, "no merge evidence is being written",
                        "Delivery marked without evidence", "Write the merge SHA at the write"));

        service.judgeOutstandingRefutations();

        ArgumentCaptor<String> component = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(kaizenService).recordSystemicDefectProposal(
                eq(null), eq("Global"), component.capture(),
                eq("Delivery marked without evidence"), action.capture());

        // The Kaizen read path deduplicates on category + targetComponent. Passing the whole system as the
        // component is what collapsed every systemic finding onto one key on 2026-08-17; an invariant key
        // is a designator that picks out which finding is meant.
        assertThat(component.getValue()).isEqualTo("invariant:delivered_requires_evidence");
        assertThat(action.getValue()).contains("Write the merge SHA at the write");
        assertThat(action.getValue()).contains("no merge evidence is being written");
        assertThat(entity.getJudgedAt()).isNotNull();
    }

    @Test
    void aFailedCallLeavesTheTransitionUnjudgedForRetry() {
        InvariantStatusChangeEntity entity = transition("runtime_status_affects_trust", "pass", "warn");
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(entity));
        when(client.judge(anyString())).thenReturn(unavailable());

        service.judgeOutstandingRefutations();

        assertThat(entity.getJudgedAt()).isNull();
        verify(repository, never()).save(any(InvariantStatusChangeEntity.class));
    }

    @Test
    void anUnjudgeableTransitionIsMarkedReadSoItCannotBlockTheQueueForever() {
        // The head-of-line case. A declined or off-schema answer is deterministic in its input: leaving
        // the row unjudged makes it an absorbing state and every later refutation is lost behind it.
        InvariantStatusChangeEntity poison = transition("poison", "pass", "warn");
        InvariantStatusChangeEntity behind = transition("behind_it", "pass", "warn");
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(poison, behind));
        when(client.judge(anyString()))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.UNJUDGEABLE, "declined", "", ""))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.ABSTAIN, "no defect", "", ""));

        service.judgeOutstandingRefutations();

        assertThat(poison.getJudgedAt()).isNotNull();
        assertThat(behind.getJudgedAt()).isNotNull();
        verify(client, times(2)).judge(anyString());
    }

    @Test
    void aCycleThatRulesOnNothingReportsTheJudgmentLayerItself() {
        // Draining rows quietly while judging none is worse than blocking: the block at least stopped
        // visibly. The layer must say so rather than empty the backlog in silence.
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(
                transition("a", "pass", "warn"), transition("b", "pass", "warn")));
        when(client.judge(anyString()))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.UNJUDGEABLE, "declined", "", ""));

        service.judgeOutstandingRefutations();

        verify(kaizenService).recordSystemicDefectProposal(
                eq(null), eq("Global"), eq("FactoryJudgmentService"), anyString(), anyString());
    }

    @Test
    void ordinaryRulingsDoNotReportTheJudgmentLayer() {
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(
                transition("a", "pass", "warn"), transition("b", "pass", "warn")));
        when(client.judge(anyString()))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.UNJUDGEABLE, "declined", "", ""))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.ABSTAIN, "no defect", "", ""));

        service.judgeOutstandingRefutations();

        verifyNoInteractions(kaizenService);
    }

    @Test
    void aFailedCallStopsTheCycleRatherThanBurningTheBacklogAgainstADeadEndpoint() {
        InvariantStatusChangeEntity first = transition("a", "pass", "warn");
        InvariantStatusChangeEntity second = transition("b", "pass", "warn");
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(first, second));
        when(client.judge(anyString())).thenReturn(unavailable());

        service.judgeOutstandingRefutations();

        verify(client, times(1)).judge(anyString());
    }

    @Test
    void oneCycleIsBoundedSoAFirstRunDrainsABacklogRatherThanBurstingThroughIt() {
        ReflectionTestUtils.setField(service, "maxPerCycle", 2);
        when(repository.findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc()).thenReturn(List.of(
                transition("a", "pass", "warn"),
                transition("b", "pass", "warn"),
                transition("c", "pass", "warn"),
                transition("d", "pass", "warn")));
        when(client.judge(anyString()))
                .thenReturn(ruling(JudgmentAgentClient.Outcome.ABSTAIN, "no defect", "", ""));

        service.judgeOutstandingRefutations();

        verify(client, times(2)).judge(anyString());
    }

    @Test
    void thePromptCarriesTheTransitionAndItsOwnPriorHistory() {
        InvariantStatusChangeEntity entity = transition("done_is_not_delivery", "pass", "warn");
        InvariantStatusChangeEntity earlier = transition("done_is_not_delivery", "warn", "pass");
        when(repository.findByInvariantKeyOrderByObservedAtDesc("done_is_not_delivery"))
                .thenReturn(List.of(entity, earlier));

        String prompt = service.buildPrompt(entity);

        assertThat(prompt).contains("done_is_not_delivery");
        assertThat(prompt).contains("pass -> warn");
        assertThat(prompt).contains("evidence for done_is_not_delivery");
        assertThat(prompt).contains("factory-wide");
        // The transition being ruled on must not appear in its own history, and the earlier one must.
        assertThat(prompt).contains("Earlier transitions of this same invariant");
        assertThat(prompt).contains("warn -> pass");
    }

    @Test
    void aFactoryWideInvariantStillGetsItsHistory() {
        // A derived query on a null project_id compiles to `project_id = null` and matches nothing, which
        // would silently blank the history of exactly the invariants that are factory-wide.
        InvariantStatusChangeEntity entity = transition("agent_claims_are_weak_evidence", "pass", "warn");
        assertThat(entity.getProjectId()).isNull();
        InvariantStatusChangeEntity earlier = transition("agent_claims_are_weak_evidence", null, "pass");
        when(repository.findByInvariantKeyOrderByObservedAtDesc("agent_claims_are_weak_evidence"))
                .thenReturn(List.of(entity, earlier));

        assertThat(service.buildPrompt(entity)).contains("(none) -> pass");
    }
}
