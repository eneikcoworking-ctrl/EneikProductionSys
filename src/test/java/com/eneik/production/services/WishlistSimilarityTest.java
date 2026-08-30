package com.eneik.production.services;

import com.eneik.production.models.persistence.WishlistEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 4.45. These are the exact rows measured on the live circuit (test-fiftieth) on 2026-08-30, quoted
 * verbatim from the database: three slices the compiler produced from one client brief, two of which the
 * grouper merged away at 00:33:34, taking a Must-Be client requirement out of the flow and freezing the
 * project behind the epic they left empty.
 */
class WishlistSimilarityTest {

    private static final UUID CLIENT_BRIEF = UUID.fromString("8aff0d75-658d-46b6-aadc-0095cc3b6488");
    private static final UUID OTHER_BRIEF = UUID.fromString("df7e776e-da15-4da3-ac73-a64f64ee181e");

    @Test
    void twoSlicesOfOneBriefAreNeverDuplicatesOfEachOther() {
        WishlistEntity privacyBackend = slice(CLIENT_BRIEF,
                "Internal work item 1 (BARCAN-TAG-02) from wishlist " + CLIENT_BRIEF + ": Privacy Compliance Backend and Integration",
                "When implementing privacy compliance for this epic, I want to expose endpoints for data "
                        + "export/erasure and integrate the consent platform, so that the product meets "
                        + "statutory privacy requirements.");
        WishlistEntity accountRecovery = slice(CLIENT_BRIEF,
                "Internal UI work item 1 (BARCAN-TAG-02) from wishlist " + CLIENT_BRIEF + ": Self-Service Account Recovery",
                "When implementing account recovery for this epic, I want to provide a secure reset "
                        + "mechanism, so that locked-out users can regain access autonomously.");

        assertFalse(ProjectFlowService.areWishlistItemsSimilar(privacyBackend, accountRecovery),
                "the compiler already decided these are different parts of one brief");
    }

    @Test
    void theGeneratedSliceHeaderAloneDoesNotMakeSlicesOfDifferentBriefsDuplicates() {
        // Same boilerplate header, different briefs, different requirements. Before this fix the shared
        // tokens internal/work/item/wishlist carried the comparison and this scored 0.417.
        WishlistEntity privacyBackend = slice(CLIENT_BRIEF,
                "Internal work item 1 (BARCAN-TAG-02) from wishlist " + CLIENT_BRIEF + ": Privacy Compliance Backend and Integration",
                "When implementing privacy compliance for this epic, I want to expose endpoints for data "
                        + "export/erasure and integrate the consent platform, so that the product meets "
                        + "statutory privacy requirements.");
        WishlistEntity backup = slice(OTHER_BRIEF,
                "Internal work item 3 (BARCAN-TAG-04) from wishlist " + OTHER_BRIEF + ": System Backup and Verified Restore",
                "When protecting the installation for this epic, I want scheduled backups and a verified "
                        + "restore drill, so that data loss is recoverable.");

        assertFalse(ProjectFlowService.areWishlistItemsSimilar(privacyBackend, backup),
                "the shared header is the factory's own text, not the requirement");
    }

    @Test
    void slicesOfDifferentBriefsAskingForTheSameThingAreStillDuplicates() {
        // The mechanism must keep working, otherwise this fix trades one silent loss for another: real
        // duplicates re-entering the flow is exactly what grouping exists to prevent.
        String job = "When implementing pagination for this epic, I want the dossier document list to page "
                + "through results, so that large dossiers stay usable.";
        WishlistEntity first = slice(CLIENT_BRIEF,
                "Internal work item 1 (BARCAN-TAG-02) from wishlist " + CLIENT_BRIEF + ": Dossier Document Pagination", job);
        WishlistEntity second = slice(OTHER_BRIEF,
                "Internal work item 2 (BARCAN-TAG-02) from wishlist " + OTHER_BRIEF + ": Paginate Dossier Documents", job);

        assertTrue(ProjectFlowService.areWishlistItemsSimilar(first, second));
    }

    @Test
    void rawBriefsAreStillComparedOnTheirOwnContent() {
        // Nothing has been compiled for a raw brief, so its content IS the requirement - the path this fix
        // must leave exactly as it was.
        WishlistEntity first = new WishlistEntity();
        first.setId(UUID.randomUUID());
        first.setContent("Coverage audit gap: missing pagination for dossier documents search");
        WishlistEntity second = new WishlistEntity();
        second.setId(UUID.randomUUID());
        second.setContent("Coverage audit gap: dossier documents search has no pagination");

        assertTrue(ProjectFlowService.areWishlistItemsSimilar(first, second));
    }

    /**
     * Findings about different tasks are different findings, whatever their prose looks like. Measured on
     * the live circuit 2026-08-30: sixteen delivery findings whose texts differ only in a task title were
     * merged into one, and fifteen pieces of ordered work vanished twenty minutes after being ordered.
     */
    @Test
    void findingsAboutDifferentTasksAreNeverDuplicatesOfEachOther() {
        String boilerplate = "Planned work never reached the main branch.\n\nThe closed task \"%s\" has "
                + "status failed, no merge evidence exists for it at all - no merged pull request, nothing "
                + "on main - and nothing is going to retry it.";
        WishlistEntity first = finding(UUID.randomUUID(), String.format(boilerplate, "API Slice B8dca98a"));
        WishlistEntity second = finding(UUID.randomUUID(), String.format(boilerplate, "API Slice 2c3442ef"));

        assertFalse(ProjectFlowService.areWishlistItemsSimilar(first, second),
                "the subject of a per-task finding is the task, not the sentence around it");
    }

    /**
     * The complement: a per-task finding is not a duplicate of a brief that is about no task at all, even
     * when both mention the same words. Without this the rule would only be half applied.
     */
    @Test
    void aPerTaskFindingIsNotADuplicateOfABriefAboutNoTask() {
        WishlistEntity finding = finding(UUID.randomUUID(), "Planned work never reached the main branch.");
        WishlistEntity plain = new WishlistEntity();
        plain.setId(UUID.randomUUID());
        plain.setContent("Planned work never reached the main branch.");

        assertFalse(ProjectFlowService.areWishlistItemsSimilar(finding, plain));
        assertFalse(ProjectFlowService.areWishlistItemsSimilar(plain, finding));
    }

    private WishlistEntity finding(UUID sourceTaskId, String content) {
        WishlistEntity item = new WishlistEntity();
        item.setId(UUID.randomUUID());
        item.setSourceTaskId(sourceTaskId);
        item.setContent(content);
        return item;
    }

    private WishlistEntity slice(UUID originBrief, String content, String jtbd) {
        WishlistEntity item = new WishlistEntity();
        item.setId(UUID.randomUUID());
        item.setOriginWishlistId(originBrief);
        item.setCompiledByRole("BARCAN-TAG-09");
        item.setContent(content);
        item.setJtbd(jtbd);
        return item;
    }
}
