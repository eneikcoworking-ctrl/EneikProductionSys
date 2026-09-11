package com.eneik.production.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ApiAuthorizationInterceptor verifying the Rights/Duties Matrix and Prohibition as Code
 * (DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX, DZHOZEF_RAZ_01_PROHIBITION_AS_CODE / D006 Authorization ambiguity).
 *
 * Proof obligation: Show the matrix and tests for at least one allowed and one denied action per relation.
 */
class ApiAuthorizationInterceptorTest {

    private static final String VALID_KEY = "eneik-test-secret-key-42";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiAuthorizationInterceptor interceptor = new ApiAuthorizationInterceptor(VALID_KEY, objectMapper);

    @Test
    @DisplayName("Relation 1: Unauthenticated request to mutating AI resource endpoints is denied with 401 UNAUTHORIZED")
    void mutatingAiEndpointsWithoutCredentialsAreDeniedWith401() throws Exception {
        String[] mutatingPaths = {
                "/api/ai/resources/probe-models",
                "/api/ai/resources/design-drafts-cleanup",
                "/api/ai/resources/design-assets",
                "/api/ai/resources/stitch-design-system",
                "/api/ai/resources/video-assets"
        };

        for (String path : mutatingPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(request, response, new Object());

            assertFalse(allowed, "Mutating endpoint " + path + " must be denied without credentials");
            assertEquals(401, response.getStatus(), "Status must be 401 Unauthorized for " + path);

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertEquals("UNAUTHORIZED", body.get("code").asText());
            assertTrue(body.get("error").asText().contains("Authorization required"));
        }
    }

    @Test
    @DisplayName("Relation 2: Mutating AI request with invalid credentials is denied with 403 FORBIDDEN")
    void mutatingAiEndpointsWithInvalidKeyAreDeniedWith403() throws Exception {
        // Test with invalid X-API-Key
        MockHttpServletRequest request1 = new MockHttpServletRequest("POST", "/api/ai/resources/probe-models");
        request1.setRequestURI("/api/ai/resources/probe-models");
        request1.addHeader("X-API-Key", "invalid-key-xyz");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        boolean allowed1 = interceptor.preHandle(request1, response1, new Object());
        assertFalse(allowed1);
        assertEquals(403, response1.getStatus());
        JsonNode body1 = objectMapper.readTree(response1.getContentAsString());
        assertEquals("FORBIDDEN", body1.get("code").asText());

        // Test with invalid Bearer token
        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/ai/resources/probe-models");
        request2.setRequestURI("/api/ai/resources/probe-models");
        request2.addHeader("Authorization", "Bearer invalid-token-abc");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        boolean allowed2 = interceptor.preHandle(request2, response2, new Object());
        assertFalse(allowed2);
        assertEquals(403, response2.getStatus());
        JsonNode body2 = objectMapper.readTree(response2.getContentAsString());
        assertEquals("FORBIDDEN", body2.get("code").asText());
    }

    @Test
    @DisplayName("Relation 3: Mutating AI request with valid credentials is authorized and allowed")
    void mutatingAiEndpointsWithValidKeyAreAllowed() throws Exception {
        // Via X-API-Key
        MockHttpServletRequest requestApiKey = new MockHttpServletRequest("POST", "/api/ai/resources/probe-models");
        requestApiKey.setRequestURI("/api/ai/resources/probe-models");
        requestApiKey.addHeader("X-API-Key", VALID_KEY);
        MockHttpServletResponse responseApiKey = new MockHttpServletResponse();

        boolean allowedApiKey = interceptor.preHandle(requestApiKey, responseApiKey, new Object());
        assertTrue(allowedApiKey, "Valid X-API-Key must be allowed");
        assertEquals(200, responseApiKey.getStatus());

        // Via Bearer token
        MockHttpServletRequest requestBearer = new MockHttpServletRequest("POST", "/api/ai/resources/design-drafts-cleanup");
        requestBearer.setRequestURI("/api/ai/resources/design-drafts-cleanup");
        requestBearer.addHeader("Authorization", "Bearer " + VALID_KEY);
        MockHttpServletResponse responseBearer = new MockHttpServletResponse();

        boolean allowedBearer = interceptor.preHandle(requestBearer, responseBearer, new Object());
        assertTrue(allowedBearer, "Valid Bearer token must be allowed");
        assertEquals(200, responseBearer.getStatus());
    }

