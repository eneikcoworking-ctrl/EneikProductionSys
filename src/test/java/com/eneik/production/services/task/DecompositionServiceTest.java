package com.eneik.production.services.task;

import com.eneik.production.dto.DecompositionResponseDto;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class DecompositionServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RoleRepository roleRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    private DecompositionService decompositionService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        decompositionService = new DecompositionService(taskRepository, roleRepository, objectMapper);

        when(roleRepository.findById(anyString())).thenAnswer(invocation -> {
            String tag = invocation.getArgument(0);
            RoleEntity role = new RoleEntity();
            role.setTag(tag);
            return Optional.of(role);
        });

        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void testDecomposeUiRequirement() {
        String requirement = "Нужна форма регистрации";
        DecompositionResponseDto response = decompositionService.decompose(requirement);

        assertThat(response.createdTasks()).hasSize(4); // 00, 01, 03, 11
        assertThat(response.createdTasks()).extracting("tag")
                .containsExactlyInAnyOrder("BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-03", "BARCAN-TAG-11");
    }

    @Test
    public void testDecomposeDatabaseRequirement() {
        String requirement = "Создать схему базы данных";
        DecompositionResponseDto response = decompositionService.decompose(requirement);

        assertThat(response.createdTasks()).hasSize(3); // 00, 01, 08
        assertThat(response.createdTasks()).extracting("tag")
                .containsExactlyInAnyOrder("BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-08");
    }

    @Test
    public void testDecomposeApiRequirement() {
        String requirement = "Реализовать backend api";
        DecompositionResponseDto response = decompositionService.decompose(requirement);

        assertThat(response.createdTasks()).hasSize(3); // 00, 01, 02
        assertThat(response.createdTasks()).extracting("tag")
                .containsExactlyInAnyOrder("BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-02");
    }

    @Test
    public void testDecomposeAuthRequirement() {
        String requirement = "Настроить пароль и логин";
        DecompositionResponseDto response = decompositionService.decompose(requirement);

        assertThat(response.createdTasks()).hasSize(3); // 00, 01, 07
        assertThat(response.createdTasks()).extracting("tag")
                .containsExactlyInAnyOrder("BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-07");
    }

    @Test
    public void testDecomposeUnmatchedRequirement() {
        String requirement = "Просто какой-то текст";
        DecompositionResponseDto response = decompositionService.decompose(requirement);

        assertThat(response.createdTasks()).hasSize(2); // 00, 01
        assertThat(response.createdTasks()).extracting("tag")
                .containsExactlyInAnyOrder("BARCAN-TAG-00", "BARCAN-TAG-01");
    }
}
