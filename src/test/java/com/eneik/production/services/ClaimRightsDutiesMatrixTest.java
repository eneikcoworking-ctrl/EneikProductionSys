package com.eneik.production.services;

import com.eneik.production.controllers.tasks.ClaimController;
import com.eneik.production.dto.ClaimDto;
import com.eneik.production.dto.ClaimRequestDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.SystemStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verification for Prescription 40:
 * - RONALD_DVORKIN_02_RIGHTS_DUTIES_MATRIX (D006 Authorization ambiguity):
 *   The right to claim a task requires an authentic, eligible subject (enabled, non-decommissioned,
 *   non-blocked AccountEntity) carrying valid bearer or master credentials.
 * - PATRITSIYA_CHERCHLAND_05_TELEOSEMANTIC_FEEDBACK (D011 Perception failure):
 *   Zero taken claims must be explicitly visible in the status summary (telemetry feedback).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ClaimRightsDutiesMatrixTest {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SystemStatusService systemStatusService;

    private ProjectEntity project;
    private RoleEntity role;
    private AccountEntity eligibleAccount;

    @BeforeEach
    void setUp() {
        claimService.resetPullClaimsCountForTesting();

        project = new ProjectEntity();
        project.setName("Rights-Duties Test Project");
        project.setSlug("rights-duties-test");
        project.setRepositoryName("rights-duties-repo");
        project.setRepoUrl("https://github.com/test/rights-duties");
        project = projectRepository.saveAndFlush(project);

        role = roleRepository.findById("BARCAN-TAG-02").orElseGet(() -> {
            RoleEntity r = new RoleEntity();
            r.setTag("BARCAN-TAG-02");
            r.setDescription("Rigid Designator Role");
            r.setRulesPath("docs/rules/role-02.md");
            return roleRepository.saveAndFlush(r);
        });

        eligibleAccount = new AccountEntity();
        eligibleAccount.setName("Dvorkin Worker 01");
        eligibleAccount.setCapabilities("BARCAN-TAG-02");
        eligibleAccount.setApiKey("dvorkin-secret-key-100");
        eligibleAccount.setEnabled(true);
        eligibleAccount.setStatus(AccountStatus.idle);
        eligibleAccount.setLastHeartbeat(Instant.now());
        eligibleAccount = accountRepository.saveAndFlush(eligibleAccount);
    }

    private TaskEntity createQueuedTask(String desc) {
        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(role);
        task.setDescription(desc);
        task.setStatus(TaskStatus.queued);
        task.setPriority(50);
        task.setCreatedAt(Instant.now());
        return taskRepository.saveAndFlush(task);
    }

    @Test
    @DisplayName("Relation 1: Eligible authenticated account claims task, task becomes claimed, counter increments")
    void eligibleAuthenticatedAccountClaimsQueuedTask() {
        TaskEntity task = createQueuedTask("Prescription 40 target task");

        // Validate caller authorization before exercising claim right
        claimService.validateClaimantAuthorization(eligibleAccount.getId(), "dvorkin-secret-key-100", null, null);

        long beforeCount = claimService.getPullClaimsCount();
        ClaimDto claim = claimService.claim(eligibleAccount.getId(), List.of("BARCAN-TAG-02"));

        assertThat(claim).isNotNull();
        assertThat(claim.taskId()).isEqualTo(task.getId());
        assertThat(claimService.getPullClaimsCount()).isEqualTo(beforeCount + 1);

        TaskEntity reloadedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloadedTask.getStatus()).isEqualTo(TaskStatus.claimed);

        AccountEntity reloadedAccount = accountRepository.findById(eligibleAccount.getId()).orElseThrow();
        assertThat(reloadedAccount.getStatus()).isEqualTo(AccountStatus.busy);
    }

    @Test
    @DisplayName("Relation 2: Missing credentials for account with API key is denied with 401 UNAUTHORIZED")
    void missingCredentialsDeniedWith401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                claimService.validateClaimantAuthorization(eligibleAccount.getId(), null, null, null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getReason()).contains("Authentication required");
    }

    @Test
    @DisplayName("Relation 3: Invalid credentials for account is denied with 403 FORBIDDEN")
    void invalidCredentialsDeniedWith403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                claimService.validateClaimantAuthorization(eligibleAccount.getId(), "wrong-bearer-key", null, null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("Access denied");
    }

    @Test
    @DisplayName("Relation 4: Master API key authorizes claim even without individual account key")
    void masterKeyAuthorizesClaim() {
        createQueuedTask("Master authorized task");

        // When master key "eneik-test-secret-key-42" is provided in Authorization Bearer
        claimService.validateClaimantAuthorization(
                eligibleAccount.getId(), null, null, "Bearer eneik-test-secret-key-42");

        ClaimDto claim = claimService.claim(eligibleAccount.getId(), List.of("BARCAN-TAG-02"));
        assertThat(claim).isNotNull();
    }

    @Test
    @DisplayName("Relation 5: Disabled or decommissioned account is denied claim right with IllegalStateException")
    void disabledOrDecommissionedAccountDeniedClaimRight() {
        createQueuedTask("Disabled account task");

        // Case A: Disabled account
        AccountEntity disabledAccount = new AccountEntity();
        disabledAccount.setName("Disabled Worker");
        disabledAccount.setCapabilities("BARCAN-TAG-02");
        disabledAccount.setEnabled(false);
        disabledAccount.setStatus(AccountStatus.idle);
        final UUID disabledId = accountRepository.saveAndFlush(disabledAccount).getId();

        IllegalStateException disabledEx = assertThrows(IllegalStateException.class, () ->
                claimService.claim(disabledId, List.of("BARCAN-TAG-02")));
        assertThat(disabledEx.getMessage()).contains("disabled");

        // Case B: Decommissioned account
        AccountEntity decommAccount = new AccountEntity();
        decommAccount.setName("Decommissioned Worker");
        decommAccount.setCapabilities("BARCAN-TAG-02");
        decommAccount.setStatus(AccountStatus.decommissioned);
        final UUID decommId = accountRepository.saveAndFlush(decommAccount).getId();

        IllegalStateException decommEx = assertThrows(IllegalStateException.class, () ->
                claimService.claim(decommId, List.of("BARCAN-TAG-02")));
        assertThat(decommEx.getMessage()).contains("decommissioned");

        // Case C: API blocked account
        AccountEntity blockedAccount = new AccountEntity();
        blockedAccount.setName("Blocked Worker");
        blockedAccount.setCapabilities("BARCAN-TAG-02");
        blockedAccount.setEnabled(true);
        blockedAccount.setStatus(AccountStatus.api_blocked);
        final UUID blockedId = accountRepository.saveAndFlush(blockedAccount).getId();

        IllegalStateException blockedEx = assertThrows(IllegalStateException.class, () ->
                claimService.claim(blockedId, List.of("BARCAN-TAG-02")));
        assertThat(blockedEx.getMessage()).contains("api_blocked");
    }

    @Test
    @DisplayName("Relation 6: Teleosemantic feedback exposes taken claims and zero count in status summary")
    void teleosemanticFeedbackExposesClaimsInSummary() {
        Map<String, Object> status = systemStatusService.getStatus(project.getId());
        @SuppressWarnings("unchecked")
        Map<String, Object> tasksSection = (Map<String, Object>) status.get("tasks");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) tasksSection.get("data");

        assertThat(data).isNotNull();
        assertThat(data).containsKey("takenClaimsPast24Hours");
        assertThat(data).containsKey("pullClaimsCount");
        assertThat(data.get("pullClaimsCount")).isEqualTo(0L);

        // Perform a claim
        createQueuedTask("Teleosemantic feedback task");
        claimService.claim(eligibleAccount.getId(), List.of("BARCAN-TAG-02"));

        assertThat(claimService.getPullClaimsCount()).isEqualTo(1L);

        Map<String, Object> updatedStatus = systemStatusService.getStatus(project.getId());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedTasks = (Map<String, Object>) updatedStatus.get("tasks");
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedData = (Map<String, Object>) updatedTasks.get("data");
        assertThat(updatedData.get("pullClaimsCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("Relation 7: ClaimController enforces HTTP status codes for deontic authorization")
    void claimControllerEnforcesHttpResponses() {
        createQueuedTask("Controller task");
        ClaimController controller = new ClaimController(claimService);

        ClaimRequestDto req = new ClaimRequestDto(eligibleAccount.getId(), List.of("BARCAN-TAG-02"));

        // 1. Missing credentials -> 401 UNAUTHORIZED
        ResponseEntity<?> unauthResp = controller.claim(null, null, null, req);
        assertThat(unauthResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Invalid credentials -> 403 FORBIDDEN
        ResponseEntity<?> forbidResp = controller.claim("bogus-token", null, null, req);
        assertThat(forbidResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Valid credentials via X-Account-Key -> 200 OK
        ResponseEntity<?> okResp = controller.claim("dvorkin-secret-key-100", null, null, req);
        assertThat(okResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(okResp.getBody()).isInstanceOf(ClaimDto.class);

        // 4. When queue empty -> 204 NO_CONTENT
        ResponseEntity<?> emptyResp = controller.claim("dvorkin-secret-key-100", null, null, req);
        assertThat(emptyResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 5. Disabled account -> 400 BAD_REQUEST
        eligibleAccount.setEnabled(false);
        accountRepository.saveAndFlush(eligibleAccount);
        ResponseEntity<?> disabledResp = controller.claim("dvorkin-secret-key-100", null, null, req);
        assertThat(disabledResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
