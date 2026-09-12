package com.eneik.production.services.compiler;

import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.ProjectEntity;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Law 26 - a path predicted for a client product may not lie in the factory's own namespace.
 *
 * <p>These assert a PROPERTY, not a list of names: the forbidden root is read from the production class
 * itself, so a rename of the factory's package cannot leave this screen asserting something that is no
 * longer true. A screen keyed to the literal "com.eneik.production" would stay green after such a rename
 * while the mechanism silently stopped guarding anything.
 */
class ProductNamespaceLaw26Test {

    @Test
    @DisplayName("the forbidden root is derived from the factory's own package, not written down")
    void rootIsDerivedFromTheFactorysOwnPackage() {
        String[] parts = TechnicalLeadCompiler.class.getPackageName().split("\\.");
        String expected = String.join(".", parts[0], parts[1], parts[2]);

        assertThat(TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT)
                .as("the prohibition must follow the factory if its package is ever renamed")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("a path inside the factory's own package is named as offending")
    void pathInsideTheFactoryPackageIsRefused() {
        String factoryDir = TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT.replace('.', '/');
        String offending = "src/main/java/" + factoryDir + "/services/InternalService.java";

        assertThat(TechnicalLeadCompiler.pathsInFactoryNamespace(List.of(offending)))
                .containsExactly(offending);
    }

    @Test
    @DisplayName("a sibling namespace under the same vendor is left alone")
    void siblingNamespaceIsNotRefused() {
        String[] parts = TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT.split("\\.");
        String siblingDir = parts[0] + "/" + parts[1] + "/somethingelse";
        String productPath = "src/main/java/" + siblingDir + "/auth/AuthController.java";

        assertThat(TechnicalLeadCompiler.pathsInFactoryNamespace(List.of(productPath)))
                .as("the guard must forbid the factory's namespace, not every namespace sharing a vendor prefix")
                .isEmpty();
    }

    @Test
    @DisplayName("only the offending paths are named, the rest of the scope survives")
    void onlyOffendingPathsAreNamed() {
        String factoryDir = TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT.replace('.', '/');
        String offending = "src/main/java/" + factoryDir + "/models/persistence/InternalEntity.java";
        String legitimate = "src/main/resources/db/migration/V2__dossier.sql";

        assertThat(TechnicalLeadCompiler.pathsInFactoryNamespace(List.of(offending, legitimate)))
                .containsExactly(offending);
    }

    @Test
    @DisplayName("an empty or null scope is not an offence")
    void emptyScopeIsNotAnOffence() {
        assertThat(TechnicalLeadCompiler.pathsInFactoryNamespace(List.of())).isEmpty();
        assertThat(TechnicalLeadCompiler.pathsInFactoryNamespace(null)).isEmpty();
    }

    @Test
    @DisplayName("pathsOutsideProductNamespace: accepts product package and rejects foreign or factory package")
    void pathsOutsideProductNamespace_acceptsProductPackageAndRejectsForeignOrFactoryPackage() {
        String productNamespace = "com.eneik.epidemiology";
        String factoryDir = TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT.replace('.', '/');

        String factoryPath = "src/main/java/" + factoryDir + "/services/InternalService.java";
        String foreignPath = "src/main/java/com/other/vendor/Service.java";
        String foreignTestPath = "src/test/java/com/other/vendor/ServiceTest.java";
        String legitimateJava = "src/main/java/com/eneik/epidemiology/services/ChessService.java";
        String legitimateTest = "src/test/java/com/eneik/epidemiology/services/ChessServiceTest.java";
        String legitimateSvelte = "frontend/src/components/Chess.svelte";
        String legitimateDoc = "docs/architecture/adr-002.md";

        List<String> offending = TechnicalLeadCompiler.pathsOutsideProductNamespace(
                productNamespace, true, false,
                List.of(factoryPath, foreignPath, foreignTestPath, legitimateJava, legitimateTest, legitimateSvelte, legitimateDoc)
        );

        assertThat(offending).containsExactlyInAnyOrder(factoryPath, foreignPath, foreignTestPath);
    }

    @Test
    @DisplayName("pathsOutsideProductNamespace: rejects foreign stack paths (Next.js/Prisma for Java, Java for Next.js)")
    void pathsOutsideProductNamespace_rejectsForeignStackPaths() {
        // 1. Java product must not receive Next.js / Prisma paths
        List<String> javaProductOffending = TechnicalLeadCompiler.pathsOutsideProductNamespace(
                "com.eneik.epidemiology", true, false,
                List.of(
                        "src/app/api/chess/route.ts",
                        "src/app/page.tsx",
                        "prisma/schema.prisma",
                        "src/main/java/com/eneik/epidemiology/services/ChessService.java"
                )
        );
        assertThat(javaProductOffending)
                .as("Next.js App router and Prisma paths are foreign to a Java stack")
                .containsExactlyInAnyOrder("src/app/api/chess/route.ts", "src/app/page.tsx", "prisma/schema.prisma");

        // 2. Next.js product must not receive Java source/resource paths
        List<String> nextJsOffending = TechnicalLeadCompiler.pathsOutsideProductNamespace(
                "customer-portal", false, true,
                List.of(
                        "src/main/java/com/example/Foo.java",
                        "src/main/resources/application.properties",
                        "src/app/api/foo/route.ts",
                        "prisma/schema.prisma"
                )
        );
        assertThat(nextJsOffending)
                .as("Java source and resources are foreign to a Next.js stack")
                .containsExactlyInAnyOrder("src/main/java/com/example/Foo.java", "src/main/resources/application.properties");
    }

    @Test
    @DisplayName("pathsOutsideProductNamespace: rejects Flyway migration when product does not use Flyway")
    void pathsOutsideProductNamespace_rejectsFlywayMigrationWhenFlywayNotConfigured() {
        String migrationPath = "src/main/resources/db/migration/V_NEXT__chess.sql";
        String entityPath = "src/main/java/com/eneik/epidemiology/models/persistence/ChessEntity.java";

        // Non-Flyway product
        List<String> nonFlywayOffending = TechnicalLeadCompiler.pathsOutsideProductNamespace(
                "com.eneik.epidemiology", false, false,
                List.of(migrationPath, entityPath)
        );
        assertThat(nonFlywayOffending)
                .as("Flyway migrations must be refused for products without Flyway")
                .containsExactly(migrationPath);

        // Flyway-enabled product
        List<String> flywayOffending = TechnicalLeadCompiler.pathsOutsideProductNamespace(
                "com.eneik.epidemiology", true, false,
                List.of(migrationPath, entityPath)
        );
        assertThat(flywayOffending)
                .as("Flyway migrations are admissible for Flyway-configured products")
                .isEmpty();
    }

    @Test
    @DisplayName("guard: refuses foreign scope, does NOT pass quietly as stripped collision, surfaces defect to journal")
    void guardRefusesForeignScopeAndSurfacesDefectToJournal() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setSlug("test-fiftieth");
        project.setProductNamespace("com.eneik.epidemiology");
        // No Flyway configured

        DefectJournalService defectJournal = mock(DefectJournalService.class);
        ProjectFileClaimRepository claimRepo = mock(ProjectFileClaimRepository.class);

        TechnicalLeadCompiler compiler = new TechnicalLeadCompiler(
                mock(WishlistRepository.class),
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
                claimRepo,
                mock(GeminiContextService.class),
                defectJournal
        );

        UUID featureId = UUID.randomUUID();
        List<String> foreignPaths = List.of("src/app/api/chess/route.ts");

        TechnicalLeadCompiler.CollisionGuardResult result = compiler.applyCrossEpicCollisionGuardForTest(
                project, featureId, "BARCAN-TAG-02", foreignPaths
        );

        assertThat(result.namespaceRefusal())
                .as("the collision guard must mark foreign namespace/stack prediction as a refusal")
                .isTrue();
        assertThat(result.paths())
                .as("foreign scope must not be admissible")
                .isEmpty();
        assertThat(result.refusedPaths())
                .as("the offending path must be recorded as refused")
                .containsExactly("src/app/api/chess/route.ts");
        assertThat(result.collisionNotes())
                .as("refusal note must be explicit")
                .contains("PRODUCT NAMESPACE REFUSAL");

        // Proof obligation: must NOT quiet or hide the incident - DefectJournal must record it
        ArgumentCaptor<String> categoryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> defectTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> patternIdCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(defectJournal).recordDefect(
                eq(project.getId()),
                eq(featureId),
                patternIdCaptor.capture(),
                eq("CRITICAL"),
                categoryCaptor.capture(),
                eq("TechnicalLeadCompiler"),
                defectTypeCaptor.capture(),
                any(String.class),
                eq(1.0)
        );

        assertThat(categoryCaptor.getValue()).isEqualTo("COMPILER");
        assertThat(defectTypeCaptor.getValue()).isEqualTo("PRODUCT_NAMESPACE_VIOLATION");
        assertThat(patternIdCaptor.getValue())
                .as("Charter pattern 6 is INDEXICAL_CONTEXT_LOCK (D006)")
                .isEqualTo(6);

        // The ledger repository must never even be consulted when paths are in foreign namespace
        verify(claimRepo, never()).findByProjectIdAndFilePathIn(any(), any());
    }

