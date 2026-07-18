package com.eneik.production.controllers.projects;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.dto.ProjectDashboardDto;
import com.eneik.production.dto.ProjectDto;
import com.eneik.production.dto.WishlistItemDto;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistItemRepository;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProjectFlowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private OnboardingAuditFindingRepository onboardingAuditFindingRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private com.eneik.production.services.ContinuousOrchestrationService continuousOrchestrationService;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    // This class uses TestRestTemplate against a real random-port server, so test methods cannot rely on
    // @Transactional rollback — every project/task/wishlist a test creates is really committed to the shared
    // H2 database. Without this cleanup, whichever project this class's last test method leaves behind stays
    // "active" for the rest of the suite, and gets silently picked up by any later test class that calls
    // ContinuousOrchestrationService.continuousOrchestrate() (which processes every active project).
    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        claimRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM needs_human_review");
        jdbcTemplate.update("DELETE FROM task_conflicts");
        jdbcTemplate.update("DELETE FROM jules_sessions");
        taskRepository.deleteAll();
        wishlistItemRepository.deleteAll();
        accountRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM github_access_status");
        projectRepository.deleteAll();
    }

    @Test
    void clientProjectWishlistOrchestrationClaimAndAcceptanceFlow() {
        ResponseEntity<ProjectDto> createProject = restTemplate.postForEntity(
                "/api/projects",
                Map.of("name", "Client Site Redesign"),
                ProjectDto.class
        );

        assertThat(createProject.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProjectDto project = createProject.getBody();
        assertThat(project).isNotNull();
        assertThat(project.status()).isEqualTo(ProjectStatus.active);
        assertThat(project.factoryStatus()).isEqualTo("ready_local");
        assertThat(project.workspacePath()).contains("project-workspaces-test");
        Path workspace = Path.of(project.workspacePath());
        assertThat(Files.exists(workspace.resolve("README.md"))).isTrue();
        assertThat(Files.exists(workspace.resolve(".env.example"))).isTrue();
        assertThat(Files.exists(workspace.resolve(".github").resolve("workflows").resolve("ci.yml"))).isTrue();
        assertThat(project.githubRepositoryStatus()).contains("skipped");
        assertThat(project.linearProjectStatus()).contains("skipped");
        // We stopped creating project-scoped accounts, so it should be 0 for the project.
        assertThat(accountRepository.findByProjectIdOrderByNameAsc(project.id())).isEmpty();

        // Create a global account for the test to use
        com.eneik.production.models.persistence.AccountEntity globalAccount = new com.eneik.production.models.persistence.AccountEntity();
        globalAccount.setName("test-agent");
        globalAccount.setCapabilities("*");
        globalAccount.setStatus(com.eneik.production.models.persistence.AccountStatus.idle);
        accountRepository.save(globalAccount);

        com.eneik.production.dto.WishlistRequestDto req = new com.eneik.production.dto.WishlistRequestDto(null, com.eneik.production.models.persistence.WishlistSource.client, "BARCAN-TAG-00", "Make the website beautiful and add backend API for leads");
        ResponseEntity<com.eneik.production.dto.WishlistResponseDto> wish = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/wishlist",
                req,
                com.eneik.production.dto.WishlistResponseDto.class
        );
        assertThat(wish.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Setup mock for synchronous generateTaskMetadata
        java.util.Map<String, Object> aiResponse = new java.util.HashMap<>();
        aiResponse.put("jtbd", "Automated UI Verification");
        aiResponse.put("acceptanceCriteria", "Visuals match reference");
        org.mockito.Mockito.when(mlPredictionServiceClient.generateTaskMetadata(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(aiResponse);

        ResponseEntity<Map> orchestration = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/orchestrate",
                null,
                Map.class
        );
        assertThat(orchestration.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> throttledOrchestration = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/orchestrate",
                null,
                Map.class
        );
        assertThat(throttledOrchestration.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttledOrchestration.getBody()).containsKey("retryAfterSeconds");

        org.awaitility.Awaitility.await()
            .atMost(5, java.util.concurrent.TimeUnit.SECONDS)
            .pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            .until(() -> !taskRepository.findByProjectIdOrderByCreatedAtDesc(project.id()).isEmpty());

        assertThat(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.id())).isNotEmpty();

        ProjectDashboardDto dashboard = restTemplate.getForObject(
                "/api/projects/" + project.id() + "/dashboard",
                ProjectDashboardDto.class
        );
        assertThat(dashboard.openWishlistCount()).isZero();
        assertThat(dashboard.queue().totalQueued()).isGreaterThan(0);
        assertThat(dashboard.agentCount()).isEqualTo(1);
        assertThat(dashboard.agents()).extracting(com.eneik.production.dto.dashboard.AgentDashboardDto::name)
                .contains("test-agent");

        ResponseEntity<ClaimDto> claim = restTemplate.postForEntity(
                "/api/tasks/claim",
                Map.of(
                    "accountId", globalAccount.getId().toString(),
                    "capableTags", new String[]{"BARCAN-TAG-09", "BARCAN-TAG-00", "BARCAN-TAG-02", "BARCAN-TAG-03", "BARCAN-TAG-05", "BARCAN-TAG-06", "BARCAN-TAG-11"

}
                ),
                ClaimDto.class
        );
        assertThat(claim.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(claim.getBody()).isNotNull();
        assertThat(claim.getBody().roleTag()).startsWith("BARCAN-TAG-");

        ResponseEntity<ProjectDto> accepted = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/accept",
                null,
                ProjectDto.class
        );
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).isNotNull();
        assertThat(accepted.getBody().status()).isEqualTo(ProjectStatus.accepted);

        ResponseEntity<Map> blockedWish = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/wishlist",
                Map.of("text", "Do more after acceptance"),
                Map.class
        );
        assertThat(blockedWish.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void orchestrationAddsDesignSystemReadinessForUiSlices() {
        ResponseEntity<ProjectDto> createProject = restTemplate.postForEntity(
                "/api/projects",
                Map.of("name", "UI Ready Project"),
                ProjectDto.class
        );

        assertThat(createProject.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProjectDto project = createProject.getBody();
        assertThat(project).isNotNull();

        ResponseEntity<com.eneik.production.dto.WishlistResponseDto> wish = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/wishlist",
                new com.eneik.production.dto.WishlistRequestDto(null, com.eneik.production.models.persistence.WishlistSource.client, null, "Create a visible portal homepage"),
                com.eneik.production.dto.WishlistResponseDto.class
        );
        assertThat(wish.getStatusCode()).isEqualTo(HttpStatus.OK);

        org.mockito.Mockito.when(mlPredictionServiceClient.generateTaskSlices(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.List.of(new com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata(
                        "Portal homepage UI",
                        "When a visitor opens the portal, I want a visible homepage, so that the product can be inspected.",
                        "Given the homepage loads, When the visitor opens the root page, Then the main portal content is visible.",
                        "BARCAN-TAG-11",
                        com.eneik.production.models.persistence.LeanValue.essential,
                        "Must-Be",
                        "clear",
                        "TOC-CONSTRAINT-DECOMPOSITION",
                        "Escaped defects <= 5%",
                        true
                )));

        ResponseEntity<Map> orchestration = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/orchestrate",
                null,
                Map.class
        );

        assertThat(orchestration.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.util.List<com.eneik.production.models.persistence.TaskEntity> tasks =
                taskRepository.findByProjectIdOrderByCreatedAtDesc(project.id());
        assertThat(tasks).hasSize(2);
        assertThat(tasks).anySatisfy(task -> {
            assertThat(task.getRole().getTag()).isEqualTo("BARCAN-TAG-01");
            assertThat(task.getPayload().path("toc_constraint_ref").asText()).isEqualTo("BOOTSTRAP-ENVIRONMENT-BOUNDARY");
        });
        com.eneik.production.models.persistence.TaskEntity uiTask = tasks.stream()
                .filter(task -> "BARCAN-TAG-11".equals(task.getRole().getTag()))
                .findFirst()
                .orElseThrow();
        assertThat(uiTask.getPayload().get("dod").asText()).contains("docs/DESIGN_SYSTEM.md");
    }

    @Test
    void orchestrationCompilesWishlistIntoSingleHonestTaskWithoutGemini() {
        // Task compilation no longer calls mlPredictionServiceClient.generateTaskSlices() at all (see
        // ProjectFlowService.resolveTaskSlices) - the user does not want to pay for Gemini generations,
        // and the multi-slice AI decomposition this test used to verify silently fell back to fabricated
        // placeholder content whenever Gemini failed. One wishlist now compiles into exactly one honest
        // task built directly from the real content; Jules does any further decomposition itself.
        ResponseEntity<ProjectDto> createProject = restTemplate.postForEntity(
                "/api/projects",
                Map.of("name", "Graph Compiler Project"),
                ProjectDto.class
        );
        assertThat(createProject.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProjectDto project = createProject.getBody();
        assertThat(project).isNotNull();

        ResponseEntity<com.eneik.production.dto.WishlistResponseDto> wish = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/wishlist",
                new com.eneik.production.dto.WishlistRequestDto(null, com.eneik.production.models.persistence.WishlistSource.client, null, "Build account settings with API, browser UI, and verification"),
                com.eneik.production.dto.WishlistResponseDto.class
        );
        assertThat(wish.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> orchestration = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/orchestrate",
                null,
                Map.class
        );

        assertThat(orchestration.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.util.List<com.eneik.production.models.persistence.TaskEntity> tasks =
                taskRepository.findByProjectIdOrderByCreatedAtDesc(project.id()).stream()
                        .filter(task -> !"BOOTSTRAP-ENVIRONMENT-BOUNDARY".equals(task.getPayload().path("toc_constraint_ref").asText()))
                        .toList();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getDependsOn()).isNull();
        assertThat(tasks.get(0).getDescription()).contains("Build account settings with API, browser UI, and verification");
        assertThat(tasks.get(0).getDescription()).contains("Original Brief");

        ProjectDashboardDto dashboard = restTemplate.getForObject(
                "/api/projects/" + project.id() + "/dashboard",
                ProjectDashboardDto.class
        );
        assertThat(dashboard.emsMetrics().graphHealth().duplicateSemanticKeys()).isZero();
    }

    @Test
    void brownfieldOnboardingAuditAndActivationFlow() throws Exception {
        ResponseEntity<ProjectDto> createProject = restTemplate.postForEntity(
                "/api/projects",
                Map.of("name", "Legacy App", "onboardingMode", "brownfield"),
                ProjectDto.class
        );

        assertThat(createProject.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProjectDto project = createProject.getBody();
        assertThat(project).isNotNull();
        assertThat(project.status()).isEqualTo(ProjectStatus.analyzing);
        assertThat(project.onboardingMode()).isEqualTo("brownfield");

        long findingsCount = onboardingAuditFindingRepository.countByProjectId(project.id());
        assertThat(findingsCount).isGreaterThanOrEqualTo(2L);

        Path reportFile = Path.of("docs/reports/onboarding-audit-" + project.slug() + ".md");
        assertThat(Files.exists(reportFile)).isTrue();
        String reportContent = Files.readString(reportFile);
        assertThat(reportContent).contains("Stack Profile");
        assertThat(reportContent).contains("Audit Findings");

        ResponseEntity<Map> reportRes = restTemplate.getForEntity(
                "/api/projects/" + project.id() + "/onboarding-report",
                Map.class
        );
        assertThat(reportRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reportRes.getBody().get("report")).isEqualTo(reportContent);

        ResponseEntity<java.util.List> findingsRes = restTemplate.getForEntity(
                "/api/projects/" + project.id() + "/onboarding-findings",
                java.util.List.class
        );
        assertThat(findingsRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findingsRes.getBody()).hasSize((int) findingsCount);

        ResponseEntity<Map> blockedOrchestrate = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/orchestrate",
                null,
                Map.class
        );
        assertThat(blockedOrchestrate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> blockedWish = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/wishlist",
                Map.of("text", "Try to add wish to analyzing project", "type", "client_wish"),
                Map.class
        );
        assertThat(blockedWish.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ProjectDto> activated = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/activate",
                null,
                ProjectDto.class
        );
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activated.getBody().status()).isEqualTo(ProjectStatus.active);

        ResponseEntity<com.eneik.production.dto.WishlistResponseDto> wish = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/wishlist",
                new com.eneik.production.dto.WishlistRequestDto(null, com.eneik.production.models.persistence.WishlistSource.client, "BARCAN-TAG-00", "Fix finding 1"),
                com.eneik.production.dto.WishlistResponseDto.class
        );
        assertThat(wish.getStatusCode()).isEqualTo(HttpStatus.OK);

        Files.deleteIfExists(reportFile);
    }

    @Test
    void taskCompilationSucceedsEvenWhenAiServiceMockWouldFail() {
        // Task compilation no longer calls the ML/Gemini service at all (see
        // ProjectFlowService.resolveTaskSlices), so it can no longer be degraded by an AI outage - this
        // is the whole point of the fix: a wishlist item now compiles into a real task immediately and
        // unconditionally, using the honest fallback slice built directly from its own content, rather
        // than deferring or silently fabricating placeholder content while waiting on Gemini.
        ResponseEntity<ProjectDto> createProject = restTemplate.postForEntity(
                "/api/projects",
                Map.of("name", "Failing AI App"),
                ProjectDto.class
        );
        ProjectDto project = createProject.getBody();

        com.eneik.production.dto.WishlistRequestDto req = new com.eneik.production.dto.WishlistRequestDto(null, com.eneik.production.models.persistence.WishlistSource.client, "BARCAN-TAG-00", "Will fail in AI");
        restTemplate.postForEntity("/api/projects/" + project.id() + "/wishlist", req, com.eneik.production.dto.WishlistResponseDto.class);

        org.mockito.Mockito.when(mlPredictionServiceClient.generateTaskMetadata(org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new RuntimeException("Simulated AI Failure"));

        continuousOrchestrationService.continuousOrchestrate();

        ProjectDashboardDto dashboard = restTemplate.getForObject("/api/projects/" + project.id() + "/dashboard", ProjectDashboardDto.class);
        assertThat(dashboard.openWishlistCount()).isEqualTo(0);

        // orchestrate() unconditionally ensures an environment-bootstrap task exists for every active project,
        // independent of wishlist compilation - that's not part of what this test is exercising.
        java.util.List<com.eneik.production.models.persistence.TaskEntity> nonBootstrapTasks =
                taskRepository.findByProjectIdOrderByCreatedAtDesc(project.id()).stream()
                        .filter(t -> t.getPayload() == null
                                || !"BOOTSTRAP-ENVIRONMENT-BOUNDARY".equals(t.getPayload().path("toc_constraint_ref").asText()))
                        .toList();
        assertThat(nonBootstrapTasks).hasSize(1);
        assertThat(nonBootstrapTasks.get(0).getDescription()).contains("Will fail in AI");
    }
}
