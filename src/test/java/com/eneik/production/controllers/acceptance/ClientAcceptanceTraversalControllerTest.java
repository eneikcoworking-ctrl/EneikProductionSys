package com.eneik.production.controllers.acceptance;

import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalDto;
import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalRequestDto;
import com.eneik.production.security.ApiAuthorizationInterceptor;
import com.eneik.production.services.acceptance.ClientAcceptanceTraversalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring WebMvc тесты для ClientAcceptanceTraversalController (AGY_ASKS #1, V100).
 *
 * Паттерны:
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER [D007, Searle]
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_07_RIGHTS_DUTIES_MATRIX [D006, Searle]
 */
@WebMvcTest(ClientAcceptanceTraversalController.class)
class ClientAcceptanceTraversalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientAcceptanceTraversalService traversalService;

    @MockBean
    private ApiAuthorizationInterceptor apiAuthorizationInterceptor;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        when(apiAuthorizationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("POST /api/projects/{projectId}/acceptance-traversals: успешная регистрация обхода возвращает 201 Created")
    void recordTraversalReturns201Created() throws Exception {
        UUID traversalId = UUID.randomUUID();
        ClientAcceptanceTraversalDto dto = new ClientAcceptanceTraversalDto(
                traversalId,
                projectId,
                "profile-demo",
                "chief-client",
                "customer successfully logs in",
                Instant.now(),
                "client",
                "video recording /artifacts/demo.mp4",
                "https://demo.eneik.com"
        );

        when(traversalService.recordTraversal(eq(projectId), any(ClientAcceptanceTraversalRequestDto.class)))
                .thenReturn(dto);

        String json = """
                {
                    "profileId": "profile-demo",
                    "actor": "chief-client",
                    "link": "customer successfully logs in",
                    "walkedBy": "client",
                    "evidence": "video recording /artifacts/demo.mp4",
                    "instanceUrl": "https://demo.eneik.com"
                }
                """;

        mockMvc.perform(post("/api/projects/{projectId}/acceptance-traversals", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(traversalId.toString())))
                .andExpect(jsonPath("$.projectId", is(projectId.toString())))
                .andExpect(jsonPath("$.profileId", is("profile-demo")))
                .andExpect(jsonPath("$.actor", is("chief-client")))
                .andExpect(jsonPath("$.link", is("customer successfully logs in")))
                .andExpect(jsonPath("$.walkedBy", is("client")))
                .andExpect(jsonPath("$.evidence", is("video recording /artifacts/demo.mp4")))
                .andExpect(jsonPath("$.instanceUrl", is("https://demo.eneik.com")));
    }

    @Test
    @DisplayName("POST /api/projects/{projectId}/acceptance-traversals: проект не найден возвращает 404 Not Found")
    void recordTraversalReturns404WhenProjectNotFound() throws Exception {
        when(traversalService.recordTraversal(eq(projectId), any()))
                .thenThrow(new NoSuchElementException("Project not found: " + projectId));

        String json = """
                {
                    "profileId": "profile-demo",
                    "actor": "client",
                    "link": "link"
                }
                """;

        mockMvc.perform(post("/api/projects/{projectId}/acceptance-traversals", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(404)))
                .andExpect(jsonPath("$.error", is("Project not found: " + projectId)));
    }

    @Test
    @DisplayName("POST /api/projects/{projectId}/acceptance-traversals: невалидные данные возвращают 400 Bad Request")
    void recordTraversalReturns400OnInvalidData() throws Exception {
        when(traversalService.recordTraversal(eq(projectId), any()))
                .thenThrow(new IllegalArgumentException("link must not be blank"));

        String json = """
                {
                    "profileId": "profile-demo",
                    "actor": "client",
                    "link": ""
                }
                """;

        mockMvc.perform(post("/api/projects/{projectId}/acceptance-traversals", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.error", is("link must not be blank")));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/acceptance-traversals: возвращает 200 OK и список обходов")
    void listTraversalsReturns200WithList() throws Exception {
        UUID traversalId = UUID.randomUUID();
        ClientAcceptanceTraversalDto dto = new ClientAcceptanceTraversalDto(
                traversalId,
                projectId,
                "profile-1",
                "client-user",
                "link-1",
                Instant.now(),
                "client",
                null,
                null
        );

        when(traversalService.listTraversals(projectId, null))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/projects/{projectId}/acceptance-traversals", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(traversalId.toString())))
                .andExpect(jsonPath("$[0].profileId", is("profile-1")));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/acceptance-traversals?walkedBy=client: передаёт фильтр в сервис")
    void listTraversalsWithFilterReturnsFilteredList() throws Exception {
        when(traversalService.listTraversals(projectId, "client"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/projects/{projectId}/acceptance-traversals", projectId)
                        .param("walkedBy", "client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
