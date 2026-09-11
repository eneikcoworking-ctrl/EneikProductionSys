package com.eneik.production.services.onboarding;

import com.eneik.production.models.persistence.OnboardingAuditFindingEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.RoleCapabilityLoader;
import org.junit.jupiter.api.BeforeEach;
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

class OnboardingAuditServiceTest {

    private OnboardingAuditFindingRepository auditFindingRepository;
    private RepositoryStackAnalyzer stackAnalyzer;
    private RoleRepository roleRepository;
    private RoleCapabilityLoader roleCapabilityLoader;
    private MLPredictionServiceClient mlPredictionServiceClient;
    private ProjectRepository projectRepository;
    private OnboardingAuditService onboardingAuditService;

    @BeforeEach
    void setUp() {
        auditFindingRepository = mock(OnboardingAuditFindingRepository.class);
        stackAnalyzer = mock(RepositoryStackAnalyzer.class);
        roleRepository = mock(RoleRepository.class);
        roleCapabilityLoader = mock(RoleCapabilityLoader.class);
        mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        projectRepository = mock(ProjectRepository.class);

        onboardingAuditService = new OnboardingAuditService(
                auditFindingRepository,
                stackAnalyzer,
                roleRepository,
                roleCapabilityLoader,
                mlPredictionServiceClient,
                projectRepository
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        List<String> slugs = List.of("customer-app", "client-svc", "ci-missing", "well-tested", "access-failed", "empty-ci-tests");
        for (String slug : slugs) {
            try {
                Files.deleteIfExists(Paths.get("docs/reports/onboarding-audit-" + slug + ".md"));
            } catch (IOException ignored) {
            }
        }
    }

    private ProjectEntity createProject(String name, String slug, String repoUrl) {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setSlug(slug);
        p.setRepositoryName(name);
        p.setRepositoryUrl(repoUrl);
        p.setStatus(ProjectStatus.analyzing);
        return p;
    }

    @Test
    void auditWithoutGithubTokenProducesZeroFindingsAndUncheckedMarkdownReport() throws IOException {
        // Section XXXI & Queue Item 10:
        // When GitHub token is not configured (or access fails), the inspection cannot observe the repo.
        // Proof obligation (GILBERT_RAYL_03_CATEGORY_ERROR_SCAN, NUEL_BELNAP_03_TRUTH_STATUS_TABLE):
        // Lack of factory capability/access must not be converted into defect findings against the customer.
        ProjectEntity project = createProject("customer-app", "customer-app", "https://github.com/customer/customer-app");

        StackProfile uncheckedProfile = StackProfile.unchecked("GitHub token not configured.", "main", "");
        when(stackAnalyzer.analyze("customer-app", "customer")).thenReturn(
                new RepositoryStackAnalyzer.AnalysisResult(uncheckedProfile, Collections.emptyList())
        );
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).thenReturn(Collections.emptyList());

        StackProfile resultProfile = onboardingAuditService.runOnboardingAudit(project, true);

        assertThat(resultProfile.isUnchecked()).isTrue();
        assertThat(resultProfile.hasCI()).isEqualTo(InspectionStatus.UNCHECKED);
        assertThat(resultProfile.hasTests()).isEqualTo(InspectionStatus.UNCHECKED);
        assertThat(resultProfile.isMonorepo()).isEqualTo(InspectionStatus.UNCHECKED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captor.capture());

        List<OnboardingAuditFindingEntity> savedFindings = captor.getValue();
        // ZERO findings about customer repository!
        assertThat(savedFindings).isEmpty();

        // Check the generated report file
        Path reportFile = Paths.get("docs/reports/onboarding-audit-customer-app.md");
        assertThat(Files.exists(reportFile)).isTrue();
        String reportContent = Files.readString(reportFile);

        assertThat(reportContent).contains("- **Framework:** не проверено");
        assertThat(reportContent).contains("- **Database:** не проверено");
        assertThat(reportContent).contains("- **Monorepo:** не проверено");
        assertThat(reportContent).contains("- **Has CI:** не проверено");
        assertThat(reportContent).contains("- **Has Tests:** не проверено");
        assertThat(reportContent).contains("### CRITICAL FINDINGS (0)");
        assertThat(reportContent).contains("No critical findings detected.");
        assertThat(reportContent).contains("### MAJOR FINDINGS (0)");
        assertThat(reportContent).contains("No major findings detected.");
        assertThat(reportContent).contains("### MINOR FINDINGS (0)");
        assertThat(reportContent).contains("No minor findings detected.");
    }

    @Test
    void auditWithInspectedRepositoryAndNoTestsFilesCriticalFindingWhenProductionClaimed() {
        ProjectEntity project = createProject("client-svc", "client-svc", "https://github.com/client/client-svc");

        StackProfile profile = new StackProfile(
                "Java/Kotlin", "Spring Boot", "PostgreSQL",
                InspectionStatus.YES, InspectionStatus.NO, InspectionStatus.NO,
                "Ready for production deployment in enterprise cloud.", "main", "abc1234", 10, 10
        );

        when(stackAnalyzer.analyze("client-svc", "client")).thenReturn(
                new RepositoryStackAnalyzer.AnalysisResult(profile, Collections.emptyList())
        );
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).thenReturn(Collections.emptyList());

