package com.eneik.production;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Screen for Flyway migration validation integrity
 * (ALFRED_TARSKIY_01_FALSIFICATION_HARNESS / D008 False green,
 *  DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT / D010 Data lineage loss).
 *
 * Proof obligations:
 * 1. application.properties enforces spring.flyway.validate-on-migrate=true and spring.flyway.repair-on-startup=false.
 * 2. EneikProductionApplication.flywayMigrationStrategy does NOT call flyway.repair() by default on normal startup.
 * 3. Modifying an applied migration's checksum causes Flyway validation to fail and abort migration with FlywayValidateException
 *    (falsification harness refuting the silent pass of tampered migrations).
 * 4. Automatic repair is only engaged when explicitly requested via spring.flyway.repair-on-startup=true.
 */
class FlywayMigrationValidationTest {

    private final EneikProductionApplication application = new EneikProductionApplication();

    @Test
    void applicationPropertiesEnforcesValidateOnMigrateAndDisablesRepairOnStartup() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(properties.getProperty("spring.flyway.validate-on-migrate"))
                .as("validate-on-migrate must be explicitly enabled to detect modified migrations on startup")
                .isEqualTo("true");

        assertThat(properties.getProperty("spring.flyway.repair-on-startup"))
                .as("repair-on-startup must be false by default to prevent silent reconciliation of tampered migrations")
                .isEqualTo("false");

        File mainPropsFile = new File("src/main/resources/application.properties");
        if (mainPropsFile.exists()) {
            Properties mainProps = new Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(mainPropsFile)) {
                mainProps.load(in);
            }
            assertThat(mainProps.getProperty("spring.flyway.validate-on-migrate")).isEqualTo("true");
            assertThat(mainProps.getProperty("spring.flyway.repair-on-startup")).isEqualTo("false");
        }
    }

    @Test
    void flywayMigrationStrategyExecutesMigrateWithoutRepairByDefault() {
        Flyway mockFlyway = mock(Flyway.class);
        FlywayMigrationStrategy defaultStrategy = application.flywayMigrationStrategy(false);

        defaultStrategy.migrate(mockFlyway);

        verify(mockFlyway).migrate();
        verify(mockFlyway, never()).repair();
    }

    @Test
    void flywayMigrationStrategyExecutesRepairOnlyWhenExplicitlyConfigured() {
        Flyway mockFlyway = mock(Flyway.class);
        FlywayMigrationStrategy repairStrategy = application.flywayMigrationStrategy(true);

        repairStrategy.migrate(mockFlyway);

        InOrder inOrder = inOrder(mockFlyway);
        inOrder.verify(mockFlyway).repair();
        inOrder.verify(mockFlyway).migrate();
    }

    @Test
    void tamperedAppliedMigrationThrowsValidationExceptionUnderDefaultStrategy() throws Exception {
        Path tempDir = Files.createTempDirectory("flyway-test-migrations");
        try {
            File v1 = new File(tempDir.toFile(), "V1__init_schema.sql");
            try (FileWriter writer = new FileWriter(v1)) {
                writer.write("CREATE TABLE sample_entity (id VARCHAR(36) PRIMARY KEY, name VARCHAR(255));");
            }

            String dbName = "flyway_test_" + UUID.randomUUID().toString().replace("-", "");
            String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl, "sa", "")
                    .locations("filesystem:" + tempDir.toAbsolutePath())
                    .validateOnMigrate(true)
                    .cleanDisabled(false)
                    .load();

            // 1. First migration runs cleanly
            flyway.migrate();

            // 2. Simulate tampering with the applied migration (altering the checksum in flyway_schema_history)
            try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE \"flyway_schema_history\" SET \"checksum\" = 123456789 WHERE \"version\" = '1'");
            }

            // 3. Default strategy (repairOnStartup = false): migrate() triggers validate(), which MUST throw FlywayValidateException
            FlywayMigrationStrategy defaultStrategy = application.flywayMigrationStrategy(false);
            assertThatThrownBy(() -> defaultStrategy.migrate(flyway))
                    .isInstanceOf(FlywayException.class)
                    .satisfies(e -> {
                        assertThat(e).isInstanceOfAny(FlywayValidateException.class, FlywayException.class);
                        assertThat(e.getMessage()).containsIgnoringCase("Validate failed");
                    });

            // 4. Counterexample proof: if repair is explicitly enabled, repair reconciles the checksum and migration succeeds
            FlywayMigrationStrategy repairStrategy = application.flywayMigrationStrategy(true);
            repairStrategy.migrate(flyway);
        } finally {
            deleteRecursively(tempDir.toFile());
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
