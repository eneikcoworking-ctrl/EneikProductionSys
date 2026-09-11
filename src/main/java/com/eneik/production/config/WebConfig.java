package com.eneik.production.config;

import com.eneik.production.security.ApiAuthorizationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * @file WebConfig.java
 * @agent TAG-06 (Deontic Consistency)
 * @description CORS and Endpoint Authorization configuration for Svelte frontend integration.
 *
 * <p>Boundary Topology (BOUNDARY_TOPOLOGY / D006, Law 12):
 * Prohibits wildcard origin patterns when allowCredentials is enabled. Restricts cross-origin access
 * to explicit loopback origins (local operator ports) and any explicitly configured operator origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String[] DEFAULT_ALLOWED_ORIGINS = {
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:8080",
            "http://127.0.0.1:8080"
    };

    private final ApiAuthorizationInterceptor apiAuthorizationInterceptor;
    private final String[] corsAllowedOrigins;

    public WebConfig(
            ApiAuthorizationInterceptor apiAuthorizationInterceptor,
            @Value("${eneik.security.cors.allowed-origins:}") String configuredCorsOrigins) {
        this.apiAuthorizationInterceptor = apiAuthorizationInterceptor;
        if (configuredCorsOrigins != null && !configuredCorsOrigins.isBlank()) {
            this.corsAllowedOrigins = Arrays.stream(configuredCorsOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        } else {
            this.corsAllowedOrigins = DEFAULT_ALLOWED_ORIGINS;
        }
    }

    public String[] getCorsAllowedOrigins() {
        return corsAllowedOrigins.clone();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuthorizationInterceptor)
                .addPathPatterns("/api/**", "/internal/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsAllowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/assets/video/**")
                .addResourceLocations("file:./data/video-assets/");
        registry.addResourceHandler("/api/assets/design/**")
                .addResourceLocations("file:./data/design-assets/");
    }
}