        onboardingAuditService.runOnboardingAudit(project, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captor.capture());

        List<OnboardingAuditFindingEntity> savedFindings = captor.getValue();
        assertThat(savedFindings).anyMatch(f ->
                "critical".equals(f.getSeverity()) &&
                "BARCAN-TAG-06".equals(f.getRoleTag()) &&
                f.getFindingText().contains("Project claims production readiness, but no test suite was found")
        );
    }

    @Test
    void auditWithInspectedRepositoryAndNoCiFilesMajorFinding() {
        ProjectEntity project = createProject("ci-missing", "ci-missing", "https://github.com/client/ci-missing");

        StackProfile profile = new StackProfile(
                "Go", "None", "None",
                InspectionStatus.NO, InspectionStatus.YES, InspectionStatus.NO,
                "Internal research tool.", "main", "def5678", 5, 5
        );

        when(stackAnalyzer.analyze("ci-missing", "client")).thenReturn(
                new RepositoryStackAnalyzer.AnalysisResult(profile, Collections.emptyList())
        );
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).thenReturn(Collections.emptyList());

        onboardingAuditService.runOnboardingAudit(project, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captor.capture());

        List<OnboardingAuditFindingEntity> savedFindings = captor.getValue();
        assertThat(savedFindings).anyMatch(f ->
                "major".equals(f.getSeverity()) &&
                "BARCAN-TAG-05".equals(f.getRoleTag()) &&
                f.getFindingText().contains("No automated CI workflows found in .github/workflows/")
        );
        assertThat(savedFindings).noneMatch(f -> "critical".equals(f.getSeverity()));
    }

    @Test
    void auditWithInspectedRepositoryAndVerifiedCiAndTestsDoesNotFileCiOrTestFindings() {
        ProjectEntity project = createProject("well-tested", "well-tested", "https://github.com/client/well-tested");

        StackProfile profile = new StackProfile(
                "Java/Kotlin", "Spring Boot", "PostgreSQL",
                InspectionStatus.YES, InspectionStatus.YES, InspectionStatus.NO,
                "A production-ready microservice with full test coverage.", "main", "fed9876", 50, 50
        );

        when(stackAnalyzer.analyze("well-tested", "client")).thenReturn(
                new RepositoryStackAnalyzer.AnalysisResult(profile, Collections.emptyList())
        );
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).thenReturn(Collections.emptyList());

        onboardingAuditService.runOnboardingAudit(project, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captor.capture());

        List<OnboardingAuditFindingEntity> savedFindings = captor.getValue();
        assertThat(savedFindings).noneMatch(f -> "BARCAN-TAG-06".equals(f.getRoleTag()));
        assertThat(savedFindings).noneMatch(f ->
                "BARCAN-TAG-05".equals(f.getRoleTag()) && f.getFindingText().contains("No automated CI workflows found")
        );
    }

    @Test
    void categoryBoundaryPreservedBetweenAccessFailureAndRepositoryReality() {
        // Direct test of GILBERT_RAYL_03_CATEGORY_ERROR_SCAN:
        // Scenario A: Access failure -> UNCHECKED -> ZERO findings.
        // Scenario B: Inspected repository with NO tests and NO CI -> 2 findings (critical + major).
        ProjectEntity projectA = createProject("access-failed", "access-failed", "https://github.com/org/access-failed");
        StackProfile profileA = StackProfile.unchecked("Could not fetch tree from GitHub.", "main", "");

        when(stackAnalyzer.analyze("access-failed", "org")).thenReturn(
                new RepositoryStackAnalyzer.AnalysisResult(profileA, Collections.emptyList())
        );
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(projectA.getId())).thenReturn(Collections.emptyList());

        onboardingAuditService.runOnboardingAudit(projectA, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captorA = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captorA.capture());
        assertThat(captorA.getValue()).isEmpty();

        org.mockito.Mockito.reset(auditFindingRepository);

        ProjectEntity projectB = createProject("empty-ci-tests", "empty-ci-tests", "https://github.com/org/empty-ci-tests");
        StackProfile profileB = new StackProfile(
                "Python", "FastAPI", "None",
                InspectionStatus.NO, InspectionStatus.NO, InspectionStatus.NO,
                "Production microservice.", "main", "abc", 15, 15
        );
        when(stackAnalyzer.analyze("empty-ci-tests", "org")).thenReturn(
                new RepositoryStackAnalyzer.AnalysisResult(profileB, Collections.emptyList())
        );
        when(auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(projectB.getId())).thenReturn(Collections.emptyList());

        onboardingAuditService.runOnboardingAudit(projectB, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OnboardingAuditFindingEntity>> captorB = ArgumentCaptor.forClass(List.class);
        verify(auditFindingRepository).saveAll(captorB.capture());
        assertThat(captorB.getValue()).isNotEmpty();
        assertThat(captorB.getValue()).anyMatch(f -> "critical".equals(f.getSeverity()));
        assertThat(captorB.getValue()).anyMatch(f -> "major".equals(f.getSeverity()));
    }
}
