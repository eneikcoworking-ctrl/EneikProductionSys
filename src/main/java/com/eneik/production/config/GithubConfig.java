package com.eneik.production.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GithubConfig {
    @Value("${github.enabled:false}")
    private boolean enabled;

    @Value("${github.token:}")
    private String token;

    @Value("${github.org}")
    private String organization;

    @Value("${github.api-base-url:https://api.github.com}")
    private String apiBaseUrl;

    @Value("${github.webhook-url:}")
    private String webhookUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public String getToken() {
        return token;
    }

    public String getOrganization() {
        return organization;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }
}
