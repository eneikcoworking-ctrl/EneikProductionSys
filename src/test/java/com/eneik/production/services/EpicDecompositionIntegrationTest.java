package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ф8 (2026-07-21, operator directive): a wishlist splits into as many эпики (epics) as the product needs,
 * each carrying its own content (JTBD/Kano/Cynefin), and every compile cycle must decide per эпик whether
 * it matches an existing one or is genuinely new. Exercises ProjectFlowService.buildTaskGraphFromSlices
 * directly against real repositories (not mocked) so the actual persistence/graph-scoping behavior is
 * proven, not just that the code compiles.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EpicDecompositionIntegrationTest {

    @Autowired
    private ProjectFlowService projectFlowService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private FeatureRepository featureRepository;

    private ProjectEntity createProject() {
        ProjectEntity project = new ProjectEntity();
        String suffix = UUID.randomUUID().toString();
        project.setName("Epic Decomposition Test " + suffix);
        project.setSlug("epic-decomposition-test-" + suffix);
        project.setRepositoryName("epic-decomposition-test-repo-" + suffix);
        project.setRepoUrl("https://github.com/eneikcoworking-ctrl/epic-decomposition-test-" + suffix);
        return projectRepository.saveAndFlush(project);
    }

    private WishlistEntity createClientWishlist(UUID projectId) {
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(projectId);
        wishlist.setSource(WishlistSource.client);
        wishlist.setContent("Notes CRUD and user profile settings");
        wishlist.setStatus(WishlistStatus.compiling);
        return wishlistRepository.saveAndFlush(wishlist);
    }

    private MLPredictionServiceClient.TaskSliceMetadata slice(String roleTag) {
        return new MLPredictionServiceClient.TaskSliceMetadata(
                "Slice for " + roleTag,
                "When implementing this for the epic, I want the smallest real change, so the epic works.",
                "Given a valid request, When it is sent, Then the expected result occurs.",
                roleTag,
                LeanValue.essential,
                "clear",
                "TOC-CONSTRAINT-DECOMPOSITION",
                "Escaped defects <= 5%",
                false
        );
    }

    @Test
    void oneWishlistCanProduceMultipleDistinctEpics() {
        ProjectEntity project = createProject();
        WishlistEntity wishlist = createClientWishlist(project.getId());

        MLPredictionServiceClient.EpicPlan notesEpic = new MLPredictionServiceClient.EpicPlan(
                null, "Notes CRUD", "When a user manages notes, I want CRUD, so I can track information.",
                "Must-Be", "complicated", "Reduce time-to-first-note from 30s to 5s", "Core CRUD throughput",
                0, List.of(slice("BARCAN-TAG-08"))
        );
        MLPredictionServiceClient.EpicPlan profileEpic = new MLPredictionServiceClient.EpicPlan(
                null, "User Profile Settings", "When a user wants control, I want to edit their profile, so they feel in control.",
                "Performance", "clear", "Reduce profile update failures from 10% to 0%", "Profile update throughput",
                0, List.of(slice("BARCAN-TAG-02"))
        );

        boolean built = projectFlowService.buildTaskGraphFromSlices(project, List.of(wishlist), List.of(notesEpic, profileEpic));

        assertThat(built).isTrue();
        List<FeatureEntity> epics = featureRepository.findByProjectId(project.getId());
        assertThat(epics).hasSize(2);
        assertThat(epics).extracting(FeatureEntity::getTitle)
                .containsExactlyInAnyOrder("Notes CRUD", "User Profile Settings");
        assertThat(epics).extracting(FeatureEntity::getKanoClass)
                .containsExactlyInAnyOrder("Must-Be", "Performance");

        // Client-sourced wishlists also trigger a coverage-audit task (BARCAN-TAG-09) per эпик alongside
        // the real work - see ProjectFlowService.dispatchCoverageAuditIfClientBrief - filter to the roles
        // this test's slices actually used, matching the convention ProjectFlowIntegrationTest already
        // established for the same reason.
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(t -> t.getRole() != null
                        && ("BARCAN-TAG-08".equals(t.getRole().getTag()) || "BARCAN-TAG-02".equals(t.getRole().getTag())))
                .toList();
        assertThat(tasks).hasSize(2);
        // Each эпик is its own dependency graph - with one slice each here, neither task should depend on
        // the other (they belong to different эпики even though they came from the same wishlist).
        assertThat(tasks).allMatch(t -> t.getDependsOn() == null);
        // Every task's featureId must point at ONE of the two distinct эпики, never mixed up.
        assertThat(tasks).extracting(TaskEntity::getFeatureId)
                .containsExactlyInAnyOrderElementsOf(epics.stream().map(FeatureEntity::getId).toList());

        assertThat(wishlistRepository.findById(wishlist.getId()).orElseThrow().getStatus())
                .isEqualTo(WishlistStatus.converted_to_task);
    }

    @Test
    void secondCompileCycleReusesMatchedExistingEpicInsteadOfDuplicating() {
        ProjectEntity project = createProject();
        WishlistEntity firstWishlist = createClientWishlist(project.getId());

        MLPredictionServiceClient.EpicPlan newEpic = new MLPredictionServiceClient.EpicPlan(
                null, "Notes CRUD", "When a user manages notes, I want CRUD, so I can track information.",
                "Must-Be", "complicated", "Reduce time-to-first-note from 30s to 5s", "Core CRUD throughput",
                0, List.of(slice("BARCAN-TAG-08"))
        );
        projectFlowService.buildTaskGraphFromSlices(project, List.of(firstWishlist), List.of(newEpic));
        List<FeatureEntity> afterFirst = featureRepository.findByProjectId(project.getId());
        assertThat(afterFirst).hasSize(1);
        UUID existingEpicId = afterFirst.get(0).getId();

        WishlistEntity secondWishlist = createClientWishlist(project.getId());
        MLPredictionServiceClient.EpicPlan matchedEpic = new MLPredictionServiceClient.EpicPlan(
                existingEpicId.toString(), null, null, null, null, null, null,
                0, List.of(slice("BARCAN-TAG-11"))
        );
        boolean built = projectFlowService.buildTaskGraphFromSlices(project, List.of(secondWishlist), List.of(matchedEpic));

        assertThat(built).isTrue();
        List<FeatureEntity> afterSecond = featureRepository.findByProjectId(project.getId());
        assertThat(afterSecond).hasSize(1).extracting(FeatureEntity::getId).containsExactly(existingEpicId);

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(t -> t.getRole() != null
                        && ("BARCAN-TAG-08".equals(t.getRole().getTag()) || "BARCAN-TAG-11".equals(t.getRole().getTag())))
                .toList();
        assertThat(tasks).hasSize(2);
        assertThat(tasks).allMatch(t -> existingEpicId.equals(t.getFeatureId()));
    }

    @Test
    void hallucinatedExistingEpicIdFallsBackToCreatingNewRatherThanFailing() {
        ProjectEntity project = createProject();
        WishlistEntity wishlist = createClientWishlist(project.getId());

        MLPredictionServiceClient.EpicPlan bogusMatch = new MLPredictionServiceClient.EpicPlan(
                UUID.randomUUID().toString(), "Fallback Epic", "When..., I want..., so that...",
                "Must-Be", "clear", "metric", "toc", 0, List.of(slice("BARCAN-TAG-08"))
        );

        boolean built = projectFlowService.buildTaskGraphFromSlices(project, List.of(wishlist), List.of(bogusMatch));

        assertThat(built).isTrue();
        List<FeatureEntity> epics = featureRepository.findByProjectId(project.getId());
        assertThat(epics).hasSize(1);
        assertThat(epics.get(0).getTitle()).isEqualTo("Fallback Epic");
    }
}
