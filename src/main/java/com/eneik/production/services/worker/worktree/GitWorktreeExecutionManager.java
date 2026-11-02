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
import java.util.concurrent.TimeUnit;

/**
 * Multi-Project Git Worktree and Bare Mirror Pool Manager (GEMINI.md §0, Frugal Engineering).
 * Operates ephemeral git worktrees with shared object store for zero disk overhead (<50MB/worktree)
 * and guarantees deterministic teardown in finally blocks.
 */
@Component
public class GitWorktreeExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(GitWorktreeExecutionManager.class);

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

    public Path getOrCreateBareMirror(ProjectEntity project) {
        Path mirrorsDir = resolveWorkableDirectory(configuredMirrorsBaseDir, "eneik-mirrors");
        String sanitizedName = sanitizeRepoName(project != null ? project.getName() : "default-repo") + ".git";
        Path mirrorPath = mirrorsDir.resolve(sanitizedName);

        if (!Files.exists(mirrorPath)) {
            try {
                Files.createDirectories(mirrorPath);
                String repoUrl = project != null ? project.getRepoUrl() : null;
                if (repoUrl != null && !repoUrl.isBlank()) {
                    String cloneUrl = repoUrl;
                    String token = resolveGitHubToken();
                    if (token != null && repoUrl.contains("github.com")) {
                        cloneUrl = formatAuthenticatedGitHubUrl(repoUrl, token);
                    }
                    log.info("Cloning bare mirror for {} from {} to {}", project.getName(), repoUrl, mirrorPath);
                    runCommand(mirrorsDir, 120, "git", "clone", "--mirror", cloneUrl, mirrorPath.toString());
                } else {
                    // Initialize empty bare git repo if no remote URL
                    runCommand(mirrorsDir, 30, "git", "init", "--bare", mirrorPath.toString());
                }
            } catch (Exception e) {
                log.warn("Failed to create bare mirror at {}: {}", mirrorPath, e.getMessage());
            }
        } else {
            // Prune and fetch updates
            try {
                log.info("Fetching origin --prune for bare mirror at {}", mirrorPath);
                runCommand(mirrorPath, 60, "git", "fetch", "origin", "--prune");
            } catch (Exception e) {
                log.warn("Fetch failed for mirror at {}: {}", mirrorPath, e.getMessage());
            }
        }
        return mirrorPath;
    }

    /**
     * Creates an isolated ephemeral worktree for a task.
     * Command: git worktree add -B worker/{taskId} {worktreePath} {startingBranch}
     */
    public Path createWorktree(UUID taskId, Path baseRepoPath, String startingBranch) {
        Path worktreeBase = resolveWorkableDirectory(configuredWorktreeBaseDir, "eneik-worktrees");
        Path worktreePath = worktreeBase.resolve("task-" + taskId);

        // Clean up any stale directory first
        if (Files.exists(worktreePath)) {
            removeWorktree(baseRepoPath, worktreePath);
        }

        String branch = (startingBranch != null && !startingBranch.isBlank()) ? startingBranch : "main";
        String workerBranch = "worker/" + taskId;

        log.info("Creating ephemeral git worktree at {} from base {} on branch {}", worktreePath, baseRepoPath, branch);

        try {
            // Check if starting branch exists; if not, try HEAD or origin/main
            List<String> cmd = new ArrayList<>(Arrays.asList("git", "worktree", "add", "-B", workerBranch, worktreePath.toString()));
            if (hasBranchOrRef(baseRepoPath, branch)) {
                cmd.add(branch);
            } else if (hasBranchOrRef(baseRepoPath, "origin/" + branch)) {
                cmd.add("origin/" + branch);
            } else {
                cmd.add("HEAD");
            }

            runCommand(baseRepoPath, 30, cmd.toArray(new String[0]));
        } catch (Exception e) {
            log.error("Failed to create git worktree at {}: {}", worktreePath, e.getMessage());
            throw new RuntimeException("Failed to create worktree: " + e.getMessage(), e);
        }

        return worktreePath;
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
            try {
                runCommand(baseRepoPath, 30, "git", "worktree", "remove", "--force", worktreePath.toString());
                runCommand(baseRepoPath, 30, "git", "worktree", "prune");
            } catch (Exception e) {
                log.warn("git worktree remove failed for {}: {}", worktreePath, e.getMessage());
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
        runCommand(worktreePath, 60, "git", "push", "--force", "-u", "origin", branch);
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
        try {
            Process process = new ProcessBuilder("git", "remote", "get-url", "origin")
                    .directory(repoPath.toFile())
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Command " + Arrays.toString(command) + " timed out after " + timeoutSeconds + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.debug("Command {} exited with code {}: {}", command[0], exitCode, output.toString().trim());
            }
            return exitCode;
        } catch (Exception e) {
            throw new RuntimeException("Execution error for " + Arrays.toString(command) + ": " + e.getMessage(), e);
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
