package com.eneik.production.controllers;

import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.WishlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WishlistControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID VALID_PROJECT_ID = WishlistService.VALID_PROJECT_ID;

    @BeforeEach
    void setUp() {
        wishlistRepository.deleteAll();
    }

    @Test
    void createWishlist_Client_Success() throws Exception {
        WishlistRequestDto request = new WishlistRequestDto(
                VALID_PROJECT_ID,
                WishlistSource.client,
                null,
                "Client feedback"
        );

        mockMvc.perform(post("/api/wishlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.projectId").value(VALID_PROJECT_ID.toString()))
                .andExpect(jsonPath("$.source").value("client"))
                .andExpect(jsonPath("$.sourceRoleTag").isEmpty())
                .andExpect(jsonPath("$.content").value("Client feedback"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void createWishlist_Role_Success() throws Exception {
        WishlistRequestDto request = new WishlistRequestDto(
                VALID_PROJECT_ID,
                WishlistSource.role,
                "BARCAN-TAG-09",
                "Role recommendation"
        );

        mockMvc.perform(post("/api/wishlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceRoleTag").value("BARCAN-TAG-09"));
    }

    @Test
    void createWishlist_Role_MissingTag_Failure() throws Exception {
        WishlistRequestDto request = new WishlistRequestDto(
                VALID_PROJECT_ID,
                WishlistSource.role,
                null,
                "Role recommendation"
        );

        mockMvc.perform(post("/api/wishlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWishlist_Client_WithTag_Failure() throws Exception {
        WishlistRequestDto request = new WishlistRequestDto(
                VALID_PROJECT_ID,
                WishlistSource.client,
                "BARCAN-TAG-09",
                "Client feedback"
        );

        mockMvc.perform(post("/api/wishlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWishlist_NonExistentProject_Failure() throws Exception {
        UUID nonExistentProjectId = UUID.randomUUID();
        WishlistRequestDto request = new WishlistRequestDto(
                nonExistentProjectId,
                WishlistSource.client,
                null,
                "Feedback for non-existent project"
        );

        mockMvc.perform(post("/api/wishlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listByProject_FilterStatus() throws Exception {
        UUID projectId = VALID_PROJECT_ID;

        WishlistEntity item1 = new WishlistEntity();
        item1.setProjectId(projectId);
        item1.setSource(WishlistSource.client);
        item1.setContent("Content 1");
        item1.setStatus(WishlistStatus.pending);
        wishlistRepository.save(item1);

        WishlistEntity item2 = new WishlistEntity();
        item2.setProjectId(projectId);
        item2.setSource(WishlistSource.client);
        item2.setContent("Content 2");
        item2.setStatus(WishlistStatus.dismissed);
        wishlistRepository.save(item2);

        // List all
        mockMvc.perform(get("/api/wishlist")
                .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Filter by pending
        mockMvc.perform(get("/api/wishlist")
                .param("projectId", projectId.toString())
                .param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("Content 1"));
    }

    @Test
    void dismissWishlist_Success() throws Exception {
        WishlistEntity entity = new WishlistEntity();
        entity.setProjectId(VALID_PROJECT_ID);
        entity.setSource(WishlistSource.client);
        entity.setContent("To be dismissed");
        entity.setStatus(WishlistStatus.pending);
        entity = wishlistRepository.save(entity);

        mockMvc.perform(patch("/api/wishlist/" + entity.getId() + "/dismiss"))
                .andExpect(status().isOk());

        WishlistEntity updated = wishlistRepository.findById(entity.getId()).get();
        assert updated.getStatus() == WishlistStatus.dismissed;
    }
}
