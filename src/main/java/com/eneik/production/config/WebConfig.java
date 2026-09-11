package com.eneik.production.config;

import com.eneik.production.security.ApiAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @file WebConfig.java
 * @agent TAG-06 (Deontic Consistency)
 * @description CORS and Endpoint Authorization configuration for Svelte frontend integration.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApiAuthorizationInterceptor apiAuthorizationInterceptor;

    public WebConfig(ApiAuthorizationInterceptor apiAuthorizationInterceptor) {
        this.apiAuthorizationInterceptor = apiAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuthorizationInterceptor)
                .addPathPatterns("/api/ai/resources/**", "/internal/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
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
