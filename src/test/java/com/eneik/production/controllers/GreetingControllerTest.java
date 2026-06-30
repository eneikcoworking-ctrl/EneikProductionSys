package com.eneik.production.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GreetingController.class)
public class GreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnLatestGreeting() throws Exception {
        mockMvc.perform(get("/api/v1/greetings/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Welcome to EneikProductionSys"));
    }

    @Test
    public void shouldMaskEmailInGreeting() throws Exception {
        String json = "{\"message\": \"Contact me at test@example.com\"}";
        mockMvc.perform(post("/api/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Contact me at [REDACTED]"));
    }

    @Test
    public void shouldBlockCreditCard() throws Exception {
        String json = "{\"message\": \"My card is 1234-5678-1234-5678\"}";
        mockMvc.perform(post("/api/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Compliance Violation"));
    }
}
