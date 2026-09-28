package com.eneik.production;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 2 для EneikProductionApplication:
 * детерминированная дефрагментация хранилища при shutdown (DEREK_PARFIT_01 / D010)
 * и эмпирическая проверка жизненного цикла рантайма (LYUDVIG_VITGENSHTEYN_14 / D013).
 *
 * Proof obligations:
 * 1. Идемпотентность: повторный вызов @PreDestroy при схождении нескольких shutdown-хуков Spring
 *    выполняет SHUTDOWN COMPACT строго один раз.
 * 2. Безопасность: не-H2 базы данных (PostgreSQL, MySQL) распознаются и пропускают команду без синтаксических ошибок.
 * 3. Fail-safe: сбои ввода-вывода или закрытия базы гарантированно изолируются и не прерывают завершение работы JVM.
 * 4. Защита от NPE при отсутствии DataSource (slice-тесты).
 * 5. FlywayMigrationStrategy: условный запуск repair() при repairOnStartup=true.
 */
class EneikProductionApplicationShutdownCompactTest {

    @Test
    @DisplayName("NPE-защита: При отсутствии DataSource вызов завершается корректно без исключений")
    void nullDataSourceSkipsCompactionCleanly() {
        EneikProductionApplication app = new EneikProductionApplication();
        assertThatCode(app::compactH2StoreOnShutdown).doesNotThrowAnyException();

        AtomicBoolean compacted = (AtomicBoolean) ReflectionTestUtils.getField(app, "compacted");
        assertThat(compacted).isNotNull();
        // При null DataSource флаг не должен блокировать возможный будущий вызов
        assertThat(compacted.get()).isFalse();
    }

    @Test
    @DisplayName("DEREK_PARFIT_01: Идемпотентность: SHUTDOWN COMPACT вызывается строго один раз даже при конвергенции хуков")
    void idempotentCompactionExecutesAtMostOnce() throws SQLException {
        EneikProductionApplication app = new EneikProductionApplication();

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(connection.createStatement()).thenReturn(statement);

        ReflectionTestUtils.setField(app, "dataSource", dataSource);

        // Первый вызов (штатный @PreDestroy)
        app.compactH2StoreOnShutdown();

        // Второй и третий вызовы (конкурентные хуки shutdown Spring / JVM shutdown hook)
        app.compactH2StoreOnShutdown();
        app.compactH2StoreOnShutdown();

        // Соединение и стейтмент открыты и выполнены СТРОГО один раз
        verify(dataSource, times(1)).getConnection();
        verify(statement, times(1)).execute("SHUTDOWN COMPACT");

        AtomicBoolean compacted = (AtomicBoolean) ReflectionTestUtils.getField(app, "compacted");
        assertThat(compacted).isNotNull();
        assertThat(compacted.get()).isTrue();
    }

    @Test
    @DisplayName("DEREK_PARFIT_01: Параллельные вызовы shutdown из разных потоков атомарно выполняют компактизацию ровно 1 раз")
    void concurrentShutdownInvocationsAreAtomicallyIdempotent() throws Exception {
        EneikProductionApplication app = new EneikProductionApplication();

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(connection.createStatement()).thenReturn(statement);

        ReflectionTestUtils.setField(app, "dataSource", dataSource);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                startLatch.await();
                app.compactH2StoreOnShutdown();
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Одновременный старт всех потоков
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        verify(dataSource, times(1)).getConnection();
        verify(statement, times(1)).execute("SHUTDOWN COMPACT");
    }

    @Test
    @DisplayName("LYUDVIG_VITGENSHTEYN_14: Не-H2 базы данных (PostgreSQL, MySQL) безопасно пропускают H2-специфичную команду")
    void nonH2DatabaseSkipsShutdownCompact() throws SQLException {
        EneikProductionApplication app = new EneikProductionApplication();

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        ReflectionTestUtils.setField(app, "dataSource", dataSource);

        app.compactH2StoreOnShutdown();

        // Стейтмент не создавался и команда не отправлялась
        verify(connection, never()).createStatement();
    }

    @Test
    @DisplayName("Fail-safe: Ошибки драйвера или ввода-вывода при компактизации изолируются и не прерывают shutdown")
    void failSafeExceptionSwallowingEnsuresShutdownNeverAborts() throws SQLException {
        EneikProductionApplication app = new EneikProductionApplication();

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Simulated disk I/O lock timeout during shutdown"));

        ReflectionTestUtils.setField(app, "dataSource", dataSource);

        // Исключение обязано быть поглощено с warning логом, без проброса наружу
        assertThatCode(app::compactH2StoreOnShutdown).doesNotThrowAnyException();

        // Флаг compacted тем не менее выставлен в true, предотвращая повторные зависания
        AtomicBoolean compacted = (AtomicBoolean) ReflectionTestUtils.getField(app, "compacted");
        assertThat(compacted).isNotNull();
        assertThat(compacted.get()).isTrue();
    }

    @Test
    @DisplayName("FlywayMigrationStrategy: Корректное выполнение миграций и условного repair")
    void flywayMigrationStrategyExecutesRepairWhenRequested() {
        EneikProductionApplication app = new EneikProductionApplication();

        Flyway flyway = mock(Flyway.class);

        // Режим repairOnStartup = true
        FlywayMigrationStrategy strategyWithRepair = app.flywayMigrationStrategy(true);
        strategyWithRepair.migrate(flyway);
        verify(flyway).repair();
        verify(flyway).migrate();

        reset(flyway);

        // Режим repairOnStartup = false (по умолчанию)
        FlywayMigrationStrategy defaultStrategy = app.flywayMigrationStrategy(false);
        defaultStrategy.migrate(flyway);
        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }
}
