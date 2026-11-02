package com.eneik.production.services.worker.worktree;

import com.eneik.production.models.persistence.TaskEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Joseph Raz's Deontic Exclusionary Reason Enforcer (D006: PROHIBITION_AS_CODE).
 * Physical pre-commit interceptor: audits 'git status --porcelain' before any commit.
 * Enforces F_actual ⊆ task.fileScope with instantaneous deterministic rollback.
 */
@Component
public class FileScopeEnforcer {

    private static final Logger log = LoggerFactory.getLogger(FileScopeEnforcer.class);
    private final ObjectMapper objectMapper;

    public FileScopeEnforcer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public FileScopeEnforcer() {
        this(new ObjectMapper());
    }

    /**
     * Parses declared scope from TaskEntity.fileScope (JSON array or delimited string).
     */
    public List<String> parseDeclaredScope(String rawScope) {
        if (rawScope == null || rawScope.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = rawScope.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<String> list = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                return list.stream().map(this::normalizePath).toList();
            } catch (Exception e) {
                log.warn("Failed to parse fileScope as JSON array, falling back to split: {}", rawScope);
            }
        }
        // Fallback: split by comma or newline
        String[] parts = trimmed.split("[,\\n]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String clean = part.trim().replace("\"", "").replace("'", "");
            if (!clean.isBlank()) {
                result.add(normalizePath(clean));
            }
        }
        return result;
    }

    /**
     * Checks if actual modified files are a subset of declared scope.
     * Returns list of violating files (empty if fully compliant).
     */
    public List<String> findViolations(List<String> actualModifiedFiles, List<String> declaredScope) {
        if (actualModifiedFiles == null || actualModifiedFiles.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> declaredSet = new HashSet<>(declaredScope.stream().map(this::normalizePath).toList());
        List<String> violations = new ArrayList<>();

        for (String actual : actualModifiedFiles) {
            String norm = normalizePath(actual);
            if (!isCovered(norm, declaredSet)) {
                violations.add(actual);
            }
        }
        return violations;
    }

    private boolean isCovered(String path, Set<String> declaredSet) {
        if (declaredSet.contains(path)) {
            return true;
        }
        // Check directory matching if declared scope contains directory path ending with / or /*
        for (String declared : declaredSet) {
            if (declared.endsWith("/*") && path.startsWith(declared.substring(0, declared.length() - 1))) {
                return true;
            }
            if (declared.endsWith("/") && path.startsWith(declared)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Audits the physical git worktree via 'git status --porcelain'.
     * If any unauthorized file was modified/created, executes 'git reset --hard' and rolls back.
     */
    public void enforce(Path worktreePath, TaskEntity task) {
        List<String> declaredScope = new ArrayList<>(parseDeclaredScope(task != null ? task.getFileScope() : null));
        if (task != null && (task.isWishlistCompiler() || task.isCarrier())) {
            declaredScope.add(".eneik/");
            declaredScope.add(".eneik/*");
            if (task.getPayload() != null && task.getPayload().has("taskPlanPath")) {
                declaredScope.add(normalizePath(task.getPayload().get("taskPlanPath").asText()));
            }
        }
        List<String> actualModifiedFiles = readGitStatusPorcelain(worktreePath);
        if (declaredScope.isEmpty()) {
            log.info("Task {} has no explicit fileScope; Raz gate permits product worktree modifications ({} files)",
                    task != null ? task.getId() : "unknown", actualModifiedFiles.size());
            return;
        }

        List<String> violations = findViolations(actualModifiedFiles, declaredScope);

        if (!violations.isEmpty()) {
            log.error("Raz Deontic Gate D006 BREACH: actual modifications {} exceed declared fileScope {}",
                    violations, declaredScope);
            // Instantaneous physical rollback
            rollbackWorktree(worktreePath);
            throw new ExclusionaryReasonViolationException(violations, declaredScope);
        }

        log.info("Raz Deontic Gate D006: fileScope verified clean ({} files in scope)", actualModifiedFiles.size());
    }

    public List<String> readGitStatusPorcelain(Path worktreePath) {
        List<String> files = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("git", "status", "--porcelain")
                    .directory(worktreePath.toFile())
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() >= 4) {
                        // porcelain format: "XY path" or "XY path -> newpath"
                        String rawPath = line.substring(3).trim();
                        if (rawPath.contains(" -> ")) {
                            rawPath = rawPath.substring(rawPath.indexOf(" -> ") + 4).trim();
                        }
                        if (rawPath.startsWith("\"") && rawPath.endsWith("\"")) {
                            rawPath = rawPath.substring(1, rawPath.length() - 1);
                        }
                        files.add(normalizePath(rawPath));
                    }
                }
            }
            process.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to execute git status --porcelain in {}: {}", worktreePath, e.getMessage());
            throw new RuntimeException("Failed to audit worktree git status: " + e.getMessage(), e);
        }
        return files;
    }

    public void rollbackWorktree(Path worktreePath) {
        try {
            log.warn("Executing physical rollback (git reset --hard && git clean -fd) in {}", worktreePath);
            Process resetProcess = new ProcessBuilder("git", "reset", "--hard")
                    .directory(worktreePath.toFile())
                    .start();
            resetProcess.waitFor(15, TimeUnit.SECONDS);

            Process cleanProcess = new ProcessBuilder("git", "clean", "-fd")
                    .directory(worktreePath.toFile())
                    .start();
            cleanProcess.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to rollback worktree {}: {}", worktreePath, e.getMessage());
        }
    }

    public String normalizePath(String path) {
        if (path == null) return "";
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}
