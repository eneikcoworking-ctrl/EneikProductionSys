package com.eneik.production.controllers.accounts;

import com.eneik.production.dto.AccountDto;
import com.eneik.production.dto.ClaimDto;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private com.eneik.production.repositories.ProjectRepository projectRepository;

    @Autowired
    private com.eneik.production.repositories.ClaimRepository claimRepository;

    @Autowired
    private com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;

    @Autowired
    private com.eneik.production.repositories.WishlistItemRepository wishlistItemRepository;

    @BeforeEach
    void setUp() {
        wishlistItemRepository.deleteAll();
        julesSessionRepository.deleteAll();
        claimRepository.deleteAll();
        taskRepository.deleteAll();
        accountRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM github_access_status");
        projectRepository.deleteAll();
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void createListStatusHeartbeatAndDeleteAccount() {
        AccountDto created = createAccount("agent-api", " BARCAN-TAG-02, BARCAN-TAG-02 , BARCAN-TAG-11 ");

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("agent-api");
        assertThat(created.status()).isEqualTo(AccountStatus.idle);
        assertThat(created.capabilities()).isEqualTo("BARCAN-TAG-02,BARCAN-TAG-11");
        assertThat(created.lastHeartbeat()).isNotNull();

        ResponseEntity<AccountDto[]> listResponse = restTemplate.getForEntity("/api/accounts", AccountDto[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).extracting(AccountDto::id).contains(created.id());

        ResponseEntity<AccountDto> offlineResponse = restTemplate.postForEntity(
                "/api/accounts/" + created.id() + "/status",
                new HttpEntity<>(Map.of("status", "offline")),
                AccountDto.class
        );
        assertThat(offlineResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(offlineResponse.getBody()).isNotNull();
        assertThat(offlineResponse.getBody().status()).isEqualTo(AccountStatus.offline);

        ResponseEntity<AccountDto> heartbeatResponse = restTemplate.postForEntity(
                "/api/accounts/" + created.id() + "/heartbeat",
                null,
                AccountDto.class
        );
        assertThat(heartbeatResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(heartbeatResponse.getBody()).isNotNull();
        assertThat(heartbeatResponse.getBody().status()).isEqualTo(AccountStatus.idle);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/accounts/" + created.id(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(accountRepository.existsById(created.id())).isFalse();
    }

    @Test
    void claimApiCanUseAccountCreatedThroughAccountApi() {
        AccountDto account = createAccount("backend-agent", "BARCAN-TAG-02");
        RoleEntity role = roleRepository.findById("BARCAN-TAG-02").orElseThrow();

        com.eneik.production.models.persistence.ProjectEntity project = new com.eneik.production.models.persistence.ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project");
        project.setRepositoryName("test-repo");
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);
        projectRepository.saveAndFlush(project);

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Implement backend endpoint");
        task.setStatus(TaskStatus.queued);
        taskRepository.saveAndFlush(task);

        ResponseEntity<ClaimDto> claimResponse = restTemplate.postForEntity(
                "/api/tasks/claim",
                Map.of(
                        "accountId", account.id().toString(),
                        "capableTags", new String[]{"BARCAN-TAG-02"}
                ),
                ClaimDto.class
        );

        assertThat(claimResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(claimResponse.getBody()).isNotNull();
        assertThat(claimResponse.getBody().taskId()).isEqualTo(task.getId());
        assertThat(claimResponse.getBody().roleTag()).isEqualTo("BARCAN-TAG-02");
        assertThat(accountRepository.findById(account.id()).orElseThrow().getStatus()).isEqualTo(AccountStatus.busy);
    }

    @Test
    void rejectInvalidCreateRequest() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/accounts",
                Map.of("name", "", "capabilities", ""),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "name is required");
    }

    private AccountDto createAccount(String name, String capabilities) {
        ResponseEntity<AccountDto> response = restTemplate.postForEntity(
                "/api/accounts",
                Map.of("name", name, "capabilities", capabilities),
                AccountDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
