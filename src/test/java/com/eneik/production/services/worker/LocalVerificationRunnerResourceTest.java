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
}
