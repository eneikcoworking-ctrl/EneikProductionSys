package com.eneik.production.controllers.roles;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.services.RoleCapabilityLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RoleRulesControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleCapabilityLoader roleCapabilityLoader;

    @Test
    void getRules_ReturnsRules() throws Exception {
        String tag = "BARCAN-TAG-03";
        RoleRules rules = new RoleRules(tag, "UI Design", List.of("No DB access"), "Figma link", "TAG-00", "Refusal", "Deontic");

        when(roleCapabilityLoader.loadRules(tag)).thenReturn(rules);

        mockMvc.perform(get("/api/roles/" + tag + "/rules"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tag").value(tag))
                .andExpect(jsonPath("$.scope").value("UI Design"))
                .andExpect(jsonPath("$.forbidden[0]").value("No DB access"));
    }
}
