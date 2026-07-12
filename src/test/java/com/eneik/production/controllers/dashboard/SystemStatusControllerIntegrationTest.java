package com.eneik.production.controllers.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SystemStatusControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void returnsGracefulStatusWithEmptyDataSources() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/system-status", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKeys(
                "integrations",
                "accounts",
                "githubAccess",
                "linearCompleteness",
                "julesSessions",
                "qualityGate",
                "tasks"
        );
        assertThat(section(response, "integrations")).containsEntry("available", true);
        assertThat(section(response, "accounts")).containsEntry("available", true);
    }

    @Test
    void returnsStatusFilteredByProject() {
        java.util.UUID projectId = java.util.UUID.randomUUID();
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/system-status?projectId=" + projectId, Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKeys(
                "integrations",
                "accounts",
                "githubAccess",
                "linearCompleteness",
                "julesSessions",
                "qualityGate",
                "tasks"
        );
        assertThat(section(response, "accounts")).containsEntry("available", true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(ResponseEntity<Map> response, String key) {
        return (Map<String, Object>) response.getBody().get(key);
    }
}
