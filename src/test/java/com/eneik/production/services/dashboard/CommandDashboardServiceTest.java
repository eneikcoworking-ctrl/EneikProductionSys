package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.AcceptanceReadinessDto;
import com.eneik.production.dto.dashboard.CommandDashboardDto;
import com.eneik.production.repositories.ClientAcceptanceTraversalRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.verdict.VerdictGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verification harness for CommandDashboardService readiness determination
 * (NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT / D007 Evidence gap,
 *  DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT / D009 Substitution failure).
 *
 * <p>Invariant: Construction (tasks done, gates passed, PRs merged, GitHub healthy) establishes only
 * that the scope is built. Readiness ("ready" / Verdict.PERMIT) strictly requires witness evidence
 * that a customer has traversed the delivery (clientAcceptanceTraversals >= 1).
 */
class CommandDashboardServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ProjectRepository projectRepository;
    private VerdictGate verdictGate;
    private ClientAcceptanceTraversalRepository traversalRepository;
    private CommandDashboardService commandDashboardService;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        projectRepository = mock(ProjectRepository.class);
        verdictGate = mock(VerdictGate.class);
        traversalRepository = mock(ClientAcceptanceTraversalRepository.class);

        commandDashboardService = new CommandDashboardService(
                jdbcTemplate,
                projectRepository,
                verdictGate,
                traversalRepository
        );

        // Standard information_schema mocks for tables
        when(jdbcTemplate.queryForObject(
                eq("SELECT count(*) FROM information_schema.tables WHERE table_name = ? OR table_name = ?"),
                eq(Integer.class), anyString(), anyString())).thenReturn(1);

        when(jdbcTemplate.queryForObject(
                eq("SELECT count(*) FROM information_schema.columns WHERE (table_name = ? OR table_name = ?) AND (column_name = ? OR column_name = ?)"),
                eq(Integer.class), anyString(), anyString(), anyString(), anyString())).thenReturn(1);
    }

    private void setupSatisfiedConstructionConditions() {
        // 1. Tasks: all done and quality gate passed
        Map<String, Object> task = new HashMap<>();
        task.put("id", UUID.randomUUID());
        task.put("status", "done");
        task.put("quality_gate_passed", true);
        when(jdbcTemplate.queryForList("SELECT * FROM tasks WHERE project_id = ?", projectId))
                .thenReturn(List.of(task));

        // 2. Wishlist: no pending items
        when(jdbcTemplate.queryForList("SELECT * FROM wishlist WHERE project_id = ?", projectId))
                .thenReturn(Collections.emptyList());

        // 3. Jules sessions
        when(jdbcTemplate.queryForList(
                eq("SELECT js.* FROM jules_sessions js JOIN tasks t ON js.task_id = t.id WHERE t.project_id = ?"),
                eq(projectId))).thenReturn(Collections.emptyList());

        // 4. PR reviews: merged and CI clean
        Map<String, Object> prReview = new HashMap<>();
        prReview.put("id", UUID.randomUUID());
        prReview.put("ci_status", "passed");
        when(jdbcTemplate.queryForList("SELECT * FROM pr_reviews WHERE project_id = ?", projectId))
                .thenReturn(List.of(prReview));

        // 5. GitHub access: healthy
        Map<String, Object> githubAccess = new HashMap<>();
        githubAccess.put("has_repo_access", true);
        githubAccess.put("ci_status", true);
        when(jdbcTemplate.queryForList("SELECT * FROM github_access_status WHERE project_id = ?", projectId))
                .thenReturn(List.of(githubAccess));

        // 6. Linear metadata
        when(jdbcTemplate.queryForList("SELECT * FROM linear_issue_metadata WHERE project_id = ?", projectId))
                .thenReturn(Collections.emptyList());

        // 7. Features (Kano classes)
        when(jdbcTemplate.queryForList("SELECT id, kano_class FROM features WHERE project_id = ?", projectId))
                .thenReturn(Collections.emptyList());

        // VerdictGate declines to act (verdict_gating_enabled is off by default)
        when(verdictGate.constrain(any(), any())).thenAnswer(inv ->
                new VerdictGate.Decision(inv.getArgument(1), false, List.of()));
    }

    @Test
    @DisplayName("Falsification: All 4 construction conditions true + 0 client acceptance traversals refutes 'ready' (Verdict.WITHHOLD)")
    void allFourConstructionConditionsSatisfiedWithZeroClientAcceptanceTraversalsIsWithheldAsNotReady() {
        setupSatisfiedConstructionConditions();
        when(traversalRepository.countByProjectIdAndWalkedByIgnoreCase(projectId, "client")).thenReturn(0L);

        CommandDashboardDto dashboard = commandDashboardService.getDashboard(projectId);
        AcceptanceReadinessDto readiness = dashboard.acceptanceReadiness();

        // 4 construction conditions hold
        assertThat(readiness.allTasksDone()).isTrue();
        assertThat(readiness.allQualityGatesPassed()).isTrue();
        assertThat(readiness.allPrsMerged()).isTrue();
        assertThat(readiness.githubAccessHealthy()).isTrue();

        // 5th condition fails
        assertThat(readiness.clientAcceptanceWitnessed()).isFalse();

        // Readiness must NOT be "ready"
        assertThat(readiness.readiness()).isEqualTo("not ready");
        assertThat(readiness.statusLabel()).isEqualTo("NOT READY");
        assertThat(readiness.uiColorToken()).isEqualTo("border-error");

        // Named reason specifies scope built awaiting client acceptance
        assertThat(readiness.unmetConditions()).contains(
                "No client acceptance traversal recorded (scope built, awaiting client acceptance)"
        );
    }

    @Test
    @DisplayName("Verification: All 4 construction conditions true + >= 1 client acceptance traversals permits 'ready'")
    void allFourConstructionConditionsSatisfiedWithClientAcceptanceTraversalIsPermittedAsReady() {
        setupSatisfiedConstructionConditions();
        when(traversalRepository.countByProjectIdAndWalkedByIgnoreCase(projectId, "client")).thenReturn(2L);

        CommandDashboardDto dashboard = commandDashboardService.getDashboard(projectId);
        AcceptanceReadinessDto readiness = dashboard.acceptanceReadiness();

        assertThat(readiness.allTasksDone()).isTrue();
        assertThat(readiness.allQualityGatesPassed()).isTrue();
        assertThat(readiness.allPrsMerged()).isTrue();
        assertThat(readiness.githubAccessHealthy()).isTrue();
        assertThat(readiness.clientAcceptanceWitnessed()).isTrue();

        assertThat(readiness.readiness()).isEqualTo("ready");
        assertThat(readiness.statusLabel()).isEqualTo("READY");
        assertThat(readiness.uiColorToken()).isEqualTo("border-success");
        assertThat(readiness.unmetConditions()).isEmpty();
    }

    @Test
    @DisplayName("Tri-state logic: Unmeasurable client traversals yields ABSTAIN and 'unknown' readiness")
    void unmeasurableClientAcceptanceTraversalsYieldsAbstainAndUnknownReadiness() {
        setupSatisfiedConstructionConditions();

        // Simulate repository error or missing measurement
        when(traversalRepository.countByProjectIdAndWalkedByIgnoreCase(projectId, "client"))
                .thenThrow(new RuntimeException("database connection failure"));

        CommandDashboardDto dashboard = commandDashboardService.getDashboard(projectId);
        AcceptanceReadinessDto readiness = dashboard.acceptanceReadiness();

        assertThat(readiness.clientAcceptanceWitnessed()).isNull();
        assertThat(readiness.readiness()).isEqualTo("unknown");
        assertThat(readiness.statusLabel()).isEqualTo("UNKNOWN");
        assertThat(readiness.uiColorToken()).isEqualTo("border-warning");
    }

    @Test
    @DisplayName("Construction failure: Unfinished tasks with witnessed client traversal remains 'not ready'")
    void unfinishedTasksWithWitnessedClientTraversalYieldsNotReady() {
        setupSatisfiedConstructionConditions();
        when(traversalRepository.countByProjectIdAndWalkedByIgnoreCase(projectId, "client")).thenReturn(1L);

        // Invalidate task condition
        Map<String, Object> incompleteTask = new HashMap<>();
        incompleteTask.put("id", UUID.randomUUID());
        incompleteTask.put("status", "in_progress");
        incompleteTask.put("quality_gate_passed", false);
        when(jdbcTemplate.queryForList("SELECT * FROM tasks WHERE project_id = ?", projectId))
                .thenReturn(List.of(incompleteTask));

        CommandDashboardDto dashboard = commandDashboardService.getDashboard(projectId);
        AcceptanceReadinessDto readiness = dashboard.acceptanceReadiness();

        assertThat(readiness.allTasksDone()).isFalse();
        assertThat(readiness.clientAcceptanceWitnessed()).isTrue();
        assertThat(readiness.readiness()).isEqualTo("not ready");
        assertThat(readiness.statusLabel()).isEqualTo("NOT READY");
        assertThat(readiness.unmetConditions()).contains("Some tasks are not done or in review");
    }
}
