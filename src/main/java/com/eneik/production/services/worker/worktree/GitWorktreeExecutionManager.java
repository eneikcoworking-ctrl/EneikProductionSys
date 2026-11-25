package com.eneik.production.services.worker.worktree;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.worker.deepseek.PatchBlock;
import com.eneik.production.services.worker.deepseek.PatchOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Multi-Project Git Worktree and Bare Mirror Pool Manager (GEMINI.md §0, Frugal Engineering).
 * Operates ephemeral git worktrees with shared object store for zero disk overhead (<50MB/worktree)
 * and guarantees deterministic teardown in finally blocks.
 */
@Component
public class GitWorktreeExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(GitWorktreeExecutionManager.class);
    private static final String SAFE_ORIGIN_FETCH_REFSPEC = "+refs/heads/*:refs/remotes/origin/*";

    private final ConcurrentHashMap<Path, ReentrantLock> mirrorLocks = new ConcurrentHashMap<>();

    @Value("${factory.worktree.base-dir:/var/lib/eneik/worktrees}")
    private String configuredWorktreeBaseDir = "/var/lib/eneik/worktrees";

    @Value("${factory.mirrors.base-dir:/var/lib/eneik/mirrors}")
    private String configuredMirrorsBaseDir = "/var/lib/eneik/mirrors";

    @Value("${factory.orchestrator.repo-path:/opt/EneikProductionSys}")
    private String orchestratorRepoPath = "/opt/EneikProductionSys";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.services.settings.SystemSettingsService settingsService;

    public GitWorktreeExecutionManager() {}

    public GitWorktreeExecutionManager(String worktreeBaseDir, String mirrorsBaseDir, String orchestratorRepoPath) {
        this.configuredWorktreeBaseDir = worktreeBaseDir;
        this.configuredMirrorsBaseDir = mirrorsBaseDir;
        this.orchestratorRepoPath = orchestratorRepoPath;
    }

    public void setSettingsService(com.eneik.production.services.settings.SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Resolves the base repository path for the given project.
     * Factory system -> /opt/EneikProductionSys
     * Client project -> /opt/{repoName} or bare mirror in /var/lib/eneik/mirrors/{owner}__{repo}.git
     */
    public Path resolveBaseRepository(ProjectEntity project) {
        if (project == null || "ORCHESTRATOR_SYSTEM".equalsIgnoreCase(project.getName()) || "Factory".equalsIgnoreCase(project.getName())) {
            Path orchPath = Paths.get(orchestratorRepoPath);
            if (Files.exists(orchPath)) {
                return orchPath;
            }
            // Fallback for tests/local run: current directory
            return Paths.get(".").toAbsolutePath().normalize();
        }

        String repoName = project.getName();
        // Check if local clone exists in /opt/{repoName}
        Path localOptPath = Paths.get("/opt", repoName);
        if (Files.isDirectory(localOptPath) && Files.exists(localOptPath.resolve(".git"))) {
            return localOptPath;
        }

        // Use or initialize bare mirror in /var/lib/eneik/mirrors/{repoName}.git
        return getOrCreateBareMirror(project);
    }

    private ReentrantLock lockForMirror(Path repoPath) {
        Path key = repoPath != null ? repoPath.toAbsolutePath().normalize() : Paths.get(".").toAbsolutePath().normalize();
        return mirrorLocks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    public Path getOrCreateBareMirror(ProjectEntity project) {
        Path mirrorsDir = resolveWorkableDirectory(configuredMirrorsBaseDir, "eneik-mirrors");
        String sanitizedName = sanitizeRepoName(project != null ? project.getName() : "default-repo") + ".git";
        Path mirrorPath = mirrorsDir.resolve(sanitizedName);

        ReentrantLock lock = lockForMirror(mirrorPath);
        lock.lock();
        try {
            if (!Files.exists(mirrorPath)) {
                try {
                    initializeBareMirror(mirrorsDir, mirrorPath, project);
                } catch (Exception e) {
                    log.warn("Failed to create bare mirror at {}: {}", mirrorPath, e.getMessage());
                }
            } else {
                // D013/D010 (Mechanism #13, 4A): Never run `--mirror` prune over local refs/heads/worker/*.
                // Enforce safe remote-tracking refspec (+refs/heads/*:refs/remotes/origin/*) under per-mirror lock.
                try {
                    ensureSafeOriginFetchRefspec(mirrorPath);
                    log.info("Fetching origin --prune with safe refspec for bare mirror at {}", mirrorPath);
                    runCommand(mirrorPath, 60, "git", "fetch", "origin", SAFE_ORIGIN_FETCH_REFSPEC, "--prune");
                } catch (Exception e) {
                    log.warn("Fetch failed for mirror at {}: {}", mirrorPath, e.getMessage());
                }
            }
            return mirrorPath;
        } finally {
            lock.unlock();
        }
    }

    /**
     * D013/D010 (Mechanism #13, 4A): Force-resyncs bare mirror with origin/<defaultBranch> under per-mirror lock.
     * If the mirror is corrupted or history diverged, safely reinitializes the bare mirror.
     */
    public boolean resyncMirrorWithOriginMain(ProjectEntity project) {
        if (project == null) {
            return false;
        }
        Path baseRepo = resolveMirrorPathWithoutFetch(project);
        ReentrantLock lock = lockForMirror(baseRepo);
        lock.lock();
        try {
            String defaultBranch = (project.getDefaultBranch() != null && !project.getDefaultBranch().isBlank())
                    ? project.getDefaultBranch().trim()
                    : "main";
            log.warn("Mechanism #13 (4A): Resyncing mirror {} with origin/{}", baseRepo, defaultBranch);

            if (Files.exists(baseRepo)) {
                ensureSafeOriginFetchRefspec(baseRepo);
                int fetchExit = runCommand(baseRepo, 60, "git", "fetch", "--force", "origin", SAFE_ORIGIN_FETCH_REFSPEC, "--prune");
                if (fetchExit == 0 && hasBranchOrRef(baseRepo, "refs/remotes/origin/" + defaultBranch)) {
                    // Best-effort fast-forward refs/heads/<defaultBranch> to match origin/<defaultBranch>
                    runCommand(baseRepo, 15, "git", "update-ref", "refs/heads/" + defaultBranch, "refs/remotes/origin/" + defaultBranch);
                    log.info("Resynced mirror {} successfully to origin/{}", baseRepo, defaultBranch);
                    return true;
                }
                // If bare mirror is damaged and we have a repoUrl, safely reinitialize it
                if (project.getRepoUrl() != null && !project.getRepoUrl().isBlank() && isBareMirrorDirectory(baseRepo)) {
                    log.warn("Mirror {} fetch failed (exit={}); reinitializing bare mirror from {}", baseRepo, fetchExit, project.getRepoUrl());
                    deleteRecursively(baseRepo.toFile());
                    initializeBareMirror(baseRepo.getParent(), baseRepo, project);
                    return hasBranchOrRef(baseRepo, "refs/remotes/origin/" + defaultBranch)
                            || hasBranchOrRef(baseRepo, defaultBranch);
                }
                return fetchExit == 0;
            } else {
                initializeBareMirror(baseRepo.getParent(), baseRepo, project);
                return hasBranchOrRef(baseRepo, "refs/remotes/origin/" + defaultBranch)
                        || hasBranchOrRef(baseRepo, defaultBranch);
            }
        } catch (Exception e) {
            log.error("resyncMirrorWithOriginMain failed for project {}: {}", project.getName(), e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
    }

    private Path resolveMirrorPathWithoutFetch(ProjectEntity project) {
        Path mirrorsDir = resolveWorkableDirectory(configuredMirrorsBaseDir, "eneik-mirrors");
        String sanitizedName = sanitizeRepoName(project.getName() != null ? project.getName() : "default-repo") + ".git";
        return mirrorsDir.resolve(sanitizedName);
    }

    private boolean isBareMirrorDirectory(Path path) {
        return path != null && path.getFileName() != null && path.getFileName().toString().endsWith(".git")
                && !Files.exists(path.resolve(".git"));
    }

    private void initializeBareMirror(Path mirrorsDir, Path mirrorPath, ProjectEntity project) throws IOException {
        if (mirrorsDir != null) {
            Files.createDirectories(mirrorsDir);
        }
        String repoUrl = project != null ? project.getRepoUrl() : null;
        if (repoUrl != null && !repoUrl.isBlank()) {
            String cloneUrl = repoUrl;
            String token = resolveGitHubToken();
            if (token != null && repoUrl.contains("github.com")) {
                cloneUrl = formatAuthenticatedGitHubUrl(repoUrl, token);
            }
            log.info("Cloning bare repo for {} from {} to {}", project.getName(), repoUrl, mirrorPath);
            int cloneExit = runCommand(mirrorsDir, 120, "git", "clone", "--bare", cloneUrl, mirrorPath.toString());
            if (cloneExit == 0 && Files.exists(mirrorPath)) {
                ensureSafeOriginFetchRefspec(mirrorPath);
                runCommand(mirrorPath, 60, "git", "fetch", "origin", SAFE_ORIGIN_FETCH_REFSPEC, "--prune");
            }
        } else {
            runCommand(mirrorsDir, 30, "git", "init", "--bare", mirrorPath.toString());
        }
    }

    private void ensureSafeOriginFetchRefspec(Path repoPath) {
        if (repoPath == null || !Files.exists(repoPath)) {
            return;
        }
        // Remove mirror=true if present from legacy `--mirror` clones so `git fetch --prune`
        // never deletes local refs/heads/worker/* branches out from under active worktrees.
        runCommand(repoPath, 10, "git", "config", "--unset-all", "remote.origin.mirror");
        runCommand(repoPath, 10, "git", "config", "remote.origin.fetch", SAFE_ORIGIN_FETCH_REFSPEC);
    }

    /**
     * Creates an isolated ephemeral worktree for a task.
     * Command: git worktree add -B worker/{taskId} {worktreePath} {origin/startingBranch | startingBranch}
     */
    public Path createWorktree(UUID taskId, Path baseRepoPath, String startingBranch) {
        Path worktreeBase = resolveWorkableDirectory(configuredWorktreeBaseDir, "eneik-worktrees");
        Path worktreePath = worktreeBase.resolve("task-" + taskId);

        ReentrantLock lock = lockForMirror(baseRepoPath);
        lock.lock();
        try {
            // Clean up any stale directory first
            if (Files.exists(worktreePath)) {
                removeWorktree(baseRepoPath, worktreePath);
            }

            String branch = (startingBranch != null && !startingBranch.isBlank()) ? startingBranch : "main";
            String workerBranch = "worker/" + taskId;

            log.info("Creating ephemeral git worktree at {} from base {} on branch {}", worktreePath, baseRepoPath, branch);

            try {
                // Prefer freshly-fetched origin/<branch>, then local <branch>, then origin/main, then main, then HEAD
                List<String> cmd = new ArrayList<>(Arrays.asList("git", "worktree", "add", "-B", workerBranch, worktreePath.toString()));
                if (hasBranchOrRef(baseRepoPath, "refs/remotes/origin/" + branch)) {
                    cmd.add("refs/remotes/origin/" + branch);
                } else if (hasBranchOrRef(baseRepoPath, branch)) {
                    cmd.add(branch);
                } else if (hasBranchOrRef(baseRepoPath, "refs/remotes/origin/main")) {
                    cmd.add("refs/remotes/origin/main");
                } else if (hasBranchOrRef(baseRepoPath, "main")) {
                    cmd.add("main");
                } else {
                    cmd.add("HEAD");
                }

                int exit = runCommand(baseRepoPath, 30, cmd.toArray(new String[0]));
                if (exit != 0) {
                    throw new RuntimeException("git worktree add exited with code " + exit);
                }
            } catch (Exception e) {
                log.error("Failed to create git worktree at {}: {}", worktreePath, e.getMessage());
                throw new RuntimeException("Failed to create worktree: " + e.getMessage(), e);
            }

            return worktreePath;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Applies surgical modifications to files in the worktree.
     */
    public void applyPatches(Path worktreePath, List<PatchOperation> operations) throws IOException {
        if (operations == null || operations.isEmpty()) {
            return;
        }

        for (PatchOperation op : operations) {
            Path targetFile = worktreePath.resolve(normalizeRelativePath(op.path()));

            if (op.isDelete()) {
                Files.deleteIfExists(targetFile);
                log.info("Deleted file: {}", targetFile);
                continue;
            }

            if (op.isCreate() || op.isFullContent()) {
                if (targetFile.getParent() != null) {
                    Files.createDirectories(targetFile.getParent());
                }
                Files.writeString(targetFile, op.content() != null ? op.content() : "", StandardCharsets.UTF_8);
                log.info("Wrote full content to file ({} bytes): {}", op.content() != null ? op.content().length() : 0, targetFile);
            } else if (op.isPatchBlocks()) {
                if (!Files.exists(targetFile)) {
                    throw new IOException("Cannot apply PATCH_BLOCKS: file does not exist: " + targetFile);
                }
                String fileContent = Files.readString(targetFile, StandardCharsets.UTF_8);
                for (PatchBlock block : op.patchBlocks()) {
                    String search = block.search();
                    String replace = block.replace();
                    if (!fileContent.contains(search)) {
                        throw new IllegalArgumentException("Search block not found in file '" + op.path() + "': [" + search + "]");
                    }
                    fileContent = fileContent.replace(search, replace);
                }
                Files.writeString(targetFile, fileContent, StandardCharsets.UTF_8);
                log.info("Applied {} patch blocks to file: {}", op.patchBlocks().size(), targetFile);
            }
        }
    }

    /**
     * Guaranteed teardown of ephemeral worktree in finally block.
     */
    public void removeWorktree(Path baseRepoPath, Path worktreePath) {
        if (worktreePath == null) {
            return;
        }

        log.info("Tearing down git worktree at {}", worktreePath);
        if (baseRepoPath != null && Files.exists(baseRepoPath)) {
            ReentrantLock lock = lockForMirror(baseRepoPath);
            lock.lock();
            try {
                runCommand(baseRepoPath, 30, "git", "worktree", "remove", "--force", worktreePath.toString());
                runCommand(baseRepoPath, 30, "git", "worktree", "prune");
            } catch (Exception e) {
                log.warn("git worktree remove failed for {}: {}", worktreePath, e.getMessage());
            } finally {
                lock.unlock();
            }
        }

        // Physical folder cleanup if git worktree remove left artifacts
        if (Files.exists(worktreePath)) {
            deleteRecursively(worktreePath.toFile());
        }
    }

    /**
     * Reads contents of files in scope from worktree to provide context to LLM.
     */
    public List<String> listRepositoryFiles(Path worktreePath) {
        if (worktreePath == null || !Files.exists(worktreePath)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        Process process = null;
        try {
            process = new ProcessBuilder("git", "ls-files")
                    .directory(worktreePath.toFile())
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String p = line.trim();
                    if (p.isEmpty()) continue;
                    if (p.startsWith("target/") || p.startsWith("node_modules/") || p.startsWith(".eneik/")
                            || p.startsWith(".git/") || p.startsWith(".m2-cache/") || p.endsWith(".class") || p.endsWith(".png")) {
                        continue;
                    }
                    files.add(p);
                }
            }
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("git ls-files timed out after 15s in {}; forcing termination", worktreePath);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to list repository files in {}: {}", worktreePath, e.getMessage());
        } finally {
            reapProcessQuietly(process);
        }
        return files;
    }

    public String readScopedFiles(Path worktreePath, List<String> relativePaths) {
        if (relativePaths == null || relativePaths.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String rel : relativePaths) {
            Path file = worktreePath.resolve(normalizeRelativePath(rel));
            if (Files.exists(file) && Files.isRegularFile(file)) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    sb.append("--- FILE: ").append(rel).append(" ---\n");
                    sb.append(content).append("\n\n");
                } catch (Exception e) {
                    log.warn("Failed to read file {}: {}", file, e.getMessage());
                }
            }
        }
        return sb.toString();
    }

    public void commit(Path worktreePath, String commitMessage) {
        runCommand(worktreePath, 30, "git", "add", "-f", ".");
        int exit = runCommand(worktreePath, 30,
                "git", "-c", "user.name=Eneik Sovereign Worker",
                "-c", "user.email=eneikdru@gmail.com",
                "commit", "-m", commitMessage);
        if (exit != 0) {
            throw new RuntimeException("git commit failed with exit code " + exit + " in " + worktreePath);
        }
    }

    /**
     * D013/D010 (Mechanism #13, 4A): Verifies that HEAD in the worktree shares a real common ancestor
     * with origin/<baseBranch> (or <baseBranch>), ruling out orphan commits (parents=[]) that trigger
     * GitHub HTTP 422 "no history in common with main".
     */
    public boolean hasCommonHistoryWithBase(Path worktreePath, String baseBranch) {
        if (worktreePath == null || !Files.exists(worktreePath)) {
            return false;
        }
        String branch = (baseBranch != null && !baseBranch.isBlank()) ? baseBranch.trim() : "main";
        String baseRef = null;
        if (hasBranchOrRef(worktreePath, "refs/remotes/origin/" + branch)) {
            baseRef = "refs/remotes/origin/" + branch;
        } else if (hasBranchOrRef(worktreePath, branch)) {
            baseRef = branch;
        } else if (hasBranchOrRef(worktreePath, "refs/remotes/origin/main")) {
            baseRef = "refs/remotes/origin/main";
        } else if (hasBranchOrRef(worktreePath, "main")) {
            baseRef = "main";
        }
        if (baseRef == null) {
            return false;
        }
        try {
            int exit = runCommand(worktreePath, 15, "git", "merge-base", "HEAD", baseRef);
            return exit == 0;
        } catch (Exception e) {
            log.warn("Failed to check merge-base between HEAD and {} in {}: {}", baseRef, worktreePath, e.getMessage());
            return false;
        }
    }

    /**
     * Throws IllegalStateException if HEAD in the worktree has no common ancestor with baseBranch.
     */
    public void verifyCommonHistoryWithBase(Path worktreePath, String baseBranch) {
        if (!hasCommonHistoryWithBase(worktreePath, baseBranch)) {
            throw new IllegalStateException(
                    "ORPHAN_COMMIT_NO_COMMON_HISTORY: worktree HEAD at " + worktreePath
                            + " has no common ancestor with origin/" + (baseBranch != null ? baseBranch : "main"));
        }
    }

    public void push(Path worktreePath, String branch) {
        String token = resolveGitHubToken();
        if (token != null && !token.isBlank()) {
            try {
                String originUrl = getRemoteOriginUrl(worktreePath);
                if (originUrl != null && originUrl.contains("github.com")) {
                    String authenticatedUrl = formatAuthenticatedGitHubUrl(originUrl, token);
                    log.info("Pushing branch {} with authenticated GitHub token", branch);
                    int exit = runCommand(worktreePath, 60, "git", "push", "--force", "-u", authenticatedUrl, branch);
                    if (exit == 0) {
                        return;
                    }
                    log.warn("Authenticated git push exited with code {}, retrying with standard origin remote", exit);
                }
            } catch (Exception e) {
                log.warn("Authenticated git push failed, falling back to standard push: {}", e.getMessage());
            }
        }
        int exit = runCommand(worktreePath, 60, "git", "push", "--force", "-u", "origin", branch);
        if (exit != 0) {
            throw new RuntimeException("git push failed with exit code " + exit + " for branch " + branch);
        }
    }

    public String resolveGitHubToken() {
        if (settingsService != null && settingsService.isKnownKey("github_token")) {
            String dbToken = settingsService.effectiveValue("github_token");
            if (dbToken != null && !dbToken.isBlank()) {
                return dbToken.trim();
            }
        }
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        return null;
    }

    public String getRemoteOriginUrl(Path repoPath) {
        Process process = null;
        try {
            process = new ProcessBuilder("git", "remote", "get-url", "origin")
                    .directory(repoPath.toFile())
                    .redirectErrorStream(true)
                    .start();
            String url = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    url = line.trim();
                }
            }
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return null;
            }
            return process.exitValue() == 0 ? url : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            reapProcessQuietly(process);
        }
    }

    public String formatAuthenticatedGitHubUrl(String rawUrl, String token) {
        if (rawUrl == null || token == null || token.isBlank()) {
            return rawUrl;
        }
        String cleanUrl = rawUrl.trim();
        // Handle git@github.com:owner/repo.git or https://github.com/owner/repo(.git)
        String repoPath;
        if (cleanUrl.startsWith("git@github.com:")) {
            repoPath = cleanUrl.substring("git@github.com:".length());
        } else if (cleanUrl.contains("github.com/")) {
            repoPath = cleanUrl.substring(cleanUrl.indexOf("github.com/") + "github.com/".length());
        } else {
            return rawUrl;
        }
        // Strip any embedded credentials
        if (repoPath.contains("@")) {
            repoPath = repoPath.substring(repoPath.indexOf("@") + 1);
        }
        if (!repoPath.endsWith(".git")) {
            repoPath = repoPath + ".git";
        }
        return "https://x-access-token:" + token.trim() + "@github.com/" + repoPath;
    }

    private boolean hasBranchOrRef(Path repoPath, String refName) {
        try {
            int exit = runCommand(repoPath, 10, "git", "rev-parse", "--verify", refName);
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    int runCommand(Path workingDir, int timeoutSeconds, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new RuntimeException("Command " + Arrays.toString(command) + " timed out after " + timeoutSeconds + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.debug("Command {} exited with code {}: {}", command[0], exitCode, output.toString().trim());
            }
            return exitCode;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Execution error for " + Arrays.toString(command) + ": " + e.getMessage(), e);
        } finally {
            reapProcessQuietly(process);
        }
    }

    private void reapProcessQuietly(Process process) {
        if (process == null) {
            return;
        }
        try {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    Path resolveWorkableDirectory(String preferredPath, String fallbackFolderName) {
        Path preferred = Paths.get(preferredPath);
        try {
            if (!Files.exists(preferred)) {
                Files.createDirectories(preferred);
            }
            // Test write permission
            Path testFile = preferred.resolve(".write-test-" + UUID.randomUUID());
            Files.createFile(testFile);
            Files.deleteIfExists(testFile);
            return preferred;
        } catch (Exception e) {
            Path tmpFallback = Paths.get(System.getProperty("java.io.tmpdir"), fallbackFolderName);
            try {
                Files.createDirectories(tmpFallback);
                return tmpFallback;
            } catch (Exception ex) {
                return Paths.get(System.getProperty("java.io.tmpdir"));
            }
        }
    }

    private String normalizeRelativePath(String path) {
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    private String sanitizeRepoName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
