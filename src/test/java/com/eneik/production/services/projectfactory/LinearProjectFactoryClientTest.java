package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Closes F8, and pins the reason rather than the message.
 *
 * Every projectCreate on test-forty-fifth and test-forty-sixth failed with Linear's generic
 * "Argument Validation Error". The live setting was {@code linear_team_id = "Eneik Production System"} - a
 * team NAME where the API requires a UUID. A configuration mistake was arriving as an opaque remote error,
 * so it repeated on every project while looking like a transient integration fault.
 *
 * The check has to be local: a remote rejection cannot name a local cause.
 */
class LinearProjectFactoryClientTest {

    private final SystemSettingsService settings = mock(SystemSettingsService.class);

    /**
     * Pointed at a closed local port, never at Linear. One of these cases deliberately gets PAST the guard,
     * and a test that then reached the real api.linear.app would send a stranger's service a request every
     * time anyone ran the suite. The connection is refused at once, so the assertion is fast and offline.
     */
    private final LinearProjectFactoryClient client =
            new LinearProjectFactoryClient("http://127.0.0.1:1/graphql", settings, new ObjectMapper());

    private ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("test-forty-seventh");
        return project;
    }

    private void configured(String teamId) {
        when(settings.effectiveBoolean("linear_enabled")).thenReturn(true);
        when(settings.effectiveValue("linear_api_key")).thenReturn("lin_api_key");
        when(settings.effectiveValue("linear_team_id")).thenReturn(teamId);
    }

    @Test
    void aTeamNameIsRefusedBeforeTheApiIsCalledAtAll() {
        configured("Eneik Production System");

        var result = client.provision(project(), "https://github.com/x/y");

        assertThat(result.status())
                .as("the exact live misconfiguration, named in the message so it can be fixed in one "
                        + "action instead of being re-diagnosed every project")
                .contains("Eneik Production System")
                .contains("not a Linear team id")
                .contains("UUID");
        assertThat(result.projectId()).isNull();
    }

    @Test
    void aTeamKeyIsRefusedToo() {
        configured("ENG");

        assertThat(client.provision(project(), "https://github.com/x/y").status())
                .as("a short team key is the other shape people paste in, and it fails identically at the "
                        + "API with the same uninformative error")
                .contains("not a Linear team id");
    }

    @Test
    void aRealUuidGetsPastTheCheck() {
        configured("b2c3d4e5-6789-4abc-8def-0123456789ab");

        assertThat(client.provision(project(), "https://github.com/x/y").status())
                .as("the guard must not become the new reason provisioning never happens - a well-formed "
                        + "id proceeds to the call, and whatever happens there is a different question")
                .doesNotContain("not a Linear team id");
    }

    @Test
    void theGuardNeverFiresWhenLinearIsSwitchedOff() {
        when(settings.effectiveBoolean("linear_enabled")).thenReturn(false);

        assertThat(client.provision(project(), "https://github.com/x/y").status())
                .as("an operator who has deliberately turned Linear off must not be told their team id is "
                        + "wrong - that is noise about a decision they already made")
                .startsWith("skipped: Linear provisioning disabled");
    }
}
