package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.gate.GateResult;
import com.eneik.production.services.gate.GateStage;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verification of EpistemicLayerInvariantGate
 * (KARL_POPPER_01_FALSIFICATION_HARNESS / D008; UILLARD_KUAYN_01_HOLISM_IMPACT_MAP / D003).
 *
 * Verifies that the primary ground truth for epistemic layer demarcation is the real PR diff
 * rather than self-attested fileScope strings.
 */
class EpistemicLayerInvariantGateTest {

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private JulesSessionRepository julesSessionRepository;

    @Mock
    private GitHubPullRequestService gitHubPullRequestService;

    private EpistemicLayerInvariantGate gate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gate = new EpistemicLayerInvariantGate(featureRepository, julesSessionRepository, gitHubPullRequestService);
    }

    @Test
    @DisplayName("Falsification: Periphery role mutating migration in real PR diff fails gate even if fileScope is misleading")
    void peripheryRoleMutatingMigrationFailsGateEvenWithMisleadingFileScope() {
        TaskEntity task = createFixtureTask("BARCAN-TAG-11");
        // Misleading self-attestation claiming only UI components
        task.setFileScope("frontend/src/components/Header.svelte");

        stubSessionAndDiff(task, 42, """
                diff --git a/src/main/resources/db/migration/V99__core_schema.sql b/src/main/resources/db/migration/V99__core_schema.sql
                --- a/src/main/resources/db/migration/V99__core_schema.sql
                +++ b/src/main/resources/db/migration/V99__core_schema.sql
                @@ -0,0 +1,5 @@
                +ALTER TABLE users ADD COLUMN is_admin BOOLEAN;
                """);

        assertTrue(gate.supports(task), "BARCAN-TAG-11 must be supported as periphery role");
        GateResult result = gate.check(task);

        assertFalse(result.passed(), "Periphery role modifying migration must fail gate");
        assertEquals("epistemic_layer_invariant", result.checkName());
        assertTrue(result.failureReasons().stream()
                .anyMatch(r -> r.contains("PERIPHERY task attempted to modify CORE file")
                        && r.contains("V99__core_schema.sql")),
                "Failure reason must cite the real core file from PR diff");
    }

    @Test
    @DisplayName("Falsification: Periphery role mutating security config in real PR diff fails gate even if fileScope is null")
    void peripheryRoleMutatingSecurityConfigFailsGateWithNullFileScope() {
        TaskEntity task = createFixtureTask("BARCAN-TAG-05");
        task.setFileScope(null);

        stubSessionAndDiff(task, 55, """
                diff --git a/src/main/java/com/eneik/SecurityConfig.java b/src/main/java/com/eneik/SecurityConfig.java
                --- a/src/main/java/com/eneik/SecurityConfig.java
                +++ b/src/main/java/com/eneik/SecurityConfig.java
                @@ -10,3 +10,4 @@
                +    .permitAll()
                """);

        assertTrue(gate.supports(task));
        GateResult result = gate.check(task);

        assertFalse(result.passed(), "Periphery role modifying SecurityConfig must fail gate");
        assertTrue(result.failureReasons().stream()
                .anyMatch(r -> r.contains("SecurityConfig.java")));
    }

    @Test
    @DisplayName("Ontological Contamination: Periphery role creating factory orchestrator entities fails gate")
    void peripheryRoleInjectingFactoryEntitiesFailsGate() {
        TaskEntity task = createFixtureTask("BARCAN-TAG-06");
        task.setFileScope("tests/TestRunner.java");

        stubSessionAndDiff(task, 60, """
                diff --git a/src/main/java/com/eneik/client/FakeAutoMergeService.java b/src/main/java/com/eneik/client/FakeAutoMergeService.java
                --- /dev/null
                +++ b/src/main/java/com/eneik/client/FakeAutoMergeService.java
                @@ -0,0 +1,10 @@
                +class FakeAutoMergeService {}
                """);

        assertTrue(gate.supports(task));
        GateResult result = gate.check(task);

        assertFalse(result.passed(), "Periphery role injecting factory orchestrator must fail gate");
        assertTrue(result.failureReasons().stream()
                .anyMatch(r -> r.contains("ONTOLOGICAL_CONTAMINATION") && r.contains("FakeAutoMergeService.java")));
    }

    @Test
    @DisplayName("Allowed: Periphery role modifying only periphery paths in real PR diff passes gate")
    void peripheryRoleModifyingOnlyPeripheryPathsPassesGate() {
        TaskEntity task = createFixtureTask("BARCAN-TAG-11");
        task.setFileScope("frontend/src/Button.svelte");

        stubSessionAndDiff(task, 77, """
                diff --git a/frontend/src/Button.svelte b/frontend/src/Button.svelte
                --- a/frontend/src/Button.svelte
                +++ b/frontend/src/Button.svelte
                @@ -1,3 +1,4 @@
                +<button class="primary">Click</button>
                diff --git a/frontend/src/theme.css b/frontend/src/theme.css
                --- a/frontend/src/theme.css
                +++ b/frontend/src/theme.css
                @@ -1,2 +1,3 @@
                +:root { --color: red; }
                """);

        assertTrue(gate.supports(task));
        GateResult result = gate.check(task);

        assertTrue(result.passed(), "Periphery role modifying only periphery files must pass gate");
        assertTrue(result.failureReasons().isEmpty());
    }

    @Test
    @DisplayName("Epistemic Layer Inheritance: Task on PERIPHERY feature with non-periphery role tag enforces core barrier")
    void peripheryFeatureWithNonPeripheryRoleEnforcesCoreProtection() {
        UUID featureId = UUID.randomUUID();
        FeatureEntity feature = new FeatureEntity();
        feature.setId(featureId);
        feature.setEpistemicLayer("PERIPHERY");
        when(featureRepository.findById(featureId)).thenReturn(Optional.of(feature));

        TaskEntity task = createFixtureTask("BARCAN-TAG-02"); // Backend role
        task.setFeatureId(featureId);

        assertTrue(gate.supports(task), "Task on PERIPHERY feature must be supported regardless of role tag");

        stubSessionAndDiff(task, 88, """
                diff --git a/src/main/resources/db/migration/V1__init.sql b/src/main/resources/db/migration/V1__init.sql
                --- a/src/main/resources/db/migration/V1__init.sql
                +++ b/src/main/resources/db/migration/V1__init.sql
                @@ -1,2 +1,3 @@
                +CREATE TABLE items (id INT);
                """);

        GateResult result = gate.check(task);
        assertFalse(result.passed(), "Task under PERIPHERY feature must not modify CORE migrations");
    }

    @Test
    @DisplayName("Non-periphery role with non-periphery feature is not supported")
    void nonPeripheryRoleWithoutPeripheryFeatureIsNotSupported() {
        UUID featureId = UUID.randomUUID();
        FeatureEntity feature = new FeatureEntity();
        feature.setId(featureId);
        feature.setEpistemicLayer("CORE");
        when(featureRepository.findById(featureId)).thenReturn(Optional.of(feature));

        TaskEntity task = createFixtureTask("BARCAN-TAG-02");
        task.setFeatureId(featureId);

        assertFalse(gate.supports(task), "Backend role on CORE feature must not be filtered by periphery gate");
    }

    @Test
    @DisplayName("Advisory fallback: When real PR diff has no files, advisory fileScope violation is caught")
    void advisoryFallbackCatchesScopeViolationWhenNoDiffAvailable() {
        TaskEntity task = createFixtureTask("BARCAN-TAG-11");
        task.setFileScope("src/main/resources/db/migration/V10__table.sql");

        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of());

        assertTrue(gate.supports(task));
        GateResult result = gate.check(task);

        assertFalse(result.passed(), "Advisory scope violation must be caught when PR diff is absent");
        assertTrue(result.failureReasons().stream()
                .anyMatch(r -> r.contains("advisory") && r.contains("CORE scope")));
    }

    @Test
    @DisplayName("Invariants: Gate stage is IMPLEMENTATION_RESULT and build-phase exemption is false")
    void invariantsForStageAndBuildPhaseExemption() {
        assertEquals(GateStage.IMPLEMENTATION_RESULT, gate.stage());
        assertFalse(gate.isBuildPhaseExempt(),
                "EpistemicLayerInvariantGate must never be build-phase exempt - core file protections hold from day zero");
    }

    private TaskEntity createFixtureTask(String roleTag) {
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
        task.setStatus(TaskStatus.queued);

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
