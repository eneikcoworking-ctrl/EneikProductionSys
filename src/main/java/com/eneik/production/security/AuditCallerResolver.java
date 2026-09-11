package com.eneik.production.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility for resolving caller context during institutional fact recording
 * (Prescription 21, INSTITUTIONAL_FACT_REGISTER / D007).
 * Identifies whether the request comes from the operator key bearer or internal system,
 * including client IP address, avoiding fictional identities.
 */
public final class AuditCallerResolver {

    private AuditCallerResolver() {}

    public static String resolveCaller() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "не установлено (internal/system)";
            }
            HttpServletRequest request = attrs.getRequest();
            return resolveCaller(request);
        } catch (Exception e) {
            return "не установлено";
        }
    }

    public static String resolveCaller(HttpServletRequest request) {
        if (request == null) {
            return "не установлено (internal/system)";
        }
        String remoteAddr = request.getRemoteAddr();
        String ipStr = (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "не установлен";

        boolean hasKey = (request.getHeader("X-API-Key") != null && !request.getHeader("X-API-Key").isBlank())
                || (request.getHeader("Authorization") != null && !request.getHeader("Authorization").isBlank());

        String callerType = hasKey ? "носитель ключа оператора" : "анонимный запрос";
        return String.format("%s (IP: %s)", callerType, ipStr);
    }
}
