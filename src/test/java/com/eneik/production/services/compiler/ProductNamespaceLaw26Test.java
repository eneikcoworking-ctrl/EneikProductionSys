package com.eneik.production.services.compiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
