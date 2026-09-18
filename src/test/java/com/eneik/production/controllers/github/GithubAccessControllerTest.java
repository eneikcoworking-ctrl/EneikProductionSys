package com.eneik.production.controllers.github;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint authority verification for GithubAccessController
 * (DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX / D006 Authorization ambiguity).
 *
 * Verifies allowed/denied rights across exposed controller paths:
 * - Mutating recheck without credentials -> 401 UNAUTHORIZED (denied)
 * - Mutating recheck with invalid key -> 403 FORBIDDEN (denied)
 * - Mutating recheck with valid key -> 200 OK (allowed)
 * - Safe status read -> 200 OK (allowed)
 * - Safe defect-rate calculation -> 200 OK (allowed)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class GithubAccessControllerTest {

    private static final String VALID_KEY = "eneik-test-secret-key-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Relation 1 Allowed: Safe read of project access status returns 200 OK")
    public void testGetAccessStatus() throws Exception {
        UUID projectId = UUID.randomUUID();
        String slug = "test-project-" + projectId;
        jdbcTemplate.update("INSERT INTO projects (id, name, slug, repository_name, status) VALUES (?, ?, ?, ?, ?)",
                projectId, "Test Project", slug, "test-repo", "active");

        mockMvc.perform(get("/api/projects/" + projectId + "/github-access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ci_status").value("skipped"));
    }

    @Test
    @DisplayName("Relation 2 Denied: POST recheck without credentials is denied with 401 UNAUTHORIZED")
    public void testRecheckAccessWithoutCredentialsIsDeniedWith401() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(post("/api/projects/" + projectId + "/github-access/recheck"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));
    }

    @Test
    @DisplayName("Relation 2 Denied: POST recheck with invalid API key is denied with 403 FORBIDDEN")
    public void testRecheckAccessWithInvalidApiKeyIsDeniedWith403() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(post("/api/projects/" + projectId + "/github-access/recheck")
                        .header("X-API-Key", "invalid-fake-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error").value(containsString("Access denied")));
    }

    @Test
    @DisplayName("Relation 2 Allowed: POST recheck with valid API key is allowed with 200 OK")
    public void testRecheckAccessWithValidApiKeyIsAllowedWith200() throws Exception {
        UUID projectId = UUID.randomUUID();
        String slug = "test-recheck-project-" + projectId;
        jdbcTemplate.update("INSERT INTO projects (id, name, slug, repository_name, status) VALUES (?, ?, ?, ?, ?)",
                projectId, "Recheck Project", slug, "test-recheck-repo", "active");

        mockMvc.perform(post("/api/projects/" + projectId + "/github-access/recheck")
                        .header("X-API-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ci_status").value("skipped"));
    }

    @Test
    @DisplayName("Relation 3 Allowed: GET defect-rate calculation is allowed with 200 OK")
    public void testGetDefectRateIsAllowed() throws Exception {
        mockMvc.perform(get("/api/github-access/defect-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChecks").exists());
    }
}
