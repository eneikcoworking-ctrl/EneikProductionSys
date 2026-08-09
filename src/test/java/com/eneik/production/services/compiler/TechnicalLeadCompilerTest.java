package com.eneik.production.services.compiler;

import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.ProjectFileClaimRepository;
import com.eneik.production.repositories.ProjectGenerationStateRepository;
import com.eneik.production.repositories.ProjectHotspotFileRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.BottleneckAwarePriorityService;
import com.eneik.production.services.FeatureService;
import com.eneik.production.services.GeminiContextService;
import com.eneik.production.services.gate.GateOrchestrator;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gricean quantity-optimal grounding (ACP-101): a compiler-generated slice's task description must include
 * only the excerpt of the root client brief relevant to that slice's own role/JTBD, retrieved via RAG - not
 * the whole document (noise) and not a fixed-length truncation (silently cuts off before whatever the slice
 * actually needed, confirmed live on test-forty-third). See buildGroundedOriginalBrief.
 */
class TechnicalLeadCompilerTest {

    private WishlistRepository wishlistRepository;
    private GeminiContextService geminiContextService;
    private TechnicalLeadCompiler compiler;

    private TechnicalLeadCompiler newCompiler() {
        wishlistRepository = mock(WishlistRepository.class);
        geminiContextService = mock(GeminiContextService.class);
        return new TechnicalLeadCompiler(
                wishlistRepository,
                mock(TaskRepository.class),
                mock(ProjectRepository.class),
                mock(RoleRepository.class),
                mock(ProjectGenerationStateRepository.class),
                mock(GateOrchestrator.class),
                mock(BottleneckAwarePriorityService.class),
                new ObjectMapper(),
                mock(ProjectHotspotFileRepository.class),
                mock(FeatureService.class),
                mock(GitHubPullRequestService.class),
                mock(ProjectFileClaimRepository.class),
                geminiContextService);
    }

    @Test
    void slicesWithNoRootLineageUseTheirOwnContentDirectly() {
        compiler = newCompiler();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setContent("A short, never-sliced wishlist's own text.");
        // originWishlistId left null - this row IS the root, nothing to retrieve from.

        String result = compiler.buildGroundedOriginalBrief(wishlist, "BARCAN-TAG-02", "jtbd", "criteria");

        assertThat(result).isEqualTo(wishlist.getContent());
        verify(geminiContextService, never()).retrieveRelevantContext(anyString(), anyInt(), anyString());
    }

    @Test
    void sliceRetrievesOnlyTheRelevantExcerptOfTheRootBriefInsteadOfTheWholeDocument() {
        compiler = newCompiler();
        UUID rootId = UUID.randomUUID();
        WishlistEntity slice = new WishlistEntity();
        slice.setId(UUID.randomUUID());
        slice.setOriginWishlistId(rootId);
        slice.setContent("Internal work item 9 (BARCAN-TAG-08) from wishlist " + rootId + ": Financial data schema");

        when(geminiContextService.retrieveRelevantContext(anyString(), anyInt(), eq("client_brief:" + rootId)))
                .thenReturn(List.of(
                        new GeminiContextService.RetrievedChunk("client_brief:" + rootId,
                                "Budgets, workload distribution and stipend calculation rules for accountants.", 0.9),
                        new GeminiContextService.RetrievedChunk("client_brief:" + rootId,
                                "Roles: accountant, economist, department head - RBAC over financial views.", 0.85)));

        String result = compiler.buildGroundedOriginalBrief(slice, "BARCAN-TAG-08",
                "define explicit models for budgets, workload and stipends", "RBAC on budgets and workload");

        assertThat(result)
                .contains("Budgets, workload distribution and stipend calculation rules")
                .contains("Roles: accountant, economist, department head");
        // The point of the fix: an irrelevant part of a large multi-domain brief (e.g. the design system or
        // core search functionality) must never leak into a financial-module slice's grounding text.
        assertThat(result).doesNotContain("Internal work item");
        verify(wishlistRepository, never()).findById(any());
    }

    @Test
    void degradesToTheRawRootBriefWhenRagReturnsNothingInsteadOfLeavingTheSliceUngrounded() {
        compiler = newCompiler();
        UUID rootId = UUID.randomUUID();
        WishlistEntity slice = new WishlistEntity();
        slice.setId(UUID.randomUUID());
        slice.setOriginWishlistId(rootId);
        slice.setContent("Internal work item 1 (BARCAN-TAG-02) from wishlist " + rootId + ": Core search");

        when(geminiContextService.retrieveRelevantContext(anyString(), anyInt(), startsWith("client_brief:")))
                .thenReturn(List.of());
        WishlistEntity root = new WishlistEntity();
        root.setId(rootId);
        root.setContent("The full original client brief, verbatim.");
        when(wishlistRepository.findById(rootId)).thenReturn(Optional.of(root));

        String result = compiler.buildGroundedOriginalBrief(slice, "BARCAN-TAG-02", "jtbd", "criteria");

        assertThat(result).isEqualTo(root.getContent());
    }

    @Test
    void degradesToTheSlicesOwnContentWhenEvenTheRootRowIsGone() {
        compiler = newCompiler();
        UUID rootId = UUID.randomUUID();
        WishlistEntity slice = new WishlistEntity();
        slice.setId(UUID.randomUUID());
        slice.setOriginWishlistId(rootId);
        slice.setContent("Internal work item 1 (BARCAN-TAG-02) from wishlist " + rootId + ": Core search");

        when(geminiContextService.retrieveRelevantContext(anyString(), anyInt(), startsWith("client_brief:")))
                .thenReturn(List.of());
        when(wishlistRepository.findById(rootId)).thenReturn(Optional.empty());

        String result = compiler.buildGroundedOriginalBrief(slice, "BARCAN-TAG-02", "jtbd", "criteria");

        assertThat(result).isEqualTo(slice.getContent());
    }
}
