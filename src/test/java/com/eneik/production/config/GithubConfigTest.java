package com.eneik.production.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification for GithubConfig centralized credentials and properties (RIGID_API_REFERENT / D001).
 */
class GithubConfigTest {

    @Test
    @DisplayName("GithubConfig: getters reflect injected values correctly")
    void testGithubConfigProperties() {
        GithubConfig config = new GithubConfig();
        ReflectionTestUtils.setField(config, "enabled", true);
        ReflectionTestUtils.setField(config, "token", "ghp_secretToken123");
        ReflectionTestUtils.setField(config, "organization", "eneik-production");
        ReflectionTestUtils.setField(config, "apiBaseUrl", "https://api.github.com");
        ReflectionTestUtils.setField(config, "webhookUrl", "https://factory.internal/webhook");
        ReflectionTestUtils.setField(config, "webhookSecret", "secret456");

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getToken()).isEqualTo("ghp_secretToken123");
        assertThat(config.getOrganization()).isEqualTo("eneik-production");
        assertThat(config.getApiBaseUrl()).isEqualTo("https://api.github.com");
        assertThat(config.getWebhookUrl()).isEqualTo("https://factory.internal/webhook");
        assertThat(config.getWebhookSecret()).isEqualTo("secret456");
    }
}