    @Test
    @DisplayName("guard: refuses Flyway migration for product without Flyway")
    void guardRefusesFlywayMigrationForProductWithoutFlyway() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setSlug("test-fiftieth");
        project.setProductNamespace("com.eneik.epidemiology");
        // No Flyway

        DefectJournalService defectJournal = mock(DefectJournalService.class);
        TechnicalLeadCompiler compiler = new TechnicalLeadCompiler(
                mock(WishlistRepository.class),
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
                mock(GeminiContextService.class),
                defectJournal
        );

        UUID featureId = UUID.randomUUID();
        String flywayPath = "src/main/resources/db/migration/V_NEXT__schema.sql";

        TechnicalLeadCompiler.CollisionGuardResult result = compiler.applyCrossEpicCollisionGuardForTest(
                project, featureId, "BARCAN-TAG-08", List.of(flywayPath)
        );

        assertThat(result.namespaceRefusal()).isTrue();
        assertThat(result.refusedPaths()).containsExactly(flywayPath);
        assertThat(result.paths()).isEmpty();

        verify(defectJournal).recordDefect(
                eq(project.getId()), eq(featureId), eq(6), eq("CRITICAL"),
                eq("COMPILER"), eq("TechnicalLeadCompiler"), eq("PRODUCT_NAMESPACE_VIOLATION"),
                anyString(), eq(1.0)
        );
    }
}
