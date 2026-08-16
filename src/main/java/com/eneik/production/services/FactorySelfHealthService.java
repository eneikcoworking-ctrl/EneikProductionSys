package com.eneik.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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

    // Optional so this service keeps working - and keeps being testable in isolation - when Kaizen is not
    // wired. A monitor that cannot start because its reporting channel is absent is worse than one that
    // logs; the point of this change is to ADD a channel, not to make the existing one conditional.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.kaizen.service.KaizenService kaizenService;

    /**
     * The last assessment already reported, so a condition that persists produces ONE finding rather than
     * one per hour. A monitor that repeats itself trains people to filter it, which returns the system to
     * being unwatched - the state this service exists to end.
     */
    private volatile String lastReportedAssessment;

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
                escalate(health);
            } else {
                // Recovered: the next occurrence is a new event and deserves to be reported again.
                lastReportedAssessment = null;
            }
        } catch (Exception e) {
            // Self-monitoring must never be the thing that breaks the system it monitors.
            log.debug("FactorySelfHealth: could not assess database health: {}", e.getMessage());
        }
    }

    /**
     * Puts a finding about the FACTORY into the same stream as findings about client products.
     *
     * This service detected the factory's own ill health and wrote log.warn - which is the shape of a
     * closed loop with the closure missing, because nothing reads logs autonomously. The 2026-08-14
     * incident is the proof: a 1678 MB file holding 96 MB of live data, the connection pool exhausted for
     * sixteen straight hours, every scheduled task failing, and the only symptom that reached a human was
     * the operator noticing the machine felt slow.
     *
     * SYSTEMIC_DEFECT is deliberately the category: it carries expectedGainPercent = 0 and is never
     * auto-applied, so this reaches a human as work rather than becoming an automatic change to the
     * factory's own configuration. Detecting a problem in oneself does not license repairing oneself.
     */
    private void escalate(DatabaseHealth health) {
        if (kaizenService == null) {
            return;
        }
        String assessment = health.assessment();
        if (assessment == null || assessment.equals(lastReportedAssessment)) {
            return;
        }
        try {
            kaizenService.recordSystemicDefectProposal(null, "Global",
                    "Factory self-health: the orchestrator's own database is unhealthy",
                    assessment + " Detected by FactorySelfHealthService, which watches the factory itself "
                            + "rather than the products it builds. Review-only: the factory's own "
                            + "configuration is never changed automatically.");
            lastReportedAssessment = assessment;
        } catch (Exception e) {
            // Self-monitoring must never be the thing that breaks the system it monitors - the same rule
            // that governs inspect() below.
            log.debug("FactorySelfHealth: could not record a systemic-defect proposal: {}", e.getMessage());
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

    /**
     * How long a live-data measurement stays usable. Declared arbitrary budget, not a tuned number.
     *
     * F40, measured 2026-08-16: this walk calls H2's DISK_SPACE_USED once per table, which on a 419 MB
     * store held a pooled connection past the 30 s leak threshold added in Step 4 - and, because
     * InfrastructureVerdictLayer calls inspect(), made GET /api/projects/{id}/verdict take 68.9 s on an
     * IDLE system. A monitor that holds connections that long is a contributor to the load it reports on.
     *
     * Bloat is a property that moves over hours, so a measurement minutes old is still a true statement
     * about it. Caching therefore costs no honesty: what is reported remains something that was measured,
     * never something inferred.
     */
    private static final Duration LIVE_DATA_MEASUREMENT_TTL = Duration.ofMinutes(10);

    private volatile long cachedLiveDataBytes = -1L;
    private volatile Instant cachedLiveDataAt = Instant.EPOCH;

    /** Sum of what the tables actually hold, which is what the file size should be compared against. */
    private long liveDataBytes() {
        Instant now = Instant.now();
        long cached = cachedLiveDataBytes;
        if (cached >= 0 && Duration.between(cachedLiveDataAt, now).compareTo(LIVE_DATA_MEASUREMENT_TTL) < 0) {
            return cached;
        }
        long measured = measureLiveDataBytes();
        cachedLiveDataBytes = measured;
        cachedLiveDataAt = now;
        return measured;
    }

    private long measureLiveDataBytes() {
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
