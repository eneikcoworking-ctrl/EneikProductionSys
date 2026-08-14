package com.eneik.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * The factory watches its clients' products for runtime health and never watched its own.
 *
 * That gap has a measured cost. On 2026-08-14 the H2 file had grown to 1678 MB holding 96 MB of real data
 * - 94% dead pages, because containers were being killed before the store could close cleanly and reclaim
 * them. The connection pool was exhausted for sixteen straight hours, every scheduled task failing, and
 * nothing reported it: the only symptom reaching a human was the operator noticing the machine felt slow.
 * A system that diagnoses others while blind to itself will always find that out the same way.
 *
 * Two signals, chosen because each catches a failure the other cannot:
 *   - bloat ratio (file size versus live data): rises when the store stops reclaiming space, which is
 *     precisely what happened and what no row count would have revealed.
 *   - absolute size: rises when data genuinely accumulates, e.g. a log with no retention policy, which
 *     the ratio alone would call perfectly healthy.
 */
@Service
public class FactorySelfHealthService {
    private static final Logger log = LoggerFactory.getLogger(FactorySelfHealthService.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${factory-self-health.db-file:./data/eneik_db.mv.db}")
    private String databaseFile;

    /** Warn above this multiple of live data. Reached 17x during the incident. */
    @Value("${factory-self-health.bloat-ratio-warn:3.0}")
    private double bloatRatioWarn;

    @Value("${factory-self-health.size-warn-mb:512}")
    private long sizeWarnMb;

    public FactorySelfHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record DatabaseHealth(long fileSizeBytes, long liveDataBytes, double bloatRatio, boolean healthy,
                                 String assessment) {
    }

    @Scheduled(cron = "${factory-self-health.cron:0 40 * * * ?}")
    public void reportIfUnhealthy() {
        try {
            DatabaseHealth health = inspect();
            if (!health.healthy()) {
                log.warn("FactorySelfHealth: {}", health.assessment());
            }
        } catch (Exception e) {
            // Self-monitoring must never be the thing that breaks the system it monitors.
            log.debug("FactorySelfHealth: could not assess database health: {}", e.getMessage());
        }
    }

    public DatabaseHealth inspect() {
        long fileSize = fileSizeBytes();
        long liveData = liveDataBytes();

        // Ratio is meaningless while the database is tiny - a fresh install is legitimately mostly
        // overhead, and warning about it would train everyone to ignore this.
        boolean bigEnoughToJudge = fileSize > 64L * 1024 * 1024;
        double ratio = liveData > 0 ? (double) fileSize / liveData : 0.0;

        boolean bloated = bigEnoughToJudge && liveData > 0 && ratio > bloatRatioWarn;
        boolean tooBig = fileSize > sizeWarnMb * 1024 * 1024;

        String assessment;
        if (bloated) {
            assessment = String.format(
                    "database file is %d MB holding only %d MB of live data (%.1fx bloat) - the store is not "
                            + "reclaiming freed space, which happens when it is killed instead of closed; a clean "
                            + "shutdown compacts it",
                    fileSize / (1024 * 1024), liveData / (1024 * 1024), ratio);
        } else if (tooBig) {
            assessment = String.format(
                    "database file is %d MB and the data is genuinely that large - check retention on the "
                            + "append-only tables rather than compacting",
                    fileSize / (1024 * 1024));
        } else {
            assessment = String.format("database file %d MB, live data %d MB - healthy",
                    fileSize / (1024 * 1024), liveData / (1024 * 1024));
        }
        return new DatabaseHealth(fileSize, liveData, ratio, !bloated && !tooBig, assessment);
    }

    private long fileSizeBytes() {
        try {
            Path path = Paths.get(databaseFile);
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** Sum of what the tables actually hold, which is what the file size should be compared against. */
    private long liveDataBytes() {
        long total = 0L;
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            String table = String.valueOf(row.get("TABLE_NAME"));
            try {
                Long used = jdbcTemplate.queryForObject("CALL DISK_SPACE_USED(?)", Long.class, table);
                total += used == null ? 0L : used;
            } catch (Exception e) {
                // One unreadable table must not void the whole measurement.
            }
        }
        return total;
    }
}
