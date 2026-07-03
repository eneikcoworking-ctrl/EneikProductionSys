package com.eneik.production.controllers.github;

import com.eneik.production.services.github.GithubAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class GithubAccessControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testGetAccessStatus() {
        // Create a dummy project
        UUID projectId = UUID.randomUUID();
        String slug = "test-project-" + projectId;
        jdbcTemplate.update("INSERT INTO projects (id, name, slug, repository_name, status) VALUES (?, ?, ?, ?, ?)",
                projectId, "Test Project", slug, "test-repo", "active");

        ResponseEntity<String> response = restTemplate.getForEntity("/api/projects/" + projectId + "/github-access", String.class);

        System.out.println("Actual JSON response for GET /github-access:");
        System.out.println(response.getBody());

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("skipped");
    }
}
