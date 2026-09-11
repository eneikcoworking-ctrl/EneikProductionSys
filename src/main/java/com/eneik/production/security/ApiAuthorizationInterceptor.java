package com.eneik.production.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Endpoint Authorization Interceptor implementing Deontic Prohibitions as Code
 * (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE, DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX / D006 Authorization ambiguity).
 *
 * <p>Enforces the Rights/Duties Matrix across two sensitive scopes:
 * 1. Mutating AI resource operations (/api/ai/resources/** with POST/PUT/PATCH/DELETE) - protects against
 *    unauthorized resource consumption and destructive operations (deleting design drafts, model probing,
 *    expensive AI generation).
 * 2. Internal administrative endpoints (/internal/**) - enforces localhost/loopback isolation or valid
 *    operator authorization.
 */
@Component
public class ApiAuthorizationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiAuthorizationInterceptor.class);

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1",
            "localhost"
    );


    private static final Set<String> MUTATING_METHODS = Set.of(
            "POST", "PUT", "PATCH", "DELETE"
    );

    private final String configuredApiKey;
    private final ObjectMapper objectMapper;

    public ApiAuthorizationInterceptor(
            @Value("${eneik.security.api-key:}") String configuredApiKey,
            ObjectMapper objectMapper) {
        this.configuredApiKey = configuredApiKey != null ? configuredApiKey.trim() : "";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod() != null ? request.getMethod().toUpperCase(Locale.ROOT) : "GET";

        // Scope 1: Internal endpoints (/internal/**)
        if (path != null && path.startsWith("/internal/")) {
            return checkInternalAccess(request, response, path);
        }

        // Scope 2: Mutating AI resource operations (/api/ai/resources/**)
        if (path != null && path.startsWith("/api/ai/resources") && MUTATING_METHODS.contains(method)) {
            return checkMutatingAiResourceAccess(request, response, path, method);
        }

        // Safe reads and non-protected paths are allowed
        return true;
    }

    private boolean checkInternalAccess(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
        String remoteAddr = request.getRemoteAddr();
        boolean isLocal = isLoopback(remoteAddr);

        if (isLocal) {
            return true;
        }

        // Non-loopback request to /internal/** requires valid operator authorization
        String token = extractToken(request);
        if (token != null && isTokenValid(token)) {
            return true;
        }

        log.warn("[SECURITY][DENIAL] Forbidden access to internal path '{}' from non-localhost IP '{}'", path, remoteAddr);
        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "Access denied: internal endpoints are restricted to localhost or authorized operator");
        return false;
    }

    private boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        return LOOPBACK_ADDRESSES.contains(remoteAddr) || remoteAddr.startsWith("127.");
    }

    private boolean checkMutatingAiResourceAccess(HttpServletRequest request, HttpServletResponse response, String path, String method) throws IOException {
        if (configuredApiKey.isBlank()) {
            log.warn("[SECURITY][DENIAL] Mutating AI operation {} '{}' rejected: server API key is not configured in environment", method, path);
            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "Access denied: server API key is not configured; mutating AI resource operations are disabled");
            return false;
        }

        String token = extractToken(request);

        if (token == null || token.isBlank()) {
            log.warn("[SECURITY][DENIAL] Unauthorized attempt to perform mutating AI operation {} '{}' without credentials", method, path);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                    "Authorization required: missing X-API-Key or Authorization Bearer header for mutating AI resource operations");
            return false;
        }

        if (!isTokenValid(token)) {
            log.warn("[SECURITY][DENIAL] Forbidden attempt to perform mutating AI operation {} '{}' with invalid key", method, path);
            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "Access denied: invalid authorization credentials");
            return false;
        }

        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.trim();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            String trimmed = authHeader.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }

        return null;
    }

    private boolean isTokenValid(String providedToken) {
        if (configuredApiKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredApiKey.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> errorBody = Map.of(
                "error", message,
                "code", code,
                "status", status
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        response.getWriter().flush();
    }
}
