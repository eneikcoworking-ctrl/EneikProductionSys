package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Popperian Falsification Suite for EpistemicLayerInvariantGate.
 * Validates epistemic invariants:
 * - D003: Quine Web of Belief & Holism Impact Map (periphery tasks forbidden from mutating core files).
 * - D002: Austin Category Error Scan (prevention of ontological contamination of client codebase with factory entities).
 * - D008: Popper Falsification Harness (real PR unified diff from GitHub overrides agent self-attested fileScope).
 * - D006: Raz Rights & Duties Matrix (unconditional non-exemption in buildPhase: isBuildPhaseExempt=false).
 */
class EpistemicLayerInvariantGateFalsificationTest {
    private static final String PR_URL = "https://github.com/org/repo/pull/101";

    private FeatureRepository featureRepository;
    private JulesSessionRepository julesSessionRepository;
    private GitHubPullRequestService gitHubPullRequestService;
    private EpistemicLayerInvariantGate gate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        featureRepository = mock(FeatureRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        gate = new EpistemicLayerInvariantGate(featureRepository, julesSessionRepository, gitHubPullRequestService);
        when(julesSessionRepository.findByTaskId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("D003 Quine Holism: Periphery tasks mutating migrations or security config in real PR diff are rejected")
    void falsifyQuineHolismImpactMap_peripheryTaskMutatingMigrationsOrSecurityConfigRejected() {
        TaskEntity uiTask = createPeripheryTask("BARCAN-TAG-11");
        stubSessionAndDiff(uiTask, 101, """
                diff --git a/src/main/resources/db/migration/V99__core_schema.sql b/src/main/resources/db/migration/V99__core_schema.sql
                --- a/src/main/resources/db/migration/V99__core_schema.sql
                +++ b/src/main/resources/db/migration/V99__core_schema.sql
                @@ -0,0 +1,5 @@
                +ALTER TABLE users ADD COLUMN is_admin BOOLEAN;
                diff --git a/src/main/java/com/acme/SecurityConfig.java b/src/main/java/com/acme/SecurityConfig.java
                --- a/src/main/java/com/acme/SecurityConfig.java
                +++ b/src/main/java/com/acme/SecurityConfig.java
                @@ -10,3 +10,4 @@
                +    .permitAll()
                """);

        assertThat(gate.supports(uiTask)).isTrue();
        GateResult result = gate.check(uiTask);

        assertThat(result.passed()).isFalse();
        assertThat(result.checkName()).isEqualTo("epistemic_layer_invariant");
        assertThat(result.failureReasons()).anyMatch(r ->
                r.contains("PERIPHERY task attempted to modify CORE file") && r.contains("V99__core_schema.sql"));
        assertThat(result.failureReasons()).anyMatch(r ->
                r.contains("PERIPHERY task attempted to modify CORE file") && r.contains("SecurityConfig.java"));
    }

    @Test
    @DisplayName("D002 Austin Category Error: Periphery task injecting factory orchestrator entities fails gate")
    void falsifyAustinCategoryErrorScan_peripheryTaskInjectingFactoryOrchestratorEntitiesRejected() {
        TaskEntity docTask = createPeripheryTask("BARCAN-TAG-05");
        stubSessionAndDiff(docTask, 102, """
                diff --git a/src/main/java/com/acme/client/AutoMergeBridge.java b/src/main/java/com/acme/client/AutoMergeBridge.java
                --- /dev/null
                +++ b/src/main/java/com/acme/client/AutoMergeBridge.java
                @@ -0,0 +1,5 @@
                +public class AutoMergeBridge {}
                diff --git a/src/main/java/com/acme/client/JulesCustomOrchestrator.java b/src/main/java/com/acme/client/JulesCustomOrchestrator.java
                --- /dev/null
                +++ b/src/main/java/com/acme/client/JulesCustomOrchestrator.java
                @@ -0,0 +1,5 @@
                +public class JulesCustomOrchestrator {}
                """);

        assertThat(gate.supports(docTask)).isTrue();
        GateResult result = gate.check(docTask);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(r ->
                r.contains("ONTOLOGICAL_CONTAMINATION") && r.contains("AutoMergeBridge.java"));
        assertThat(result.failureReasons()).anyMatch(r ->
                r.contains("ONTOLOGICAL_CONTAMINATION") && r.contains("JulesCustomOrchestrator.java"));
    }

    @Test
    @DisplayName("D008 Popper Falsification Harness: Real PR diff from GitHub overrides misleading fileScope claims")
    void falsifyPopperFalsificationHarness_realPrDiffOverridesMisleadingFileScopeClaims() {
        TaskEntity task = createPeripheryTask("BARCAN-TAG-11");
        // Agent claims innocuous scope in task metadata
        task.setFileScope("frontend/src/components/Navigation.svelte");

        // Empirical truth: the PR diff touches core migrations
        stubSessionAndDiff(task, 103, """
                diff --git a/src/main/resources/db/migration/V10__add_accounts.sql b/src/main/resources/db/migration/V10__add_accounts.sql
                --- a/src/main/resources/db/migration/V10__add_accounts.sql
                +++ b/src/main/resources/db/migration/V10__add_accounts.sql
                @@ -0,0 +1,3 @@
                +CREATE TABLE accounts (id UUID);
                """);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(r ->
                r.contains("PERIPHERY task attempted to modify CORE file") && r.contains("V10__add_accounts.sql"));
    }

    @Test
    @DisplayName("D003 Quine Web of Belief: Periphery modifications to UI/CSS/HTML pass without violations")
    void falsifyQuineWebOfBelief_peripheryTaskModifyingOnlyPeripheryFilesPassesGate() {
        TaskEntity task = createPeripheryTask("BARCAN-TAG-11");
        stubSessionAndDiff(task, 104, """
                diff --git a/frontend/src/App.svelte b/frontend/src/App.svelte
                --- a/frontend/src/App.svelte
                +++ b/frontend/src/App.svelte
                @@ -1,2 +1,3 @@
                +<h1>Welcome</h1>
                diff --git a/frontend/src/styles.css b/frontend/src/styles.css
                --- a/frontend/src/styles.css
                +++ b/frontend/src/styles.css
                @@ -1,2 +1,3 @@
                +:root { --bg: #fff; }
                """);

        GateResult result = gate.check(task);

        assertThat(result.passed()).isTrue();
        assertThat(result.failureReasons()).isEmpty();
    }

    @Test
    @DisplayName("D002 Austin Category Error: Internal .eneik records bypassed as legitimate factory reports")
    void falsifyAustinCategoryErrorScan_internalEneikRecordsBypassedAsFactoryReports() {
        TaskEntity qaTask = createPeripheryTask("BARCAN-TAG-06");
        // Internal reports may contain IDs or strings, but .eneik/ paths must not be flagged as contamination
        stubSessionAndDiff(qaTask, 105, """
                diff --git a/.eneik/records/qa-verification-123.json b/.eneik/records/qa-verification-123.json
                --- /dev/null
                +++ b/.eneik/records/qa-verification-123.json
                @@ -0,0 +1,5 @@
                +{"testsRun": 3, "testsPassed": 3, "testsFailed": 0, "verdict": "pass"}
                diff --git a/.eneik/records/design-check-456/desktop-1440.png b/.eneik/records/design-check-456/desktop-1440.png
                --- /dev/null
                +++ b/.eneik/records/design-check-456/desktop-1440.png
                @@ -0,0 +1,1 @@
                +binary
                """);

        GateResult result = gate.check(qaTask);

        assertThat(result.passed()).isTrue();
        assertThat(result.failureReasons()).isEmpty();
    }

    @Test
    @DisplayName("D006 Raz Duty Matrix: isBuildPhaseExempt is strictly false, stage is IMPLEMENTATION_RESULT, supports() accurate")
    void falsifyRazDutyMatrix_isBuildPhaseExemptIsStrictlyFalseAndStageIsImplementationResult() {
        // Essential invariant: core file protections hold unconditionally from day zero!
        assertThat(gate.isBuildPhaseExempt()).isFalse();
        assertThat(gate.stage()).isEqualTo(GateStage.IMPLEMENTATION_RESULT);

        // Verification of supports() logic
        TaskEntity uiTask = createPeripheryTask("BARCAN-TAG-11");
        TaskEntity docTask = createPeripheryTask("BARCAN-TAG-05");
        TaskEntity qaTask = createPeripheryTask("BARCAN-TAG-06");
        TaskEntity backendTask = createPeripheryTask("BARCAN-TAG-02");

        assertThat(gate.supports(uiTask)).isTrue();
        assertThat(gate.supports(docTask)).isTrue();
        assertThat(gate.supports(qaTask)).isTrue();
        assertThat(gate.supports(backendTask)).isFalse();
        assertThat(gate.supports(null)).isFalse();

        // Epistemic layer inheritance: backend role on PERIPHERY feature becomes supported
        UUID featureId = UUID.randomUUID();
        FeatureEntity peripheryFeature = new FeatureEntity();
        peripheryFeature.setId(featureId);
        peripheryFeature.setEpistemicLayer("PERIPHERY");
        when(featureRepository.findById(featureId)).thenReturn(Optional.of(peripheryFeature));

        backendTask.setFeatureId(featureId);
        assertThat(gate.supports(backendTask)).isTrue();
    }

    private TaskEntity createPeripheryTask(String roleTag) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("epistemic-test-proj");

        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        role.setDescription(roleTag + " Role");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setRole(role);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("role", roleTag);
        task.setPayload(payload);

        return task;
    }

    private void stubSessionAndDiff(TaskEntity task, int prNumber, String unifiedDiff) {
        String prUrl = "https://github.com/org/repo/pull/" + prNumber;
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setPrUrl(prUrl);
        session.setStatus("pr_opened");

        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(gitHubPullRequestService.parsePullNumber(prUrl)).thenReturn(prNumber);
        when(gitHubPullRequestService.fetchDiffText(eq(task.getProject()), eq(prNumber)))
                .thenReturn(Optional.of(unifiedDiff));
    }
}
