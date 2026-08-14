package com.eneik.production.services;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduces the real incident numbers: a 1678 MB file holding 96 MB of live data went unnoticed for
 * sixteen hours while every scheduled task failed. These tests pin that such a state is now reported, and
 * equally that ordinary states are not - a monitor that cries wolf gets muted, which returns the system to
 * being unwatched.
 */
class FactorySelfHealthServiceTest {

    private FactorySelfHealthService serviceWith(Path dbFile, long liveDataBytes) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString()))
                .thenReturn(List.of(Map.of("TABLE_NAME", "TASKS")));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(liveDataBytes);

        FactorySelfHealthService service = new FactorySelfHealthService(jdbc);
        ReflectionTestUtils.setField(service, "databaseFile", dbFile.toString());
        ReflectionTestUtils.setField(service, "bloatRatioWarn", 3.0);
        ReflectionTestUtils.setField(service, "sizeWarnMb", 512L);
        return service;
    }

    private Path fileOfSize(long bytes) throws Exception {
        Path file = Files.createTempFile("selfhealth", ".mv.db");
        try (var channel = new java.io.RandomAccessFile(file.toFile(), "rw")) {
            channel.setLength(bytes);
        }
        file.toFile().deleteOnExit();
        return file;
    }

    @Test
    void detectsTheStateThatActuallyTookTheDatabaseDown() throws Exception {
        // The real numbers from 2026-08-14, scaled down to keep the test fast: same 17x ratio.
        Path file = fileOfSize(170L * 1024 * 1024);
        FactorySelfHealthService service = serviceWith(file, 10L * 1024 * 1024);

        FactorySelfHealthService.DatabaseHealth health = service.inspect();

        assertThat(health.healthy()).isFalse();
        assertThat(health.bloatRatio()).isGreaterThan(10.0);
        assertThat(health.assessment())
                .as("the message must point at the actual cause - unreclaimed space from an unclean "
                        + "shutdown - not merely state that the file is large")
                .contains("not reclaiming");
    }

    @Test
    void staysQuietWhenTheFileIsLargeBecauseTheDataIsLarge() throws Exception {
        Path file = fileOfSize(100L * 1024 * 1024);
        FactorySelfHealthService service = serviceWith(file, 90L * 1024 * 1024);

        assertThat(service.inspect().healthy())
                .as("a file that is mostly real data is healthy however big it is")
                .isTrue();
    }

    @Test
    void doesNotJudgeRatioOnASmallDatabase() throws Exception {
        // A fresh install is legitimately mostly overhead; warning here would train everyone to ignore it.
        Path file = fileOfSize(20L * 1024 * 1024);
        FactorySelfHealthService service = serviceWith(file, 1L * 1024 * 1024);

        assertThat(service.inspect().healthy()).isTrue();
    }

    @Test
    void flagsGenuineDataGrowthDifferentlyFromBloat() throws Exception {
        Path file = fileOfSize(600L * 1024 * 1024);
        FactorySelfHealthService service = serviceWith(file, 590L * 1024 * 1024);

        FactorySelfHealthService.DatabaseHealth health = service.inspect();

        assertThat(health.healthy()).isFalse();
        assertThat(health.assessment())
                .as("real growth calls for retention, not compaction - the advice must differ or it is useless")
                .contains("retention");
    }

    @Test
    void neverThrowsWhenTheDatabaseFileIsNotWhereItExpects() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        FactorySelfHealthService service = new FactorySelfHealthService(jdbc);
        ReflectionTestUtils.setField(service, "databaseFile", "/nonexistent/path/eneik_db.mv.db");
        ReflectionTestUtils.setField(service, "bloatRatioWarn", 3.0);
        ReflectionTestUtils.setField(service, "sizeWarnMb", 512L);

        FactorySelfHealthService.DatabaseHealth health = service.inspect();

        assertThat(health.fileSizeBytes()).isZero();
        assertThat(health.healthy())
                .as("self-monitoring must never become the thing that breaks what it monitors")
                .isTrue();
    }
}
