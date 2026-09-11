package com.eneik.production.services.accounts;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.ProjectFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Coherence test for Prescription 22 (PART_WHOLE_OWNERSHIP / D004, Law 12).
 * Verifies that the SQL admission selection (AccountRepository.lockNextJulesAccountWithCapacity)
 * and the Java diagnostic mirror (ProjectFlowService.evaluateGeneralPoolAdmissionDecision)
 * are strictly coherent:
 *
 * <p>Invariant:
 *   lockNextJulesAccountWithCapacity(...).isEmpty() <=> evaluateGeneralPoolAdmissionDecision(...).outcome() != ADMITTED
 * And when empty, the Java mirror names the exact conjunct violated in the database.
 */
@DataJpaTest
@ActiveProfiles("test")
class GeneralPoolAdmissionCoherenceIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    private ProjectFlowService service;

    private static final int DEFAULT_MAX_CONCURRENT = 3;
    private static final int DEFAULT_MAX_DAILY = 15;

    @BeforeEach
    void setUp() {
        entityManager.getEntityManager().createQuery("DELETE FROM JulesSessionEntity").executeUpdate();
        entityManager.getEntityManager().createQuery("DELETE FROM TaskEntity").executeUpdate();
        entityManager.getEntityManager().createQuery("DELETE FROM AccountEntity").executeUpdate();

        service = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "taskRepository", mock(TaskRepository.class));
        ReflectionTestUtils.setField(service, "claimService", mock(ClaimService.class));
        ReflectionTestUtils.setField(service, "maxConcurrentJulesSessionsPerAccount", DEFAULT_MAX_CONCURRENT);
        ReflectionTestUtils.setField(service, "maxDailySessionsPerAccount", DEFAULT_MAX_DAILY);
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    @DisplayName("Coherence: Eligible account is locked by SQL and admitted by Java decision")
    void coherentWhenEligible() {
        AccountEntity account = persistAccount("eligible-acc", true, AccountStatus.idle, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isPresent();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.ADMITTED);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Disabled account is rejected by SQL and reported as DISABLED by Java decision")
    void coherentWhenDisabled() {
        AccountEntity account = persistAccount("disabled-acc", false, AccountStatus.idle, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.DISABLED);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Daily limit exceeded account is rejected by SQL and reported as DAILY_LIMIT_EXCEEDED by Java decision")
    void coherentWhenDailyLimitExceeded() {
        AccountEntity account = persistAccount("daily-limit-acc", true, AccountStatus.idle, "*", null, 15, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.DAILY_LIMIT_EXCEEDED);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Account in daily_limited status is rejected by SQL and reported as RESTING by Java decision")
    void coherentWhenRestingDailyLimited() {
        AccountEntity account = persistAccount("resting-daily-acc", true, AccountStatus.daily_limited, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.RESTING);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Account in api_blocked status is rejected by SQL and reported as RESTING by Java decision")
    void coherentWhenRestingApiBlocked() {
        AccountEntity account = persistAccount("resting-blocked-acc", true, AccountStatus.api_blocked, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.RESTING);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Decommissioned account is rejected by SQL and reported as RETIRED by Java decision")
    void coherentWhenRetiredDecommissioned() {
        AccountEntity account = persistAccount("decom-acc", false, AccountStatus.decommissioned, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.RETIRED);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Offline account is rejected by SQL and reported as RETIRED by Java decision")
    void coherentWhenRetiredOffline() {
        AccountEntity account = persistAccount("offline-acc", true, AccountStatus.offline, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.RETIRED);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Concurrent sessions exhausted account is rejected by SQL and reported as SESSIONS_EXHAUSTED by Java decision")
    void coherentWhenConcurrentSessionsExhausted() {
        AccountEntity account = persistAccount("concurrent-full-acc", true, AccountStatus.idle, "*", null, 0, 15, 1);
        RoleEntity role = persistRole("BARCAN-TAG-11");
        TaskEntity activeTask = persistTask(role, TaskStatus.in_progress);
        persistSession(account.getId(), activeTask.getId(), "running");
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", 1, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), null, taskId, 1, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.SESSIONS_EXHAUSTED);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Role capability mismatch account is rejected by SQL and reported as EXCLUDED_BY_RULE by Java decision")
    void coherentWhenCapabilityMismatch() {
        AccountEntity account = persistAccount("cap-mismatch-acc", true, AccountStatus.idle, "BARCAN-TAG-01", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-02", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-02", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.EXCLUDED_BY_RULE);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Account in excludedAccountNames CSV is rejected by SQL and reported as EXCLUDED_BY_RULE by Java decision")
    void coherentWhenExcludedByExcludedAccountNames() {
        AccountEntity account = persistAccount("excluded-name-acc", true, AccountStatus.idle, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, "excluded-name-acc");
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of("excluded-name-acc"), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.EXCLUDED_BY_RULE);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Account excluded for this attempt is rejected by SQL and reported as EXCLUDED_BY_RULE by Java decision")
    void coherentWhenExcludedForThisAttempt() {
        AccountEntity account = persistAccount("attempt-excluded-acc", true, AccountStatus.idle, "*", null, 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                null, "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, "attempt-excluded-acc", DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, null, "BARCAN-TAG-11", Set.of(), "attempt-excluded-acc", taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.EXCLUDED_BY_RULE);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    @Test
    @DisplayName("Coherence: Account assigned to a different project is rejected by SQL and reported as EXCLUDED_BY_RULE by Java decision")
    void coherentWhenProjectMismatch() {
        ProjectEntity otherProj = persistProject("other-project");
        ProjectEntity myProj = persistProject("my-project");

        AccountEntity account = persistAccount("project-scoped-acc", true, AccountStatus.idle, "*", otherProj.getId(), 0, 15, 3);
        entityManager.flush();
        entityManager.clear();

        UUID taskId = UUID.randomUUID();
        Optional<AccountEntity> locked = accountRepository.lockNextJulesAccountWithCapacity(
                myProj.getId(), "BARCAN-TAG-11", DEFAULT_MAX_CONCURRENT, null, DEFAULT_MAX_DAILY, null);
        ProjectFlowService.GeneralPoolAdmissionDecision decision = service.evaluateGeneralPoolAdmissionDecision(
                locked, myProj.getId(), "BARCAN-TAG-11", Set.of(), null, taskId, DEFAULT_MAX_CONCURRENT, DEFAULT_MAX_DAILY);

        assertThat(locked).isEmpty();
        assertThat(decision.outcome()).isEqualTo(AccountAdmissionOutcome.EXCLUDED_BY_RULE);
        assertThat(locked.isEmpty()).isEqualTo(decision.outcome() != AccountAdmissionOutcome.ADMITTED);
    }

    private ProjectEntity persistProject(String name) {
        ProjectEntity project = new ProjectEntity();
        project.setName(name);
        project.setSlug(name.toLowerCase());
        project.setRepositoryName(name);
        project.setRepositoryUrl("https://github.com/eneikcoworking-ctrl/" + name);
        project.setStatus(ProjectStatus.active);
        entityManager.persist(project);
        return project;
    }

    private AccountEntity persistAccount(String name, boolean enabled, AccountStatus status,
                                         String capabilities, UUID currentProjectId,
                                         int dispatchedToday, Integer dailyCapacity, Integer concurrentCapacity) {
        AccountEntity account = new AccountEntity();
        account.setName(name);
        account.setEnabled(enabled);
        account.setStatus(status);
        account.setApiKey("test-api-key");
        account.setCapabilities(capabilities);
        account.setCurrentProjectId(currentProjectId);
        account.setSessionsDispatchedToday(dispatchedToday);
        account.setEstimatedDailyCapacity(dailyCapacity);
        account.setEstimatedConcurrentCapacity(concurrentCapacity);
        entityManager.persist(account);
        return account;
    }

    private RoleEntity persistRole(String tag) {
        RoleEntity existing = entityManager.find(RoleEntity.class, tag);
        if (existing != null) {
            return existing;
        }
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setRulesPath(tag + ".md");
        entityManager.persist(role);
        return role;
    }

    private TaskEntity persistTask(RoleEntity role, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("test task");
        task.setStatus(status);
        entityManager.persist(task);
        return task;
    }

    private void persistSession(UUID accountId, UUID taskId, String status) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setAccountId(accountId);
        session.setTaskId(taskId);
        session.setStatus(status);
        entityManager.persist(session);
    }
}
