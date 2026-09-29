package com.eneik.production.services.onboarding;

import com.eneik.production.models.persistence.OnboardingAuditFindingEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.RoleCapabilityLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Falsification tests for OnboardingAuditService anchored in Barcan philosopher patterns:
 * 1. NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012 Policy contradiction, Belnap):
 *    Tri-state logic (YES / NO / UNCHECKED). The unknown state is explicit in types and display ("не проверено"),
 *    preventing binary boolean coercion.
 * 2. GILBERT_RAYL_03_CATEGORY_ERROR_SCAN (D002 Invalid state, Ryle):
 *    Category boundary: Factory-side access limitations must never be categorized as customer repository defects.
 *    UNCHECKED inspection status yields exactly 0 findings against the customer.
 * 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010 Data lineage loss, Goldman):
 *    Findings are filed only on verified data (isNo()); idempotency and cleanup of prior findings (deleteAll)
 *    ensures reliable lineage.
 */
class OnboardingAuditServiceFalsificationTest {

    private OnboardingAuditFindingRepository auditFindingRepository;
    private RepositoryStackAnalyzer stackAnalyzer;
    private RoleRepository roleRepository;
    private RoleCapabilityLoader roleCapabilityLoader;
    private MLPredictionServiceClient mlPredictionServiceClient;
    private ProjectRepository projectRepository;
    private OnboardingAuditService service;

    @BeforeEach
    void setUp() {
        auditFindingRepository = mock(OnboardingAuditFindingRepository.class);
        stackAnalyzer = mock(RepositoryStackAnalyzer.class);
        roleRepository = mock(RoleRepository.class);
        roleCapabilityLoader = mock(RoleCapabilityLoader.class);
        mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        projectRepository = mock(ProjectRepository.class);

        service = new OnboardingAuditService(
                auditFindingRepository,
                stackAnalyzer,
                roleRepository,
                roleCapabilityLoader,
                mlPredictionServiceClient,
                projectRepository
        );
    }

    @AfterEach
    void tearDown() {
        List<String> slugs = List.of("falsify-belnap", "falsify-ryle", "falsify-goldman", "falsify-cleanup");
        for (String slug : slugs) {
            try {
                Files.deleteIfExists(Paths.get("docs/reports/onboarding-audit-" + slug + ".md"));
            } catch (IOException ignored) {
            }
        }
    }

    private ProjectEntity createProject(String slug) {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setName(slug);
        p.setSlug(slug);
        p.setRepositoryName(slug);
        p.setRepositoryUrl("https://github.com/customer/" + slug);
        p.setStatus(ProjectStatus.analyzing);
        return p;
    }

    @Test
    @DisplayName("Belnap D012: Tri-state status UNCHECKED renders as 'не проверено', never as boolean false/NO")
    void falsifyBelnapTruthStatusTable_uncheckedRendersExplicitly() throws IOException {
        ProjectEntity project = createProject("falsify-belnap");
        StackProfile unchecked = StackProfile.unchecked("API rate limited.", "main", "");

        when(stackAnalyzer.analyze(project.getRepositoryName(), "customer"))
                .thenReturn(new RepositoryStackAnalyzer.AnalysisResult(unchecked, Collections.emptyList()));
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(Collections.emptyList());

        StackProfile result = service.runOnboardingAudit(project, true);

        assertThat(result.hasCI()).isEqualTo(InspectionStatus.UNCHECKED);
        assertThat(result.hasTests()).isEqualTo(InspectionStatus.UNCHECKED);
        assertThat(result.isMonorepo()).isEqualTo(InspectionStatus.UNCHECKED);
        assertThat(result.hasCI().displayValue()).isEqualTo("не проверено");

        Path reportPath = Paths.get("docs/reports/onboarding-audit-falsify-belnap.md");
        assertThat(Files.exists(reportPath)).isTrue();
        String report = Files.readString(reportPath);
        assertThat(report).contains("- **Has CI:** не проверено");
        assertThat(report).contains("- **Has Tests:** не проверено");
        assertThat(report).doesNotContain("- **Has CI:** No");
    }

    @Test
    @DisplayName("Ryle D002: Category boundary prevents factory access failure from becoming customer defect findings")
    void falsifyRyleCategoryError_accessFailureProducesZeroFindings() {
        ProjectEntity project = createProject("falsify-ryle");
        StackProfile unchecked = StackProfile.unchecked("Repository not found or 403 Forbidden.", "main", "");

        when(stackAnalyzer.analyze(project.getRepositoryName(), "customer"))
                .thenReturn(new RepositoryStackAnalyzer.AnalysisResult(unchecked, Collections.emptyList()));
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(Collections.emptyList());

        service.runOnboardingAudit(project, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captor.capture());

        // Zero findings about the customer repository must be saved
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Goldman D010: Defect findings are filed only when inspection evidence is confirmed (isNo())")
    void falsifyGoldmanReliabilityChain_findingsRequireConfirmedEvidence() {
        ProjectEntity project = createProject("falsify-goldman");
        // Confirmed inspected profile where tests are NO and production is claimed
        StackProfile inspectedNoTests = new StackProfile(
                "TypeScript", "NestJS", "PostgreSQL",
                InspectionStatus.YES, InspectionStatus.NO, InspectionStatus.NO,
                "Enterprise production system for customer onboarding", "main", "sha-987", 20, 20
        );

        when(stackAnalyzer.analyze(project.getRepositoryName(), "customer"))
                .thenReturn(new RepositoryStackAnalyzer.AnalysisResult(inspectedNoTests, Collections.emptyList()));
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(Collections.emptyList());

        service.runOnboardingAudit(project, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captor.capture());

        List<OnboardingAuditFindingEntity> findings = captor.getValue();
        assertThat(findings).isNotEmpty();
        // Finding generated because hasTests().isNo() was verified empirically
        assertThat(findings).anyMatch(f -> "BARCAN-TAG-06".equals(f.getRoleTag()) && "critical".equals(f.getSeverity()));
    }

    @Test
    @DisplayName("Goldman D010: Prior findings are cleanly removed on re-audit to preserve reliable data lineage")
    void falsifyGoldmanReliabilityChain_priorFindingsCleanedOnReAudit() {
        ProjectEntity project = createProject("falsify-cleanup");

        OnboardingAuditFindingEntity staleFinding = new OnboardingAuditFindingEntity();
        staleFinding.setId(UUID.randomUUID());
        staleFinding.setProject(project);
        staleFinding.setFindingText("Stale finding from old inspection");

        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(staleFinding));

        StackProfile freshProfile = StackProfile.unchecked("Temporary token refresh", "main", "");
        when(stackAnalyzer.analyze(project.getRepositoryName(), "customer"))
                .thenReturn(new RepositoryStackAnalyzer.AnalysisResult(freshProfile, Collections.emptyList()));

        service.runOnboardingAudit(project, true);

        // Previous findings must be purged to maintain clean lineage
        verify(auditFindingRepository).deleteAll(List.of(staleFinding));
    }
}
