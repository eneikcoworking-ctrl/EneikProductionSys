package com.eneik.production.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Screen for Law 20 / invariant S4: {@code ∀ path to merge: it passes through the gate}.
 *
 * <p>Why this is written over the SET of merge sites rather than over a forbidden name. The first version
 * of this screen asserted that a method called {@code reconcileCleanOpenGitHubPullRequests} does not exist
 * and that the string {@code DIRECT-SWEEP} does not appear. Both are blacklists of one observed symptom:
 * they go green the moment the same ungated merge is written again under any other name, which is exactly
 * the defect class this repository has already recorded twice - a guard whose pattern list is narrower than
 * the property it guards (see the refuted list, "тесты, которые не могут упасть").
 *
 * <p>The property itself is a counting statement, and it is stated here as one. Let {@code M} be the set of
 * source sites that issue a merge of PRODUCT code to a protected branch. S4 requires every element of
 * {@code M} to be gated, which is enforceable only if {@code M} is pinned: an unenumerated site cannot be
 * shown to be gated. So the screen fixes {@code M} and fails on any addition, anywhere in the tree, under
 * any name.
 *
 * <p>{@code M} as measured on the real flow:
 * <ul>
 *   <li>{@code AutoMergeService.executeMerge} - the gated path. Issues the merge itself over HTTP and is
 *       preceded by {@code rejectByFactoryPokaYoke}.</li>
 *   <li>{@code AutoMergeService} closeout - merges a completed feature thread branch into main.</li>
 *   <li>{@code AutoMergeService} conflict-fix - merges a fix PR into the thread branch, not into main.</li>
 * </ul>
 *
 * <p>Deliberately NOT in {@code M}: {@code GitHubPullRequestService.mergeRecordPullRequest}. A record PR
 * carries the factory's own bookkeeping (a review verdict, an audit report), never client product code -
 * Law 2 keeps the carrier channel separate from the product one, and holding carrier records to the product
 * gate is the category mistake that law forbids. The transport methods inside
 * {@code GitHubPullRequestService} are likewise not sites: they are the single implementation of the HTTP
 * call, and it is their CALLERS that this screen counts.
 */
class AutoMergeLaw20InvariantS4Test {

    private static final Path MAIN = Path.of("src/main/java/com/eneik/production");

    /** The transport layer itself - defines the merge call, does not decide to merge. */
    private static final String TRANSPORT = "GitHubPullRequestService.java";

    /** The one service allowed to decide that product code merges. */
    private static final String GATEKEEPER = "AutoMergeService.java";

    /** Sites that issue a product merge: a call to mergePullRequest, or a raw PUT to a .../merge path. */
    private static final Pattern PRODUCT_MERGE_SITE =
            Pattern.compile("mergePullRequest\\s*\\(|/pulls/\"?\\s*\\+[^;]*\\+\\s*\"/merge\"|\"/merge\"");

    private static List<Path> mainSources() throws IOException {
        assertTrue(Files.exists(MAIN), "S4 screen cannot run: main source tree not found at " + MAIN.toAbsolutePath());
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /**
     * Source with comments blanked and string literals kept, so prose about merging is never counted and a
     * literal is never mistaken for a comment.
     *
     * <p>Written as a scanner rather than a regex, after two failures that are worth keeping recorded.
     * First: stripping {@code //} to end of line deleted the merge URL itself, because
     * {@code "https://api.github.com/..."} carries {@code //} inside a string literal, and the screen then
     * reported two merge sites where the file holds three. Second: matching literals and comments in one
     * alternation fixed that and then overflowed the stack on a six-thousand-line service, because a lazy
     * {@code .*?} over an input that size backtracks recursively. A left-to-right scan has neither failure
     * mode: one pass, constant stack, no backtracking.
     */
    private static String code(Path file) throws IOException {
        String src = Files.readString(file);
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, src.length());
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(c);
                i++;
                while (i < src.length()) {
                    char q = src.charAt(i);
                    out.append(q);
                    i++;
                    if (q == '\\' && i < src.length()) {
                        out.append(src.charAt(i));
                        i++;
                    } else if (q == quote) {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("S4: only the gatekeeper decides that product code merges - no other file may issue one")
    void noServiceOutsideTheGatekeeperIssuesAProductMerge() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            String name = file.getFileName().toString();
            if (name.equals(TRANSPORT) || name.equals(GATEKEEPER)) {
                continue;
            }
            if (PRODUCT_MERGE_SITE.matcher(code(file)).find()) {
                offenders.add(name);
            }
        }
        assertEquals(List.of(), offenders,
                "Law 20 / S4: a product merge may be issued only from " + GATEKEEPER + ", where the gate is. "
                        + "These files issue one on their own: " + offenders + ". A merge written outside the "
                        + "gatekeeper is an ungated path to main whatever it is called.");
    }

    @Test
    @DisplayName("S4: the set of product-merge sites in the gatekeeper is pinned, so a new one cannot appear unnoticed")
    void theSetOfProductMergeSitesIsPinned() throws IOException {
        Matcher m = PRODUCT_MERGE_SITE.matcher(code(MAIN.resolve("services").resolve(GATEKEEPER)));
        int sites = 0;
        while (m.find()) {
            sites++;
        }
        assertEquals(3, sites,
                "Law 20 / S4: " + GATEKEEPER + " is expected to hold exactly three product-merge sites - "
                        + "executeMerge (gated), the feature-thread closeout, and the conflict-fix merge into "
                        + "the thread branch - but holds " + sites + ". Adding a fourth is adding a path to "
                        + "main: name it here, and name the gate it passes through, or do not add it.");
    }

    @Test
    @DisplayName("S4: the gated site consults the poka-yoke before it issues the merge")
    void theGatedSiteConsultsThePokaYokeFirst() throws IOException {
        String source = code(MAIN.resolve("services").resolve(GATEKEEPER));

        int executeMerge = source.indexOf("void executeMerge(PrReviewEntity");
        assertTrue(executeMerge > 0, "executeMerge(PrReviewEntity) must exist in " + GATEKEEPER);

        int pokaYoke = source.indexOf("rejectByFactoryPokaYoke", executeMerge);
        int issuance = source.indexOf("\"/merge\"", executeMerge);

        assertTrue(pokaYoke > 0, "executeMerge must consult rejectByFactoryPokaYoke");
        assertTrue(issuance > 0, "executeMerge must be the site that issues the merge");
        assertTrue(pokaYoke < issuance,
                "Law 20 / S4: rejectByFactoryPokaYoke must be consulted BEFORE the merge is issued, "
                        + "not after - a gate that runs after the action is not a gate.");
    }
}
