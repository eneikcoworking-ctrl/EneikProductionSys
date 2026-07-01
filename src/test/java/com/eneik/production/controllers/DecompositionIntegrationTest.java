package com.eneik.production.controllers;

import com.eneik.production.dto.DecompositionResponseDto;
import com.eneik.production.dto.RequirementRequestDto;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DecompositionIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    public void testCreateRequirementAndVerifyTasks() {
        RequirementRequestDto request = new RequirementRequestDto("Нужна форма регистрации с email и паролем");

        ResponseEntity<DecompositionResponseDto> response = restTemplate.postForEntity(
                "/api/requirements", request, DecompositionResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().requirementId()).isNotNull();
        assertThat(response.getBody().createdTasks()).isNotEmpty();

        // Check if tags are present (ui keywords -> TAG-03, TAG-11; auth keywords -> TAG-07; + always TAG-00, TAG-01)
        assertThat(response.getBody().createdTasks()).extracting("tag")
                .contains("BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-03", "BARCAN-TAG-11", "BARCAN-TAG-07");

        // Verify in DB
        response.getBody().createdTasks().forEach(taskShort -> {
            var taskEntity = taskRepository.findById(taskShort.id());
            assertThat(taskEntity).isPresent();
            assertThat(taskEntity.get().getStatus()).isEqualTo(TaskStatus.queued);
            assertThat(taskEntity.get().getPayload().get("requirementText").asText()).isEqualTo(request.text());
            assertThat(taskEntity.get().getPayload().get("sourceRequirementId").asText()).isEqualTo(response.getBody().requirementId().toString());
        });
    }

    @Test
    public void testCreateEmptyRequirement() {
        RequirementRequestDto request = new RequirementRequestDto("");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/requirements", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
