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
    private AccountRepository accountRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        claimRepository.deleteAll();
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

        ResponseEntity<WishlistItemDto> wish = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/wishlist",
                Map.of("text", "Make the website beautiful and add backend API for leads", "type", "client_wish"),
                WishlistItemDto.class
        );
        assertThat(wish.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> orchestration = restTemplate.postForEntity(
                "/api/projects/" + project.id() + "/orchestrate",
                null,
                Map.class
        );
        assertThat(orchestration.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.id())).isNotEmpty();

        ProjectDashboardDto dashboard = restTemplate.getForObject(
                "/api/projects/" + project.id() + "/dashboard",
                ProjectDashboardDto.class
        );
        assertThat(dashboard.openWishlistCount()).isZero();
        assertThat(dashboard.queue().totalQueued()).isGreaterThan(0);

        ResponseEntity<ClaimDto> claim = restTemplate.postForEntity(
                "/api/tasks/claim",
                Map.of(
                    "accountId", globalAccount.getId().toString(),
                    "capableTags", new String[]{"BARCAN-TAG-09", "BARCAN-TAG-00", "BARCAN-TAG-02", "BARCAN-TAG-03", "BARCAN-TAG-05", "BARCAN-TAG-06", "BARCAN-TAG-11"}
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
}
