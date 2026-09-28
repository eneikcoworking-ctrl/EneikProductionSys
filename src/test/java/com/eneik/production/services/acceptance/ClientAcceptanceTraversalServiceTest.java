package com.eneik.production.services.acceptance;

import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalDto;
import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalRequestDto;
import com.eneik.production.models.persistence.ClientAcceptanceTraversalEntity;
import com.eneik.production.repositories.ClientAcceptanceTraversalRepository;
import com.eneik.production.repositories.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тест-заслон для ClientAcceptanceTraversalService (AGY_ASKS #1, V100).
 *
 * Проверяемые паттерны:
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER [D007, Searle]
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_07_RIGHTS_DUTIES_MATRIX [D006, Searle]
 */
class ClientAcceptanceTraversalServiceTest {

    private ClientAcceptanceTraversalRepository traversalRepository;
    private ProjectRepository projectRepository;
    private ClientAcceptanceTraversalService service;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        traversalRepository = mock(ClientAcceptanceTraversalRepository.class);
        projectRepository = mock(ProjectRepository.class);
        service = new ClientAcceptanceTraversalService(traversalRepository, projectRepository);

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(traversalRepository.save(any(ClientAcceptanceTraversalEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("DZHON_SERL_05: Регистрация обхода по умолчанию присваивает walkedBy='client' (институциональный факт)")
    void recordTraversalDefaultsToClientWalkedBy() {
        var request = new ClientAcceptanceTraversalRequestDto(
                "profile-auth",
                "customer-lead",
                "user clicks login and sees dashboard",
                null,
                "screenshot: https://test-stand.local/auth.png",
                "https://test-stand.local"
        );

        ClientAcceptanceTraversalDto result = service.recordTraversal(projectId, request);

        assertThat(result).isNotNull();
        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.profileId()).isEqualTo("profile-auth");
        assertThat(result.actor()).isEqualTo("customer-lead");
        assertThat(result.link()).isEqualTo("user clicks login and sees dashboard");
        assertThat(result.walkedBy()).isEqualTo("client");
        assertThat(result.evidence()).isEqualTo("screenshot: https://test-stand.local/auth.png");
        assertThat(result.instanceUrl()).isEqualTo("https://test-stand.local");
        assertThat(result.traversedAt()).isNotNull();

        ArgumentCaptor<ClientAcceptanceTraversalEntity> captor = ArgumentCaptor.forClass(ClientAcceptanceTraversalEntity.class);
        verify(traversalRepository).save(captor.capture());
        ClientAcceptanceTraversalEntity saved = captor.getValue();
        assertThat(saved.getWalkedBy()).isEqualTo("client");
        assertThat(saved.getProjectId()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("DZHON_SERL_07: Разделение субъектов обхода — сохранение явного walkedBy='factory'")
    void recordTraversalPreservesExplicitFactoryWalkedBy() {
        var request = new ClientAcceptanceTraversalRequestDto(
                "profile-checkout",
                "qa-bot",
                "checkout button navigates to payment",
                "factory",
                "playwright trace recorded",
                "https://qa-stand.local"
        );

        ClientAcceptanceTraversalDto result = service.recordTraversal(projectId, request);

        assertThat(result.walkedBy()).isEqualTo("factory");
        ArgumentCaptor<ClientAcceptanceTraversalEntity> captor = ArgumentCaptor.forClass(ClientAcceptanceTraversalEntity.class);
        verify(traversalRepository).save(captor.capture());
        assertThat(captor.getValue().getWalkedBy()).isEqualTo("factory");
    }

    @Test
    @DisplayName("Защита границы: выброс NoSuchElementException при отсутствии проекта в базе")
    void recordTraversalThrowsWhenProjectDoesNotExist() {
        UUID unknownProject = UUID.randomUUID();
        when(projectRepository.existsById(unknownProject)).thenReturn(false);

        var request = new ClientAcceptanceTraversalRequestDto("profile-1", "user", "click button");

        assertThatThrownBy(() -> service.recordTraversal(unknownProject, request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Project not found");

        verify(traversalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Защита контракта: обязательность полей profileId, actor, link")
    void recordTraversalValidatesRequiredFields() {
        // null profileId
        assertThatThrownBy(() -> service.recordTraversal(projectId, new ClientAcceptanceTraversalRequestDto(null, "actor", "link")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileId must not be blank");

        // blank actor
        assertThatThrownBy(() -> service.recordTraversal(projectId, new ClientAcceptanceTraversalRequestDto("profile", "  ", "link")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor must not be blank");

        // blank link
        assertThatThrownBy(() -> service.recordTraversal(projectId, new ClientAcceptanceTraversalRequestDto("profile", "actor", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("link must not be blank");

        // null body
        assertThatThrownBy(() -> service.recordTraversal(projectId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request body must not be null");

        // null projectId
        assertThatThrownBy(() -> service.recordTraversal(null, new ClientAcceptanceTraversalRequestDto("profile", "actor", "link")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId must not be null");
    }

    @Test
    @DisplayName("Защита схемы V100: усечение сверхдлинных полей под лимиты таблицы")
    void recordTraversalTruncatesOversizedFields() {
        String longProfileId = "P".repeat(100); // max 64
        String longActor = "A".repeat(100);     // max 64
        String longLink = "L".repeat(600);      // max 512
        String longWalkedBy = "W".repeat(50);   // max 32
        String longEvidence = "E".repeat(2500); // max 2000
        String longUrl = "U".repeat(600);       // max 512

        var request = new ClientAcceptanceTraversalRequestDto(
                longProfileId, longActor, longLink, longWalkedBy, longEvidence, longUrl
        );

        ClientAcceptanceTraversalDto result = service.recordTraversal(projectId, request);

        assertThat(result.profileId()).hasSize(64);
        assertThat(result.actor()).hasSize(64);
        assertThat(result.link()).hasSize(512);
        assertThat(result.walkedBy()).hasSize(32);
        assertThat(result.evidence()).hasSize(2000);
        assertThat(result.instanceUrl()).hasSize(512);
    }

    @Test
    @DisplayName("listTraversals возвращает список обходов проекта")
    void listTraversalsReturnsListOrderedByTime() {
        ClientAcceptanceTraversalEntity e1 = new ClientAcceptanceTraversalEntity();
        e1.setId(UUID.randomUUID());
        e1.setProjectId(projectId);
        e1.setProfileId("p1");
        e1.setActor("a1");
        e1.setLink("l1");
        e1.setWalkedBy("client");
        e1.setTraversedAt(Instant.now());

        when(traversalRepository.findByProjectIdOrderByTraversedAtDesc(projectId))
                .thenReturn(List.of(e1));

        List<ClientAcceptanceTraversalDto> list = service.listTraversals(projectId, null);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).profileId()).isEqualTo("p1");
        verify(traversalRepository).findByProjectIdOrderByTraversedAtDesc(projectId);
    }

    @Test
    @DisplayName("listTraversals с фильтром walkedBy вызывает специализированный метод репозитория")
    void listTraversalsWithFilterCallsFilteredMethod() {
        when(traversalRepository.findByProjectIdAndWalkedByIgnoreCaseOrderByTraversedAtDesc(projectId, "client"))
                .thenReturn(List.of());

        List<ClientAcceptanceTraversalDto> list = service.listTraversals(projectId, "client");

        assertThat(list).isEmpty();
        verify(traversalRepository).findByProjectIdAndWalkedByIgnoreCaseOrderByTraversedAtDesc(projectId, "client");
    }

    @Test
    @DisplayName("countClientTraversals корректно подсчитывает обходы заказчика")
    void countClientTraversalsCountsOnlyClientWalks() {
        when(traversalRepository.countByProjectIdAndWalkedByIgnoreCase(projectId, "client")).thenReturn(5L);

        long count = service.countClientTraversals(projectId);

        assertThat(count).isEqualTo(5L);
        verify(traversalRepository).countByProjectIdAndWalkedByIgnoreCase(projectId, "client");
    }
}