    @Test
    @DisplayName("Relation 4: Safe read-only GET endpoints on /api/ai/resources are allowed without credentials")
    void safeReadEndpointsArePubliclyAllowed() throws Exception {
        String[] readPaths = {
                "/api/ai/resources",
                "/api/ai/resources/design-consistency-audit",
                "/api/ai/resources/stitch-tools-debug",
                "/api/ai/resources/video-assets/sample-project"
        };

        for (String path : readPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(request, response, new Object());
            assertTrue(allowed, "Read-only GET path " + path + " must be allowed without credentials");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @DisplayName("Relation 5: External non-localhost request to /internal/** without credentials is denied with 403 FORBIDDEN")
    void externalInternalEndpointRequestWithoutCredentialsIsDeniedWith403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/tasks");
        request.setRequestURI("/internal/tasks");
        request.setRemoteAddr("198.51.100.24"); // external IP
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertFalse(allowed, "External access to internal endpoint must be denied");
        assertEquals(403, response.getStatus());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("FORBIDDEN", body.get("code").asText());
        assertTrue(body.get("error").asText().contains("restricted to localhost"));
    }

    @Test
    @DisplayName("Relation 6: Localhost request to /internal/** is allowed")
    void localhostInternalEndpointRequestIsAllowed() throws Exception {
        String[] loopbacks = {"127.0.0.1", "::1", "localhost"};

        for (String ip : loopbacks) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/tasks");
            request.setRequestURI("/internal/tasks");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(request, response, new Object());
            assertTrue(allowed, "Localhost " + ip + " must be allowed on /internal/tasks");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @DisplayName("Relation 7: External request to /internal/** with valid operator credentials is allowed")
    void externalInternalEndpointRequestWithValidCredentialsIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/tasks");
        request.setRequestURI("/internal/tasks");
        request.setRemoteAddr("198.51.100.24"); // external IP
        request.addHeader("X-API-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertTrue(allowed, "External request with valid operator key must be allowed");
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Relation 8: When API key is not configured, mutating AI requests are rejected with 403 FORBIDDEN")
    void mutatingAiEndpointsWhenApiKeyNotConfiguredAreDeniedWith403() throws Exception {
        ApiAuthorizationInterceptor unconfiguredInterceptor = new ApiAuthorizationInterceptor("", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/resources/probe-models");
        request.setRequestURI("/api/ai/resources/probe-models");
        request.addHeader("X-API-Key", "any-key-attempt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = unconfiguredInterceptor.preHandle(request, response, new Object());
        assertFalse(allowed, "Must be denied when API key is not configured");
        assertEquals(403, response.getStatus());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("FORBIDDEN", body.get("code").asText());
        assertTrue(body.get("error").asText().contains("not configured"));
    }

    @Test
    @DisplayName("Relation 9: Non-loopback private addresses (.1) on /internal/** without API key are denied with 403 (GILBERT_RAYL_03_CATEGORY_ERROR_SCAN)")
    void nonLoopbackPrivateAddressesRequireApiKey() throws Exception {
        String[] hostGateways = {"172.18.0.1", "172.17.0.1", "10.0.0.1", "192.168.1.1"};

        for (String ip : hostGateways) {
            // Without API key -> denied
            MockHttpServletRequest reqDenied = new MockHttpServletRequest("GET", "/internal/tasks");
            reqDenied.setRequestURI("/internal/tasks");
            reqDenied.setRemoteAddr(ip);
            MockHttpServletResponse respDenied = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(reqDenied, respDenied, new Object());
            assertFalse(allowed, "Address " + ip + " without API key must not be allowed via address-form wildcard");
            assertEquals(403, respDenied.getStatus());

            // With valid API key -> allowed
            MockHttpServletRequest reqAllowed = new MockHttpServletRequest("GET", "/internal/tasks");
            reqAllowed.setRequestURI("/internal/tasks");
            reqAllowed.setRemoteAddr(ip);
            reqAllowed.addHeader("X-API-Key", VALID_KEY);
            MockHttpServletResponse respAllowed = new MockHttpServletResponse();

            boolean allowedWithKey = interceptor.preHandle(reqAllowed, respAllowed, new Object());
            assertTrue(allowedWithKey, "Address " + ip + " with valid API key must be allowed");
            assertEquals(200, respAllowed.getStatus());
        }
    }

    @Test
    @DisplayName("Relation 10: Unauthenticated mutating request to console endpoints (/api/accounts, /api/settings, /api/projects) is denied with 401 (Prescription 24)")
    void mutatingConsoleEndpointsWithoutCredentialsAreDeniedWith401() throws Exception {
        record TestCase(String method, String path) {}
        TestCase[] testCases = {
                new TestCase("POST", "/api/accounts"),
                new TestCase("PATCH", "/api/accounts/acc-123"),
                new TestCase("DELETE", "/api/accounts/acc-123"),
                new TestCase("PUT", "/api/settings"),
                new TestCase("POST", "/api/projects"),
                new TestCase("DELETE", "/api/projects/proj-456"),
                new TestCase("POST", "/api/wishlist"),
                new TestCase("PATCH", "/api/wishlist/w-789/dismiss")
        };

        for (TestCase tc : testCases) {
            MockHttpServletRequest request = new MockHttpServletRequest(tc.method, tc.path);
            request.setRequestURI(tc.path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(request, response, new Object());

            assertFalse(allowed, "Mutating console endpoint " + tc.method + " " + tc.path + " must be denied without credentials");
            assertEquals(401, response.getStatus(), "Status must be 401 Unauthorized for " + tc.path);

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertEquals("UNAUTHORIZED", body.get("code").asText());
            assertTrue(body.get("error").asText().contains("Authorization required"));
        }
    }

    @Test
    @DisplayName("Relation 11: Mutating console request with invalid credentials is denied with 403 FORBIDDEN")
    void mutatingConsoleEndpointsWithInvalidKeyAreDeniedWith403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/accounts/acc-123");
        request.setRequestURI("/api/accounts/acc-123");
        request.addHeader("X-API-Key", "invalid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertFalse(allowed);
        assertEquals(403, response.getStatus());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("FORBIDDEN", body.get("code").asText());
    }

    @Test
    @DisplayName("Relation 12: Mutating console request with valid credentials (X-API-Key or Bearer) is authorized")
    void mutatingConsoleEndpointsWithValidKeyAreAllowed() throws Exception {
        // Via X-API-Key
        MockHttpServletRequest reqKey = new MockHttpServletRequest("PATCH", "/api/accounts/acc-123");
        reqKey.setRequestURI("/api/accounts/acc-123");
        reqKey.addHeader("X-API-Key", VALID_KEY);
        MockHttpServletResponse respKey = new MockHttpServletResponse();

        boolean allowedKey = interceptor.preHandle(reqKey, respKey, new Object());
        assertTrue(allowedKey, "Valid X-API-Key must be allowed for PATCH /api/accounts/acc-123");
        assertEquals(200, respKey.getStatus());

        // Via Bearer
        MockHttpServletRequest reqBearer = new MockHttpServletRequest("PUT", "/api/settings");
        reqBearer.setRequestURI("/api/settings");
        reqBearer.addHeader("Authorization", "Bearer " + VALID_KEY);
        MockHttpServletResponse respBearer = new MockHttpServletResponse();

        boolean allowedBearer = interceptor.preHandle(reqBearer, respBearer, new Object());
        assertTrue(allowedBearer, "Valid Bearer must be allowed for PUT /api/settings");
        assertEquals(200, respBearer.getStatus());
    }

    @Test
    @DisplayName("Relation 13: Webhooks (/api/webhooks/**) with mutating operations require credentials (NUEL_BELNAP_06_SUBSTITUTION_ORACLE / D009)")
    void mutatingWebhookEndpointWithoutCredentialsIsDeniedWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/github");
        request.setRequestURI("/api/webhooks/github");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertFalse(allowed, "Webhook without credentials must be denied to prevent unauthorized PR spoofing");
        assertEquals(401, response.getStatus());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("UNAUTHORIZED", body.get("code").asText());
    }

    @Test
    @DisplayName("Relation 13b: Webhook endpoint with valid API key is allowed")
    void mutatingWebhookEndpointWithValidApiKeyIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/github");
        request.setRequestURI("/api/webhooks/github");
        request.addHeader("X-API-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertTrue(allowed, "Webhook with valid API key must be allowed");
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Relation 14: Safe read-only GET endpoints on console paths are allowed without credentials")
    void safeReadConsoleEndpointsArePubliclyAllowed() throws Exception {
        String[] readPaths = {
                "/api/accounts",
                "/api/settings",
                "/api/projects",
                "/api/wishlist"
        };

        for (String path : readPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(request, response, new Object());
            assertTrue(allowed, "Safe read GET path " + path + " must be allowed without credentials");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @DisplayName("Relation 15: Mutating requests on /internal/** from localhost without credentials are denied with 401 (Prescription 59)")
    void mutatingInternalRequestsFromLocalhostWithoutCredentialsAreDeniedWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/gemini-observer/retire-stuck-worker-now");
        request.setRequestURI("/internal/gemini-observer/retire-stuck-worker-now");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertFalse(allowed, "Mutating internal operations from localhost must require credentials");
        assertEquals(401, response.getStatus());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("UNAUTHORIZED", body.get("code").asText());
    }

    @Test
    @DisplayName("Relation 16: Mutating requests on /internal/** with valid credentials are allowed")
    void mutatingInternalRequestsWithValidCredentialsAreAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/gemini-observer/retire-stuck-worker-now");
        request.setRequestURI("/internal/gemini-observer/retire-stuck-worker-now");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-API-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertTrue(allowed, "Mutating internal operations with valid key must be allowed");
        assertEquals(200, response.getStatus());
    }
}
