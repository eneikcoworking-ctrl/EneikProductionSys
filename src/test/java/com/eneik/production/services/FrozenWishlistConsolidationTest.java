package com.eneik.production.services;

import com.eneik.production.dto.WishlistRequestDto;
import com.eneik.production.dto.WishlistResponseDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Popperian falsification harness for WO #14 (Mechanism #16):
 * Accumulation and consolidation of wishlists for frozen projects with full lineage and Belnap BOTH conflict preservation.
 *
 * Grounded in:
 * - ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010): non-destructive lineage via merged_into_wishlist_id & consolidated_from_ids.
 * - NUEL_BELNAP_03_TRUTH_STATUS_TABLE (D012/D002): explicit deferred_frozen / consolidated statuses & BOTH conflict flagging.
 * - KARL_POPPER_01_FALSIFICATION_HARNESS (D008): barriers 14-1..14-5 over real H2 + Flyway V1..V148.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FrozenWishlistConsolidationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectFlowService projectFlowService;

    @Autowired
    private FrozenWishlistConsolidationService consolidationService;

    @Autowired
    private OperationalPolicyService operationalPolicyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private ProjectEntity createFrozenProject(String slugSuffix) {
        String slug = "frozen-test-" + slugSuffix + "-" + UUID.randomUUID().toString().substring(0, 6);
        ProjectEntity project = new ProjectEntity();
        project.setName("frozen-test-" + slugSuffix);
        project.setSlug(slug);
        project.setRepositoryName(slug);
        project.setRepositoryUrl("https://github.com/eneik-test/" + slug);
        project.setRepoUrl("https://github.com/eneik-test/" + slug);
        project.setStatus(ProjectStatus.frozen);
        return projectRepository.save(project);
    }

    // --- Barrier 14-1: Frozen Accumulation, No Tasks ---

    @Test
    @DisplayName("Barrier 14-1: addWishlistItem on frozen project saves deferred_frozen with 0 tasks while ORCHESTRATE/DISPATCH remain forbidden")
    void frozenProjectAccumulatesWishlistsWithoutTasksAndKeepsExecutionBlocked() {
        ProjectEntity frozen = createFrozenProject("accum");

        assertFalse(ProjectStatus.frozen.isOperational(),
                "ProjectStatus.frozen.isOperational() must remain false (Dennett autonomy invariant)");
        assertTrue(operationalPolicyService.authorize(frozen.getId(), OperationalAction.ADD_WISHLIST).allowed(),
                "ADD_WISHLIST must be permitted on frozen projects");
        assertFalse(operationalPolicyService.authorize(frozen.getId(), OperationalAction.ORCHESTRATE).allowed(),
                "ORCHESTRATE must remain forbidden on frozen projects");
        assertFalse(operationalPolicyService.authorize(frozen.getId(), OperationalAction.DISPATCH_QUEUED_TASKS).allowed(),
                "DISPATCH_QUEUED_TASKS must remain forbidden on frozen projects");
        assertFalse(operationalPolicyService.authorize(frozen.getId(), OperationalAction.MERGE_PR).allowed(),
                "MERGE_PR must remain forbidden on frozen projects");
        assertFalse(operationalPolicyService.authorize(frozen.getId(), OperationalAction.RECOVER_FAILED_FRONTIER).allowed(),
                "RECOVER_FAILED_FRONTIER must remain forbidden on frozen projects");

        WishlistResponseDto clientWish = projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null, "Добавить экспорт отчётов в PDF по ГОСТ")
        );
        WishlistResponseDto roleWish = projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.role, "BARCAN-TAG-06", "Проверить покрытие unit-тестами экспорт PDF")
        );

        assertEquals(WishlistStatus.deferred_frozen, clientWish.status());
        assertEquals(WishlistStatus.deferred_frozen, roleWish.status());
        assertNotNull(clientWish.createdAt());
        assertNotNull(roleWish.createdAt());
        assertEquals("BARCAN-TAG-06", roleWish.sourceRoleTag());

        WishlistEntity savedClient = wishlistRepository.findById(clientWish.id()).orElseThrow();
        assertFalse(savedClient.movable(), "deferred_frozen wishlist must not be movable into decomposition");
        assertEquals("Добавить экспорт отчётов в PDF по ГОСТ", savedClient.getContent());
        assertEquals(0, taskRepository.findByProjectIdOrderByCreatedAtDesc(frozen.getId()).size(),
                "Accumulating wishlists on a frozen project must create 0 tasks");
    }

    // --- Barrier 14-2: Semantic Grouping + Lineage ---

    @Test
    @DisplayName("Barrier 14-2: unfreezing groups similar deferred_frozen wishes into 1 pending brief with full lineage and marks originals consolidated")
    void unfreezeConsolidatesSimilarWishesWithFullLineage() throws Exception {
        ProjectEntity frozen = createFrozenProject("group");

        WishlistResponseDto w1 = projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null,
                        "Добавить фильтр по дате и статусу в таблицу заказов")
        );
        WishlistResponseDto w2 = projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null,
                        "Добавить сортировку и фильтр по дате в таблицу заказов")
        );
        WishlistResponseDto w3 = projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null,
                        "Перевести уведомления электронной почты на SMTP релей с DKIM")
        );

        projectFlowService.activateProject(frozen.getId());

        List<WishlistEntity> all = wishlistRepository.findByProjectId(frozen.getId());
        List<WishlistEntity> pending = all.stream().filter(w -> w.getStatus() == WishlistStatus.pending).toList();
        List<WishlistEntity> consolidatedOriginals = all.stream().filter(w -> w.getStatus() == WishlistStatus.consolidated).toList();

        assertEquals(2, pending.size(), "Two similar wishes should merge into 1 group, plus 1 distinct group = 2 pending briefs");
        assertEquals(3, consolidatedOriginals.size(), "All 3 original wishes must remain in DB with status=consolidated");

        WishlistEntity mergedOrderBrief = pending.stream()
                .filter(w -> w.getContent().contains(w1.id().toString()) && w.getContent().contains(w2.id().toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected 1 consolidated brief referencing both similar wishlists"));

        List<String> fromIds = objectMapper.readValue(mergedOrderBrief.getConsolidatedFromIds(), new TypeReference<>() {});
        assertEquals(2, fromIds.size());
        assertTrue(fromIds.contains(w1.id().toString()));
        assertTrue(fromIds.contains(w2.id().toString()));
        assertFalse(mergedOrderBrief.isConsolidationConflict());
        assertEquals(WishlistSource.client, mergedOrderBrief.getSource());
        assertNull(mergedOrderBrief.getSourceRoleTag());

        WishlistEntity orig1 = wishlistRepository.findById(w1.id()).orElseThrow();
        WishlistEntity orig2 = wishlistRepository.findById(w2.id()).orElseThrow();
        WishlistEntity orig3 = wishlistRepository.findById(w3.id()).orElseThrow();

        assertEquals(WishlistStatus.consolidated, orig1.getStatus());
        assertEquals(mergedOrderBrief.getId(), orig1.getMergedIntoWishlistId());
        assertFalse(orig1.movable(), "consolidated wishlist must not be movable");
        assertEquals("Добавить фильтр по дате и статусу в таблицу заказов", orig1.getContent(),
                "Original wishlist content must never be overwritten");

        assertEquals(WishlistStatus.consolidated, orig2.getStatus());
        assertEquals(mergedOrderBrief.getId(), orig2.getMergedIntoWishlistId());

        assertEquals(WishlistStatus.consolidated, orig3.getStatus());
        assertNotNull(orig3.getMergedIntoWishlistId());
        assertNotEquals(mergedOrderBrief.getId(), orig3.getMergedIntoWishlistId());
    }

    // --- Barrier 14-3: Idempotency ---

    @Test
    @DisplayName("Barrier 14-3: repeated freeze/unfreeze and repeated consolidateForProject calls produce zero duplicates")
    void consolidationIsStrictlyIdempotentAcrossRepeatedUnfreezes() {
        ProjectEntity frozen = createFrozenProject("idemp");

        projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null, "Ускорить поиск по каталогу товаров через индекс")
        );
        projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null, "Оптимизировать поиск по каталогу товаров и добавить индекс")
        );

        // First unfreeze -> consolidates 2 -> 1
        projectFlowService.activateProject(frozen.getId());
        long pendingAfterFirst = wishlistRepository.countByProjectIdAndStatus(frozen.getId(), WishlistStatus.pending);
        assertEquals(1, pendingAfterFirst);

        // Direct repeat call -> strict no-op
        FrozenWishlistConsolidationService.ConsolidationReport repeatReport =
                consolidationService.consolidateForProject(frozen.getId());
        assertEquals(0, repeatReport.candidateCount());
        assertEquals(0, repeatReport.consolidatedGroupCount());
        assertEquals(1, wishlistRepository.countByProjectIdAndStatus(frozen.getId(), WishlistStatus.pending));

        // Freeze again and unfreeze again -> still strict no-op (count == 1)
        projectFlowService.pauseProject(frozen.getId());
        projectFlowService.activateProject(frozen.getId());
        assertEquals(1, wishlistRepository.countByProjectIdAndStatus(frozen.getId(), WishlistStatus.pending),
                "Repeated freeze/unfreeze must never duplicate consolidated wishlists");
    }

    // --- Barrier 14-4: Conflict Preservation (Belnap BOTH) ---

    @Test
    @DisplayName("Barrier 14-4: contradictory wishes are flagged with Belnap BOTH consolidationConflict=true and preserve both sides & IDs")
    void contradictoryWishesAreNeverSilentlyMergedAndCarryBelnapConflictMarker() {
        ProjectEntity frozen = createFrozenProject("conflict");

        WishlistResponseDto enableWish = projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null, "Включить кэширование Redis для API")
        );
        WishlistResponseDto disableWish = projectFlowService.addWishlistItem(
                frozen.getId(),
                new WishlistRequestDto(frozen.getId(), WishlistSource.client, null, "Полностью отключить и удалить кэширование Redis для API")
        );

        projectFlowService.activateProject(frozen.getId());

        List<WishlistEntity> pending = wishlistRepository.findByProjectIdAndStatus(frozen.getId(), WishlistStatus.pending);
        assertEquals(1, pending.size(), "Contradictory directives on the same subject must be grouped and flagged");

        WishlistEntity consolidated = pending.get(0);
        assertTrue(consolidated.isConsolidationConflict(),
                "Belnap BOTH conflict flag must be true for polar contradictory directives");
        assertNotNull(consolidated.getConsolidationNotes());
        assertTrue(consolidated.getConsolidationNotes().contains("Belnap BOTH"));

        String content = consolidated.getContent();
        assertTrue(content.contains("[CONFLICT DETECTED — REQUIRES RESOLUTION]"),
                "Consolidated content must contain explicit conflict header");
        assertTrue(content.contains(enableWish.id().toString()), "Must preserve Side A UUID");
        assertTrue(content.contains(disableWish.id().toString()), "Must preserve Side B UUID");
        assertTrue(content.contains("Включить кэширование Redis для API"), "Must preserve Side A position");
        assertTrue(content.contains("Полностью отключить и удалить кэширование Redis для API"), "Must preserve Side B position");

        WishlistEntity origA = wishlistRepository.findById(enableWish.id()).orElseThrow();
        WishlistEntity origB = wishlistRepository.findById(disableWish.id()).orElseThrow();
        assertEquals(WishlistStatus.consolidated, origA.getStatus());
        assertEquals(WishlistStatus.consolidated, origB.getStatus());
        assertEquals(consolidated.getId(), origA.getMergedIntoWishlistId());
        assertEquals(consolidated.getId(), origB.getMergedIntoWishlistId());
    }

    // --- Barrier 14-5: H2 + Flyway V1..V148 & Pre-existing pending Pickup on Frozen Project ---

    @Test
    @DisplayName("Barrier 14-5: Flyway V148 schema is applied in H2 and pre-existing pending wishlists on a frozen project are consolidated on unfreeze")
    void flywayV148SchemaAndLegacyPendingWishlistsPickupOnUnfreeze() throws Exception {
        // Verify V148 applied in Flyway history and columns exist on wishlist table
        Integer v148Applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '148' AND \"success\" = TRUE",
                Integer.class);
        assertEquals(1, v148Applied, "Flyway migration V148 must be applied cleanly on H2");

        ProjectEntity frozen = createFrozenProject("crie-sim");

        // Seed 3 legacy pending wishlists mimicking knowledge-base-crie (caa495f4, e41fc341, 62f73c58)
        WishlistEntity launchability = new WishlistEntity();
        launchability.setProjectId(frozen.getId());
        launchability.setSource(WishlistSource.product_not_launchable);
        launchability.setSourceRoleTag("BARCAN-TAG-00");
        launchability.setStatus(WishlistStatus.pending);
        launchability.setContent("Product container healthcheck failed on port 8080 during runtime launch probe");
        launchability.setCreatedAt(Instant.parse("2026-11-13T10:00:00Z"));
        launchability = wishlistRepository.save(launchability);

        WishlistEntity parity1 = new WishlistEntity();
        parity1.setProjectId(frozen.getId());
        parity1.setSource(WishlistSource.client);
        parity1.setStatus(WishlistStatus.pending);
        parity1.setContent("[Java 21 / Spring Boot 3 Production Parity Gate] Целевой стек бэкенда Java 21 Spring Boot 3 REST API");
        parity1.setCreatedAt(Instant.parse("2026-11-24T22:29:00Z"));
        parity1 = wishlistRepository.save(parity1);

        WishlistEntity parity2 = new WishlistEntity();
        parity2.setProjectId(frozen.getId());
        parity2.setSource(WishlistSource.client);
        parity2.setStatus(WishlistStatus.pending);
        parity2.setContent("[Java 21 Spring Boot 3 Backend | Live Production Parity Gate] Implement the missing Java 21 Spring Boot 3 REST API");
        parity2.setCreatedAt(Instant.parse("2026-11-24T22:50:00Z"));
        parity2 = wishlistRepository.save(parity2);

        // Unfreeze -> automatically picks up legacy pending wishlists without manual intervention
        projectFlowService.activateProject(frozen.getId());

        List<WishlistEntity> pendingAfter = wishlistRepository.findByProjectIdAndStatus(frozen.getId(), WishlistStatus.pending);
        assertEquals(2, pendingAfter.size(),
                "Two overlapping Java 21 Parity Gate briefs should consolidate into 1, plus 1 launchability brief = 2");

        UUID p1Id = parity1.getId();
        UUID p2Id = parity2.getId();
        WishlistEntity mergedParity = pendingAfter.stream()
                .filter(w -> w.getConsolidatedFromIds() != null
                        && w.getConsolidatedFromIds().contains(p1Id.toString())
                        && w.getConsolidatedFromIds().contains(p2Id.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected the 2 overlapping Java 21 briefs to consolidate together"));

        List<String> mergedIds = objectMapper.readValue(mergedParity.getConsolidatedFromIds(), new TypeReference<>() {});
        assertEquals(List.of(p1Id.toString(), p2Id.toString()), mergedIds,
                "consolidated_from_ids must preserve deterministic chronological order");
        assertFalse(mergedParity.isConsolidationConflict());

        // Verify all 3 originals are preserved in DB with status=consolidated and merged_into_wishlist_id set
        WishlistEntity reloadedLaunch = wishlistRepository.findById(launchability.getId()).orElseThrow();
        WishlistEntity reloadedP1 = wishlistRepository.findById(p1Id).orElseThrow();
        WishlistEntity reloadedP2 = wishlistRepository.findById(p2Id).orElseThrow();
        assertEquals(WishlistStatus.consolidated, reloadedLaunch.getStatus());
        assertEquals(WishlistStatus.consolidated, reloadedP1.getStatus());
        assertEquals(WishlistStatus.consolidated, reloadedP2.getStatus());
        assertEquals(mergedParity.getId(), reloadedP1.getMergedIntoWishlistId());
        assertEquals(mergedParity.getId(), reloadedP2.getMergedIntoWishlistId());
    }
}
