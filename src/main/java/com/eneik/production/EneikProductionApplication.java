package com.eneik.production;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@SpringBootApplication
public class EneikProductionApplication {

    private static final Logger log = LoggerFactory.getLogger(EneikProductionApplication.class);

    // Optional so the many slice tests that build a context without a DataSource still start.
    @Autowired(required = false)
    private DataSource dataSource;

    public static void main(String[] args) {
        SpringApplication.run(EneikProductionApplication.class, args);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }

    /**
     * Compacts the H2 store on a clean shutdown (Phase 4 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md).
     *
     * <p>Why this is needed on top of the URL flags: H2's MVStore does not return pages to the file when a
     * process dies without closing the store, and this factory's container has died that way repeatedly
     * (OOM kills, `wsl --shutdown`, a wedged Docker engine). The file grew to 1.84 GB that way, past the
     * point where it fit the page cache, and the resulting `read 0` failures took the whole pipeline down.
     * {@code DEFRAG_ALWAYS} handles the shutdowns H2 itself performs; this hook covers the ones Spring
     * performs, which is the normal path for a `docker compose down` or a SIGTERM from the daemon.
     *
     * <p>Deliberately swallows every failure. A database that will not compact is a slow database; an
     * exception thrown out of {@code @PreDestroy} aborts the rest of the shutdown, which is how a store
     * gets left unclosed - exactly the state that causes the bloat this method exists to prevent.
     *
     * <p>Non-H2 datasources are skipped rather than probed: SHUTDOWN COMPACT is H2-specific syntax and
     * would be a syntax error anywhere else.
     */
    // Spring can invoke a @PreDestroy more than once when several shutdown paths converge (observed
    // 2026-08-28: the second call found the store already closed and logged a warning that reads like a
    // failure). Compaction is idempotent in effect but not free, and a warning that is not a problem
    // teaches the reader to ignore warnings.
    private final java.util.concurrent.atomic.AtomicBoolean compacted =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @PreDestroy
    public void compactH2StoreOnShutdown() {
        if (dataSource == null || !compacted.compareAndSet(false, true)) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("h2")) {
                return;
            }
            long startedAt = System.currentTimeMillis();
            try (Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN COMPACT");
            }
            log.info("H2 store compacted on shutdown in {} ms", System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            log.warn("Could not compact the H2 store on shutdown (continuing shutdown regardless): {}",
                    e.getMessage());
        }
    }
}
