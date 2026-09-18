package com.eneik.production.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Integration security test verifying that all mutating surfaces of InternalGeminiObserverController
 * are rejected without valid authorization credentials, enforcing BOUNDARY_TOPOLOGY
 * (Varzi 1999 / D006, Prescription 59).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InternalGeminiObserverSecurityIntegrationTest {

    private static final String VALID_KEY = "eneik-test-secret-key-42";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Mutating POST /retire-stuck-worker-now without credentials is rejected with 401 UNAUTHORIZED")
    void retireStuckWorkerWithoutCredentialsIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/internal/gemini-observer/retire-stuck-worker-now")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                        .param("projectId", UUID.randomUUID().toString())
                        .param("carrierTaskId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));
    }

    @Test
    @DisplayName("Mutating POST /release-finalizing-wishlist without credentials is rejected with 401 UNAUTHORIZED")
    void releaseFinalizingWishlistWithoutCredentialsIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/internal/gemini-observer/release-finalizing-wishlist")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                        .param("wishlistId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));
    }

    @Test
    @DisplayName("Mutating POST /reset-daily-session-counts-now without credentials is rejected with 401 UNAUTHORIZED")
    void resetDailySessionCountsWithoutCredentialsIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/internal/gemini-observer/reset-daily-session-counts-now")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));
    }

    @Test
    @DisplayName("Mutating POST /clear-corrupted-session-pr-url without credentials is rejected with 401 UNAUTHORIZED")
    void clearCorruptedSessionPrUrlWithoutCredentialsIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/internal/gemini-observer/clear-corrupted-session-pr-url")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                        .param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value(containsString("Authorization required")));
    }

    @Test
    @DisplayName("Mutating POST with invalid X-API-Key is rejected with 403 FORBIDDEN")
    void mutatingWithInvalidApiKeyIsRejectedWith403() throws Exception {
        mockMvc.perform(post("/internal/gemini-observer/reset-daily-session-counts-now")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                        .header("X-API-Key", "invalid-key-attempt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error").value(containsString("invalid authorization credentials")));
    }

    @Test
    @DisplayName("Safe diagnostic read GET /db-table-sizes from localhost without credentials is allowed (200 OK)")
    void safeReadFromLocalhostWithoutCredentialsIsAllowed() throws Exception {
        mockMvc.perform(get("/internal/gemini-observer/db-table-sizes")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Safe diagnostic read GET /db-table-sizes from non-localhost IP without credentials is rejected with 403 FORBIDDEN")
    void safeReadFromExternalWithoutCredentialsIsRejectedWith403() throws Exception {
        mockMvc.perform(get("/internal/gemini-observer/db-table-sizes")
                        .with(req -> { req.setRemoteAddr("198.51.100.42"); return req; }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error").value(containsString("restricted to localhost")));
    }

    @Test
    @DisplayName("Mutating POST /reset-daily-session-counts-now with valid X-API-Key is authorized and succeeds (200 OK)")
    void resetDailySessionCountsWithValidCredentialsSucceeds() throws Exception {
        mockMvc.perform(post("/internal/gemini-observer/reset-daily-session-counts-now")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                        .header("X-API-Key", VALID_KEY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mutating POST /release-finalizing-wishlist with valid Bearer token is authorized and succeeds (200 OK)")
    void releaseFinalizingWishlistWithValidBearerSucceeds() throws Exception {
        mockMvc.perform(post("/internal/gemini-observer/release-finalizing-wishlist")
                        .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                        .header("Authorization", "Bearer " + VALID_KEY)
                        .param("wishlistId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
