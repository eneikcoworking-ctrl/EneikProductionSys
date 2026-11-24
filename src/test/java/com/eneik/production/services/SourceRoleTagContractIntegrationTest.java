package com.eneik.production.services;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.accounts.AccountHealthService;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.judgment.DeliveredWorkJudgmentService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.orchestration.BranchGarbageCollectorService;
import com.eneik.production.services.runtime.ClientRuntimeObservabilityService;
import com.eneik.production.services.runtime.ProductLaunchabilityService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.toc.TocSubordinationLever;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Falsification & integration gate for Mechanism #9 Step A (D003 / D008 / D002):
 * verifies real H2 + Flyway V1..V146 persistence of canonical single- and multi-role
 * SOURCE_ROLE_TAG values, D008 Main CI Recovery without SQL 22001, and fail-fast
 * rejection of pattern names or oversized tags before hitting the SQL layer.
 */
@SpringBootTest
@ActiveProfiles("test")
class SourceRoleTagContractIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TechnicalLeadCompiler technicalLeadCompiler;

    private ProjectEntity persistProject(String suffix) {
        ProjectEntity project = new ProjectEntity();
        String name = "source-role-tag-test-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        project.setName(name);
        project.setSlug(name);
        project.setRepositoryName(name);
        project.setRepoUrl("https://github.com/eneik-test/" + name);
        project.setStatus(ProjectStatus.active);
        return projectRepository.saveAndFlush(project);
    }

    @Test
    void recoverFailingMainCiPersistsCanonicalBarcanTag06InRealH2WithoutSql22001() {
        ProjectEntity project = persistProject("d008");

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                projectRepository,
                mock(ProjectFlowService.class),
                mock(AccountRepository.class),
                mock(JulesSessionRepository.class),
                mock(JulesDispatchService.class),
                wishlistRepository,
                technicalLeadCompiler,
                mock(MLPredictionServiceClient.class),
                taskRepository,
                new SystemProgressTracker(),
                mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                mock(AccountHealthService.class),
                mock(ProductLaunchabilityService.class),
                mock(ClientRuntimeObservabilityService.class),
                mock(DeliveredWorkJudgmentService.class),
                mock(TocSubordinationLever.class)
        );

        FlowCoreDto flowSnapshot = mock(FlowCoreDto.class);
        FlowSpineDto spine = mock(FlowSpineDto.class);
        FlowSpineDto.FlowCounts counts = mock(FlowSpineDto.FlowCounts.class);
        when(flowSnapshot.snapshot()).thenReturn(spine);
        when(spine.currentState()).thenReturn("BLOCKED_BY_MAIN_CI");
        when(spine.counts()).thenReturn(counts);
        when(counts.mainCiStatus()).thenReturn("failure");
        when(spine.blockingReason()).thenReturn("TelemetryMetricsServiceTest=failure, InternalServiceTest=failure");

        ContinuousOrchestrationService.MainCiRecoveryResult result = service.recoverFailingMainCi(project, flowSnapshot);

        assertTrue(result.recoveryActive());
        assertEquals("TRIGGERED", result.status());
        assertNotNull(result.recoveryWishlistId());

        WishlistEntity persisted = wishlistRepository.findById(result.recoveryWishlistId()).orElseThrow();
        assertEquals("BARCAN-TAG-06", persisted.getSourceRoleTag());
        assertEquals("MAIN_CI_RECOVERY", persisted.getTocConstraintRef());
        assertEquals(WishlistSource.self_falsification, persisted.getSource());
        assertTrue(persisted.getContent().contains("KARL_POPPER_01_FALSIFICATION_HARNESS"));
        assertTrue(persisted.getDod().contains("KARL_POPPER_01_FALSIFICATION_HARNESS"));
    }

    @Test
    void multiRoleCanonicalSourceRoleTagPersistsInV146SchemaAndEvaluatesDoctrinePressure() {
        ProjectEntity project = persistProject("multirole");

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setTargetContext(TargetContext.PRODUCT_CODEBASE);
        wishlist.setSource(WishlistSource.philosophical_falsification);
        wishlist.setSourceRoleTag("BARCAN-TAG-01, BARCAN-TAG-06, BARCAN-TAG-08");
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setContent("Multi-philosopher convergence cluster across BARCAN-TAG-01/06/08");

        WishlistEntity saved = wishlistRepository.saveAndFlush(wishlist);
        WishlistEntity reloaded = wishlistRepository.findById(saved.getId()).orElseThrow();

        assertEquals("BARCAN-TAG-01, BARCAN-TAG-06, BARCAN-TAG-08", reloaded.getSourceRoleTag());
        Double pressure = ReflectionTestUtils.invokeMethod(
                technicalLeadCompiler, "doctrinePressure", reloaded.getSourceRoleTag());
        assertNotNull(pressure);
        assertEquals(0.25, pressure, 1e-6);
    }

    @Test
    void falsificationPatternNameOrOversizedSourceRoleTagRejectedBeforeSqlLayer() {
        WishlistEntity wishlist = new WishlistEntity();
        assertNull(WishlistEntity.validateCanonicalSourceRoleTag(null));
        wishlist.setSourceRoleTag(null);
        assertNull(wishlist.getSourceRoleTag());

        // Pattern name must never leak into source_role_tag
        IllegalArgumentException patternEx = assertThrows(
                IllegalArgumentException.class,
                () -> wishlist.setSourceRoleTag("KARL_POPPER_01_FALSIFICATION_HARNESS"));
        assertTrue(patternEx.getMessage().contains("KARL_POPPER_01_FALSIFICATION_HARNESS"));

        // Blank string must be rejected
        assertThrows(IllegalArgumentException.class, () -> wishlist.setSourceRoleTag("   "));

        // > 128 chars must be rejected before SQL layer even if all tokens are canonical
        String oversized = String.join(", ", List.of(
                "BARCAN-TAG-01", "BARCAN-TAG-02", "BARCAN-TAG-03", "BARCAN-TAG-04",
                "BARCAN-TAG-05", "BARCAN-TAG-06", "BARCAN-TAG-07", "BARCAN-TAG-08",
                "BARCAN-TAG-09", "BARCAN-TAG-10"));
        assertTrue(oversized.length() > WishlistEntity.MAX_SOURCE_ROLE_TAG_LENGTH);
        IllegalArgumentException lengthEx = assertThrows(
                IllegalArgumentException.class,
                () -> wishlist.setSourceRoleTag(oversized));
        assertTrue(lengthEx.getMessage().contains("128"));
    }
}
