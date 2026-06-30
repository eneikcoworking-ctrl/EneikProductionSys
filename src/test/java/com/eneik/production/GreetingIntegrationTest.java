package com.eneik.production;

import com.eneik.production.dto.GreetingResponseDTO;
import com.eneik.production.models.persistence.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GreetingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testCreateAndGetLatestGreeting() {
        // 1. Create a greeting
        Map<String, String> request = new HashMap<>();
        request.put("message", "Test Lean Greeting");

        ResponseEntity<GreetingResponseDTO> postResponse = restTemplate.postForEntity(
            "/api/v1/greetings", request, GreetingResponseDTO.class);

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResponse.getBody()).isNotNull();
        assertThat(postResponse.getBody().getMessage()).isEqualTo("Test Lean Greeting");
        assertThat(postResponse.getBody().getCurrentStatus()).isEqualTo(Status.RECEIVED);
        assertThat(postResponse.getBody().getLeadTimeSeconds()).isGreaterThanOrEqualTo(0);

        // 2. Get latest greeting
        ResponseEntity<GreetingResponseDTO> getResponse = restTemplate.getForEntity(
            "/api/v1/greetings/latest", GreetingResponseDTO.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getId()).isEqualTo(postResponse.getBody().getId());
        assertThat(getResponse.getBody().getMessage()).isEqualTo("Test Lean Greeting");
    }

    @Test
    public void testCreateInvalidGreeting() {
        Map<String, String> request = new HashMap<>();
        request.put("message", "");

        ResponseEntity<Map> postResponse = restTemplate.postForEntity(
            "/api/v1/greetings", request, Map.class);

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postResponse.getBody()).containsEntry("error", "Compliance Violation");
        assertThat(postResponse.getBody()).containsEntry("code", 400);
    }
}
