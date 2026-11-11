package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.worker.verification.LocalVerificationRunner;
import com.eneik.production.services.worker.verification.SimulationStubDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LocalVerificationRunnerResourceTest {

    private final LocalVerificationRunner runner = new LocalVerificationRunner();

    @Test
    @DisplayName("Host Memory Invariant: Semaphore(1) strictly forbids concurrent compiler runs on host")
    void testHostSemaphoreIsolation() throws InterruptedException {
        assertEquals(1, LocalVerificationRunner.HOST_VERIFICATION_SEMAPHORE.availablePermits());

        CountDownLatch acquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        AtomicBoolean secondThreadBlocked = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            try {
                LocalVerificationRunner.HOST_VERIFICATION_SEMAPHORE.acquire();
                acquiredLatch.countDown();
                releaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            } finally {
                LocalVerificationRunner.HOST_VERIFICATION_SEMAPHORE.release();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                acquiredLatch.await(2, TimeUnit.SECONDS);
                boolean ok = LocalVerificationRunner.HOST_VERIFICATION_SEMAPHORE.tryAcquire(200, TimeUnit.MILLISECONDS);
                if (!ok) {
                    secondThreadBlocked.set(true);
                } else {
                    LocalVerificationRunner.HOST_VERIFICATION_SEMAPHORE.release();
                }
            } catch (InterruptedException ignored) {}
        });

        t1.start();
        t2.start();

        t2.join(3000);
        releaseLatch.countDown();
        t1.join(3000);

        assertTrue(secondThreadBlocked.get(), "Second thread must be blocked by Semaphore(1)");
        assertEquals(1, LocalVerificationRunner.HOST_VERIFICATION_SEMAPHORE.availablePermits());
    }

    @Test
    @DisplayName("Blind Arbiter: derives deterministic check commands from repository files, ignoring LLM whim")
    void testBlindArbiterDerivesCommand(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("pom.xml"));

        TaskEntity task = new TaskEntity();
        task.setFileScope("[\"src/main/A.java\", \"src/test/ATest.java\"]");

        List<String> cmd = runner.resolveArbiterCommand(tempDir, task);
        assertTrue(cmd.contains("mvn"));
        assertTrue(cmd.contains("test-compile"));
        assertTrue(cmd.stream().anyMatch(s -> s.contains("-Dtest=ATest")));
    }

    @Test
    @DisplayName("Docker cgroup resource limits: command includes --memory=1200m --cpus=1.5")
    void testDockerResourceLimits(@TempDir Path tempDir) {
        List<String> tokens = runner.buildDockerCommandTokens(tempDir, List.of("mvn", "test-compile"));
        assertTrue(tokens.contains("--memory=1200m"), "Must enforce 1.2 GiB RAM cgroup limit");
        assertTrue(tokens.contains("--cpus=1.5"), "Must enforce 1.5 CPU cgroup limit");
    }

    @Test
    @DisplayName("Carrier or WishlistCompiler tasks skip Maven verification and return success immediately")
    void testCarrierOrCompilerTaskSkipsVerification(@TempDir Path tempDir) {
        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setId(java.util.UUID.randomUUID());
        carrierTask.setFileScope("[\".eneik/records/task-plan-123.json\"]");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.createObjectNode();
        payload.put(TaskEntity.CARRIER_PAYLOAD_KEY, TaskEntity.WISHLIST_COMPILER_TASK_TYPE);
        carrierTask.setPayload(payload);

        assertTrue(carrierTask.isCarrier());
        assertTrue(carrierTask.isWishlistCompiler());

        var result = runner.runVerification(tempDir, carrierTask);
        assertTrue(result.passed());
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Carrier/compiler task"));
    }

    @Test
    @DisplayName("Goldman Level of Abstraction Lock (D010): Rejects task introducing 1x1 pixel or cardboard stub")
    void testGoldmanD010RejectsStubInWorktree(@TempDir Path tempDir) throws IOException {
        Path mockFile = tempDir.resolve("design/mockup.html");
        Files.createDirectories(mockFile.getParent());
        Files.writeString(mockFile, "<img src=\"https://assets.eneik.com/default-hero.webp\"><button>Explore Dashboard</button>", StandardCharsets.UTF_8);

        TaskEntity task = new TaskEntity();
        task.setId(java.util.UUID.randomUUID());
        task.setFileScope("[\"design/mockup.html\"]");

        var result = runner.auditWorktreeAgainstSimulacra(tempDir, task);
        assertFalse(result.passed());
        assertEquals(101, result.exitCode());
        assertTrue(result.errorSignature().contains("D010_SIMULACRA_DETECTED"));
        assertTrue(result.output().contains("Dead placeholder domain"));
    }

    @Test
    @DisplayName("Goldman Level of Abstraction Lock (D010): Automatically compiles and synchronizes Svelte frontend to static/index.html")
    void testGoldmanD010LevelOfAbstractionLockCompilesSvelteToStaticIndexHtml(@TempDir Path tempDir) throws IOException {
        Path svelteFile = tempDir.resolve("frontend/src/App.svelte");
        Files.createDirectories(svelteFile.getParent());
        String svelteContent = """
                <script>
                  const specialties = [
                    { id: '31.08.36', label: '31.08.36 Эпидемиология' },
                    { id: '31.08.35', label: '31.08.35 Инфекционные болезни' },
                    { id: '31.08.19', label: '31.08.19 Педиатрия' }
                  ];
                </script>
                <main><h1>ЦНИИ Эпидемиологии</h1></main>
                """;
        Files.writeString(svelteFile, svelteContent, StandardCharsets.UTF_8);

        Path frontendIndex = tempDir.resolve("frontend/index.html");
        String frontendHtml = """
                <!DOCTYPE html>
                <html>
                <head><title>ЦНИИЭ База Знаний</title></head>
                <body>
                  <div id="app">Specialties: 31.08.36 Эпидемиология, 31.08.35 Инфекционные болезни</div>
                </body>
                </html>
                """;
        Files.writeString(frontendIndex, frontendHtml, StandardCharsets.UTF_8);

        Path staleStaticIndex = tempDir.resolve("src/main/resources/static/index.html");
        Files.createDirectories(staleStaticIndex.getParent());
        Files.writeString(staleStaticIndex, "<h2>Сводный пакет институциональных документов (2026–2027)</h2>", StandardCharsets.UTF_8);

        // Execute conjugated build step
        boolean synced = runner.compileAndSyncFrontendToStatic(tempDir);
        assertTrue(synced, "Must successfully compile and synchronize Svelte frontend");

        String updatedStatic = Files.readString(staleStaticIndex, StandardCharsets.UTF_8);
        assertTrue(updatedStatic.contains("31.08.36"), "Static index.html must now contain authentic specialty 31.08.36");
        assertFalse(updatedStatic.contains("Сводный пакет институциональных документов"), "Stale mock must be eliminated");

        // Verify abstraction lock check passes
        var abstractionLockResult = runner.verifyLevelOfAbstractionLock(tempDir);
        assertTrue(abstractionLockResult.passed(), "Abstraction lock must pass once synchronized");
    }

    @Test
    @DisplayName("Mechanism #8 (D010, D002, D003, D007): Eliminates DeepSeek branding, document counter and ensures session persistence")
    void testMechanism8InstitutionalPurityAndSessionContinuity(@TempDir Path tempDir) throws IOException {
        Path svelteFile = tempDir.resolve("frontend/src/App.svelte");
        Files.createDirectories(svelteFile.getParent());
        String svelteContent = """
                <script>
                  const specialties = [{ id: '31.08.36', label: 'Эпидемиология' }];
                  let chatMessages = [];
                </script>
                <main><h1>ЦНИИЭ Консультант на DeepSeek-V3</h1><span>1713 документов</span></main>
                """;
        Files.writeString(svelteFile, svelteContent, StandardCharsets.UTF_8);

        Path frontendIndex = tempDir.resolve("frontend/index.html");
        String frontendHtml = """
                <!DOCTYPE html>
                <html>
                <head><title>ЦНИИЭ</title></head>
                <body>
                  <div id="app">Specialties: 31.08.36 Эпидемиология. Консультант на DeepSeek-V3. 1713 документов в базе.</div>
                  <a href="/archive.zip">Скачать архив (15 документов)</a>
                </body>
                </html>
                """;
        Files.writeString(frontendIndex, frontendHtml, StandardCharsets.UTF_8);

        boolean synced = runner.compileAndSyncFrontendToStatic(tempDir);
        assertTrue(synced, "Must successfully synchronize and cleanse frontend");

        Path staticIndex = tempDir.resolve("src/main/resources/static/index.html");
        String updatedStatic = Files.readString(staticIndex, StandardCharsets.UTF_8);

        // 1. D010: Zero DeepSeek mentions
        assertFalse(updatedStatic.contains("DeepSeek"), "Must cleanse all DeepSeek model branding");
        assertTrue(updatedStatic.contains("Учебно-методического управления ФБУН ЦНИИ Эпидемиологии"), "Must use institutional branding");

        // 2. D010: Zero 1713 document counter
        assertFalse(updatedStatic.contains("1713 документов"), "Must eliminate hardcoded file counters");

        // 3. D002: Session persistence injected
        assertTrue(updatedStatic.contains("localStorage"), "Must inject session persistence for chat");

        // 4. D003 / D007: Dynamic export endpoints
        assertTrue(updatedStatic.contains("/api/generate/"), "Must wire dynamic generation endpoints");

        // Abstraction lock passes
        var lockResult = runner.verifyLevelOfAbstractionLock(tempDir);
        assertTrue(lockResult.passed(), "Abstraction lock must pass with institutional purity");
    }
}
