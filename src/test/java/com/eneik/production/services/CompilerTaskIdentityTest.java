package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V137: a compiler task's identity is a function of the work it does.
 *
 * <p>Measured 2026-09-05 on the live circuit: ninety-two tasks created in one day, sixty-nine of them
 * duplicates of four subjects - thirty-one rows compiling one wishlist, thirty compiling another, each
 * dispatched row a Jules session. The existing guard was never wrong: it honestly saw no live carrier,
 * because a sweep had marked the previous one done. A fresh identity per turn is what made the repetition
 * invisible, and it is what this screen fixes.
 *
 * <p>Two assertions of different kinds, deliberately. The first is a property of the key itself. The second
 * is the structural one that actually holds the invariant: the SET of sites that mint a compiler task is
 * pinned, so a third site added tomorrow breaks this test whatever it is called. A guard inside one method
 * holds only for callers who pass through it, and twenty mechanisms write wishlists.
 */
class CompilerTaskIdentityTest {

    private static WishlistEntity wishlist(UUID id) {
        WishlistEntity w = new WishlistEntity();
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    private static ProjectEntity project(UUID id) {
        ProjectEntity p = new ProjectEntity();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("The same work asked for twice, in any order, is one identity - not two")
    void identityIsAFunctionOfTheWorkAndNotOfTheMoment() {
        UUID projectId = UUID.randomUUID();
        UUID a = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID b = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String forward = ProjectFlowService.compilerContentKey(project(projectId), List.of(wishlist(a), wishlist(b)));
        String reversed = ProjectFlowService.compilerContentKey(project(projectId), List.of(wishlist(b), wishlist(a)));

        assertEquals(forward, reversed,
                "The same set of wishlists asked for in a different order is the same work. If the order "
                        + "changed the identity, a batch reshuffled by any caller would mint a second row and "
                        + "the multiplication would return under a new name.");

        String otherProject = ProjectFlowService.compilerContentKey(project(UUID.randomUUID()), List.of(wishlist(a), wishlist(b)));
        assertNotEquals(forward, otherProject, "Work in another project is other work");

        String subset = ProjectFlowService.compilerContentKey(project(projectId), List.of(wishlist(a)));
        assertNotEquals(forward, subset, "A different set of wishlists is different work");
    }

    @Test
    @DisplayName("The set of sites that mint a wishlist-compiler task is pinned, and every one of them sets an identity")
    void everySiteThatMintsACompilerTaskGivesItAnIdentity() throws IOException {
        Path source = Path.of("src/main/java/com/eneik/production/services/ProjectFlowService.java");
        assertTrue(Files.exists(source), "screen cannot run: " + source.toAbsolutePath() + " not found");
        String src = Files.readString(source);

        // A site is where a task is marked as a wishlist compiler carrier. Comments are not sites: the
        // pattern requires the payload write itself, not a mention of the constant.
        Pattern site = Pattern.compile("payload\\.put\\(WISHLIST_COMPILER_PAYLOAD_KEY,\\s*WISHLIST_COMPILER_TASK_TYPE\\)");
        Matcher m = site.matcher(src);
        int sites = 0;
        while (m.find()) {
            sites++;
        }

        assertEquals(2, sites,
                "V137: exactly two sites mint a wishlist-compiler task - the one-shot compilation and the "
                        + "persistent worker. A third is a third way for the same work to acquire a new "
                        + "identity, which is how sixty-nine rows were created for four units of work. Name "
                        + "it here and give it a content key, or do not add it.");

        assertEquals(sites, countOccurrences(src, "setContentKey("),
                "Every site that mints a compiler task must give it an identity derived from its work. "
                        + "A site without a content key cannot be found by the next attempt, so that attempt "
                        + "creates a row instead of reviving one.");
    }

    private static int countOccurrences(String src, String needle) {
        int n = 0;
        int i = src.indexOf(needle);
        while (i >= 0) {
            n++;
            i = src.indexOf(needle, i + needle.length());
        }
        return n;
    }
}
