package com.eneik.production.controllers.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
public class SystemDriftController {

    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(5);

    private final Environment environment;
    private final String systemRepoRoot;

    public SystemDriftController(Environment environment,
                                 @Value("${eneik.operator.system-repo-root:}") String systemRepoRoot) {
        this.environment = environment;
        this.systemRepoRoot = systemRepoRoot;
    }

    @GetMapping("/api/system/drift")
    public Map<String, Object> drift() {
        return driftReport();
    }

    @GetMapping("/actuator/info")
    public Map<String, Object> actuatorInfo() {
        Map<String, Object> report = driftReport();
        Map<String, Object> build = new LinkedHashMap<>();
        build.put("gitSha", report.get("runtimeBuildRevision"));
        build.put("gitDirty", report.get("runtimeBuildDirty"));
        build.put("time", report.get("runtimeBuildTime"));
        return Map.of(
                "build", build,
                "runtimeSource", report
        );
    }

    private Map<String, Object> driftReport() {
        String buildRevision = env("ENEIK_BUILD_GIT_SHA", "unknown");
        String buildDirty = env("ENEIK_BUILD_GIT_DIRTY", "unknown");
        String buildTime = env("ENEIK_BUILD_TIME", "unknown");
        Path repoRoot = repoRoot();

        Optional<String> mountedHead = git(repoRoot, "rev-parse", "HEAD");
        Optional<String> remoteHead = git(repoRoot, "rev-parse", "origin/main");
        Optional<String> aheadBehind = git(repoRoot, "rev-list", "--left-right", "--count", "HEAD...origin/main");

        boolean runtimeKnown = !"unknown".equals(buildRevision);
        boolean runtimeMatchesMountedRepoHead = runtimeKnown
                && mountedHead.map(buildRevision::equals).orElse(false)
                && "false".equals(buildDirty);
        boolean remoteMatchesMountedRepoHead = mountedHead.isPresent()
                && remoteHead.map(mountedHead.get()::equals).orElse(false);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ok", runtimeMatchesMountedRepoHead);
        report.put("mountedRepoRoot", repoRoot.toString());
        report.put("mountedRepoExists", Files.isDirectory(repoRoot));
        report.put("mountedRepoHead", mountedHead.orElse("unknown"));
        report.put("remoteHead", remoteHead.orElse("unknown"));
        report.put("aheadBehind", aheadBehind.orElse("unknown"));
        report.put("runtimeBuildRevision", buildRevision);
        report.put("runtimeBuildDirty", buildDirty);
        report.put("runtimeBuildTime", buildTime);
        report.put("runtimeMatchesMountedRepoHead", runtimeMatchesMountedRepoHead);
        report.put("remoteMatchesMountedRepoHead", remoteMatchesMountedRepoHead);
        report.put("worktreeDirtySource", "host check_system_drift.ps1 remains authoritative for dirty worktree status");
        return report;
    }

    private String env(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private Path repoRoot() {
        if (systemRepoRoot != null && !systemRepoRoot.isBlank()) {
            return Path.of(systemRepoRoot);
        }
        return Path.of(".").toAbsolutePath().normalize();
    }

    private Optional<String> git(Path repoRoot, String... args) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command(args));
            processBuilder.directory(repoRoot.toFile());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output = process.inputReader().lines()
                    .collect(Collectors.joining("\n"))
                    .trim();
            if (process.exitValue() != 0 || output.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(output);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String[] command(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
    }
}
