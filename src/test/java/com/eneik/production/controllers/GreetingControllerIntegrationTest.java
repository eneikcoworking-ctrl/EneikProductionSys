package com.eneik.production.controllers;

import com.eneik.production.models.domain.GreetingStatus;
import com.eneik.production.models.persistence.GreetingEntity;
import com.eneik.production.repositories.GreetingRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GreetingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GreetingRepository greetingRepository;

    @MockBean
    private MLPredictionServiceClient mlServiceClient;

    @BeforeEach
    void setUp() {
        greetingRepository.deleteAll();
    }

    @Test
    void test1_CreateGreeting_Positive() throws Exception {
        String json = "{\"message\": \"Hello Agency\"}";

        mockMvc.perform(post("/api/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.message").value("Hello Agency"))
                .andExpect(jsonPath("$.currentStatus").value("RECEIVED"));
    }

    @Test
    void test2_CorsConfiguration() throws Exception {
        mockMvc.perform(options("/api/v1/greetings")
                .header("Access-Control-Request-Method", "POST")
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        mockMvc.perform(get("/api/v1/greetings/latest")
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isNotFound()) // No greetings yet but CORS should be there
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void test3_PiiRedaction_Email() throws Exception {
        String json = "{\"message\": \"Contact me at test@example.com\"}";

        mockMvc.perform(post("/api/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/greetings/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("[REDACTED]")))
                .andExpect(jsonPath("$.message").value(not(containsString("test@example.com"))));
    }

    @Test
    void test4_ComplianceViolation_CardNumber() throws Exception {
        String json = "{\"message\": \"My card is 1234-5678-1234-5678\"}";

        mockMvc.perform(post("/api/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Compliance Violation"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void test5_AiFallback() throws Exception {
        when(mlServiceClient.predictBottleneck(anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("AI Service Down"));

        String json = "{\"message\": \"Testing Fallback\"}";

        mockMvc.perform(post("/api/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Testing Fallback"));
    }
}
