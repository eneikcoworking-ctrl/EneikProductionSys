package com.eneik.production.controllers.settings;

import com.eneik.production.dto.settings.SettingDto;
import com.eneik.production.dto.settings.SettingUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SettingsControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanSettings() {
        jdbcTemplate.update("DELETE FROM system_settings");
    }

    @Test
    void savesTokenAndReturnsOnlyMaskedValue() {
        ResponseEntity<SettingDto> saveResponse = restTemplate.exchange(
                "/api/settings",
                HttpMethod.PUT,
                new HttpEntity<>(new SettingUpdateRequest("github_token", "ghp_testtoken_a1b2")),
                SettingDto.class
        );

        assertThat(saveResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(saveResponse.getBody()).isNotNull();
        assertThat(saveResponse.getBody().maskedValue()).isEqualTo("****a1b2");
        assertThat(saveResponse.getBody().source()).isEqualTo("database");

        String stored = jdbcTemplate.queryForObject(
                "SELECT \"value\" FROM system_settings WHERE \"key\" = 'github_token'",
                String.class
        );
        assertThat(stored).isEqualTo("ghp_testtoken_a1b2");

        ResponseEntity<SettingDto[]> listResponse = restTemplate.getForEntity("/api/settings", SettingDto[].class);
        SettingDto githubToken = Arrays.stream(listResponse.getBody())
                .filter(setting -> "github_token".equals(setting.key()))
                .findFirst()
                .orElseThrow();
        assertThat(githubToken.maskedValue()).isEqualTo("****a1b2");
        assertThat(githubToken.toString()).doesNotContain("ghp_testtoken_a1b2");
    }

    @Test
    void corsPreflightAllowsSettingsPutFromFrontend() throws Exception {
        mockMvc.perform(options("/api/settings")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PUT")));
    }

    @Test
    void internalResolveReturnsRawValueForLocalScripts() {
        restTemplate.exchange(
                "/api/settings",
                HttpMethod.PUT,
                new HttpEntity<>(new SettingUpdateRequest("linear_api_key", "lin_test_secret")),
                SettingDto.class
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/internal/settings/resolve",
                Map.of("key", "linear_api_key"),
                Map.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("value", "lin_test_secret");
        assertThat(response.getBody()).containsEntry("source", "database");
    }
}
