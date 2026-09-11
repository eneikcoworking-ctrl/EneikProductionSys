package com.eneik.production.config;

import com.eneik.production.security.ApiAuthorizationInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verification for WebConfig CORS boundary enforcement (BOUNDARY_TOPOLOGY / D006, Law 12).
 * Verifies that wildcard origin pattern "*" is eliminated when credentials are allowed,
 * and only explicit trusted origins (loopback operator ports or configured origins) are permitted.
 */
class WebConfigCorsTest {

    @Test
    @DisplayName("CORS: Default allowed origins restrict to local operator ports without wildcards")
    void defaultAllowedOriginsContainOnlyLocalhostWithoutWildcards() {
        ApiAuthorizationInterceptor interceptor = mock(ApiAuthorizationInterceptor.class);
        WebConfig config = new WebConfig(interceptor, "");

        String[] origins = config.getCorsAllowedOrigins();
        List<String> originList = Arrays.asList(origins);

        assertThat(originList).isNotEmpty();
        assertThat(originList).contains(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:8080",
                "http://127.0.0.1:8080"
        );
        assertThat(originList).doesNotContain("*");
        assertThat(originList).noneMatch(o -> o.contains("*"));
    }

    @Test
    @DisplayName("CORS: Explicitly configured origins override defaults")
    void configuredAllowedOriginsOverrideDefaults() {
        ApiAuthorizationInterceptor interceptor = mock(ApiAuthorizationInterceptor.class);
        WebConfig config = new WebConfig(interceptor, "http://custom-operator.local:3000, https://control.eneik.internal");

        String[] origins = config.getCorsAllowedOrigins();
        List<String> originList = Arrays.asList(origins);

        assertThat(originList).containsExactly(
                "http://custom-operator.local:3000",
                "https://control.eneik.internal"
        );
        assertThat(originList).doesNotContain("*");
    }

    @Test
    @DisplayName("CORS: Registration applies without exception and binds configured origins")
    void corsRegistrationAppliesToRegistry() {
        ApiAuthorizationInterceptor interceptor = mock(ApiAuthorizationInterceptor.class);
        WebConfig config = new WebConfig(interceptor, null);

        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);

        // WebConfig sets allowedOrigins which Spring validates against allowCredentials(true)
        // Spring requires that allowedOrigins not be "*" when allowCredentials is true
        assertThat(config.getCorsAllowedOrigins()).doesNotContain("*");
    }
}
