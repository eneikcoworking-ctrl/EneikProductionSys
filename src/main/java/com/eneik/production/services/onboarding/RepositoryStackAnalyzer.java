package com.eneik.production.services.onboarding;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class RepositoryStackAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(RepositoryStackAnalyzer.class);

    private final String organization;
    private final String apiBaseUrl;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public record FileEntry(String path, long size, String sha) {}
    public record AnalysisResult(StackProfile profile, List<FileEntry> filesToScan) {}

    public RepositoryStackAnalyzer(
            @Value("${github.org}") String organization,
            @Value("${github.api-base-url:https://api.github.com}") String apiBaseUrl,
            SystemSettingsService settingsService,
            ObjectMapper objectMapper) {
        this.organization = organization;
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public AnalysisResult analyze(String repositoryName) {
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            log.warn("GitHub token not configured, returning empty StackProfile");
            StackProfile empty = new StackProfile("Unknown", "None", "None", false, false, false,
                    "GitHub token not configured.", "main", "", 0, 0);
            return new AnalysisResult(empty, Collections.emptyList());
        }

        try {
            // 1. Get default branch
            String repoInfoUrl = apiBaseUrl + "/repos/" + organization + "/" + repositoryName;
            String defaultBranch = "main";
            HttpRequest repoRequest = createGetRequest(repoInfoUrl, token);
            HttpResponse<String> repoResponse = httpClient.send(repoRequest, HttpResponse.BodyHandlers.ofString());
            if (repoResponse.statusCode() == 200) {
                JsonNode repoJson = objectMapper.readTree(repoResponse.body());
                if (repoJson.has("default_branch")) {
                    defaultBranch = repoJson.path("default_branch").asText("main");
                }
            } else {
                log.warn("Failed to fetch repository default branch, status={}", repoResponse.statusCode());
            }

            // 2. Get baseline commit SHA
            String branchInfoUrl = apiBaseUrl + "/repos/" + organization + "/" + repositoryName + "/branches/" + defaultBranch;
            String baselineCommitSha = "";
            HttpRequest branchRequest = createGetRequest(branchInfoUrl, token);
            HttpResponse<String> branchResponse = httpClient.send(branchRequest, HttpResponse.BodyHandlers.ofString());
            if (branchResponse.statusCode() == 200) {
                JsonNode branchJson = objectMapper.readTree(branchResponse.body());
                baselineCommitSha = branchJson.path("commit").path("sha").asText("");
            } else {
                log.warn("Failed to fetch branch info for {}, status={}", defaultBranch, branchResponse.statusCode());
                // Fallback to commits API
                String commitsUrl = apiBaseUrl + "/repos/" + organization + "/" + repositoryName + "/commits?sha=" + defaultBranch + "&per_page=1";
                HttpRequest commitsRequest = createGetRequest(commitsUrl, token);
                HttpResponse<String> commitsResponse = httpClient.send(commitsRequest, HttpResponse.BodyHandlers.ofString());
                if (commitsResponse.statusCode() == 200) {
                    JsonNode commitsJson = objectMapper.readTree(commitsResponse.body());
                    if (commitsJson.isArray() && commitsJson.size() > 0) {
                        baselineCommitSha = commitsJson.get(0).path("sha").asText("");
                    }
                }
            }

            // 3. Get recursive tree
            String treeUrl = apiBaseUrl + "/repos/" + organization + "/" + repositoryName + "/git/trees/" + defaultBranch + "?recursive=1";
            HttpRequest treeRequest = createGetRequest(treeUrl, token);
            HttpResponse<String> treeResponse = httpClient.send(treeRequest, HttpResponse.BodyHandlers.ofString());
            if (treeResponse.statusCode() != 200) {
                log.warn("Failed to get recursive tree: HTTP {}", treeResponse.statusCode());
                StackProfile empty = new StackProfile("Unknown", "None", "None", false, false, false,
                        "Could not fetch tree from GitHub.", defaultBranch, baselineCommitSha, 0, 0);
                return new AnalysisResult(empty, Collections.emptyList());
            }

            JsonNode treeJson = objectMapper.readTree(treeResponse.body());
            JsonNode treeNodes = treeJson.path("tree");
            List<FileEntry> allFiles = new ArrayList<>();
            for (JsonNode node : treeNodes) {
                if ("blob".equals(node.path("type").asText())) {
                    String path = node.path("path").asText();
                    long size = node.path("size").asLong(0);
                    String sha = node.path("sha").asText();
                    allFiles.add(new FileEntry(path, size, sha));
                }
            }

            int totalFiles = allFiles.size();

            // 4. Parse .gitignore first
            List<String> gitignorePatterns = new ArrayList<>();
            Optional<FileEntry> gitignoreEntry = allFiles.stream()
                    .filter(f -> f.path().equals(".gitignore"))
                    .findFirst();
            if (gitignoreEntry.isPresent()) {
                String gitignoreContent = fetchFileContent(repositoryName, ".gitignore", token);
                if (!gitignoreContent.isBlank()) {
                    for (String line : gitignoreContent.split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            gitignorePatterns.add(trimmed);
                        }
                    }
                }
            }

            // Always exclude node_modules/, vendor/, dist/, build/, .git/
            List<String> standardExclusions = List.of("node_modules", "vendor", "dist", "build", ".git");

            // Filter files
            List<FileEntry> filteredFiles = new ArrayList<>();
            for (FileEntry file : allFiles) {
                String path = file.path();
                boolean excluded = false;
                for (String excl : standardExclusions) {
                    if (path.contains(excl + "/") || path.equals(excl) || path.startsWith(excl + "/")) {
                        excluded = true;
                        break;
                    }
                }
                if (excluded) continue;

                // Match gitignore patterns
                for (String pat : gitignorePatterns) {
                    if (matchesPattern(path, pat)) {
                        excluded = true;
                        break;
                    }
                }
                if (excluded) continue;

                filteredFiles.add(file);
            }

            // Sort and prioritize up to 500 files
            filteredFiles.sort(Comparator.comparingInt(f -> fileScore(f.path())));
            List<FileEntry> filesToScan = filteredFiles.size() > 500 ? filteredFiles.subList(0, 500) : filteredFiles;
            int analyzedFiles = filesToScan.size();

            // Build tech stack markers set
            Set<String> fileNames = new HashSet<>();
            for (FileEntry f : filesToScan) {
                fileNames.add(f.path().substring(f.path().lastIndexOf('/') + 1));
            }
            // Check full paths for some root files
            Set<String> rootFilePaths = new HashSet<>();
            for (FileEntry f : filesToScan) {
                if (!f.path().contains("/")) {
                    rootFilePaths.add(f.path());
                }
            }

            String primaryLanguage = "Unknown";
            String framework = "None";
            String database = "None";
            boolean hasCI = false;
            boolean hasTests = false;
            boolean isMonorepo = false;
            String declaredPurpose = "No README.md found.";

            // Detect main markers
            if (rootFilePaths.contains("pom.xml") || rootFilePaths.contains("build.gradle")) {
                primaryLanguage = "Java/Kotlin";
                if (rootFilePaths.contains("pom.xml")) {
                    String pomContent = fetchFileContent(repositoryName, "pom.xml", token);
                    if (pomContent.contains("spring-boot-starter")) {
                        framework = "Spring Boot";
                    }
                }
            } else if (rootFilePaths.contains("package.json")) {
                primaryLanguage = "JavaScript/TypeScript";
                String packageJsonStr = fetchFileContent(repositoryName, "package.json", token);
                try {
                    JsonNode packageJson = objectMapper.readTree(packageJsonStr);
                    JsonNode deps = packageJson.path("dependencies");
                    JsonNode devDeps = packageJson.path("devDependencies");

                    if (hasDependency(deps, devDeps, "react")) framework = "React";
                    else if (hasDependency(deps, devDeps, "vue")) framework = "Vue";
                    else if (hasDependency(deps, devDeps, "svelte")) framework = "Svelte";
                    else if (hasDependency(deps, devDeps, "next")) framework = "Next.js";
                    else if (hasDependency(deps, devDeps, "express")) framework = "Express";
                    else if (hasDependency(deps, devDeps, "@nestjs/core")) framework = "NestJS";

                    if (packageJson.has("workspaces")) {
                        isMonorepo = true;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse package.json: {}", e.getMessage());
                }
            } else if (rootFilePaths.contains("requirements.txt") || rootFilePaths.contains("pyproject.toml") || rootFilePaths.contains("Pipfile")) {
                primaryLanguage = "Python";
                String reqs = "";
                if (rootFilePaths.contains("requirements.txt")) {
                    reqs = fetchFileContent(repositoryName, "requirements.txt", token).toLowerCase();
                } else if (rootFilePaths.contains("pyproject.toml")) {
                    reqs = fetchFileContent(repositoryName, "pyproject.toml", token).toLowerCase();
                }
                if (reqs.contains("django")) framework = "Django";
                else if (reqs.contains("flask")) framework = "Flask";
                else if (reqs.contains("fastapi")) framework = "FastAPI";
            } else if (rootFilePaths.contains("go.mod")) {
                primaryLanguage = "Go";
            } else if (rootFilePaths.contains("Cargo.toml")) {
                primaryLanguage = "Rust";
            } else if (rootFilePaths.contains("composer.json")) {
                primaryLanguage = "PHP";
            } else if (rootFilePaths.contains("Gemfile")) {
                primaryLanguage = "Ruby";
            }

            // Database detection
            if (rootFilePaths.contains("docker-compose.yml")) {
                String compose = fetchFileContent(repositoryName, "docker-compose.yml", token).toLowerCase();
                if (compose.contains("postgres")) database = "PostgreSQL";
                else if (compose.contains("mysql")) database = "MySQL";
                else if (compose.contains("mongo")) database = "MongoDB";
                else if (compose.contains("redis")) database = "Redis";
            }

            // CI check
            for (FileEntry f : filesToScan) {
                if (f.path().startsWith(".github/workflows/")) {
                    hasCI = true;
                    break;
                }
            }

            // Tests check
            for (FileEntry f : filesToScan) {
                String lower = f.path().toLowerCase();
                if (lower.contains("test") || lower.contains("__tests__")) {
                    hasTests = true;
                    break;
                }
            }

            // README / Declared Purpose
            String readmeFile = null;
            for (FileEntry f : filesToScan) {
                if (f.path().equalsIgnoreCase("README.md") && !f.path().contains("/")) {
                    readmeFile = f.path();
                    break;
                }
            }

            if (readmeFile != null) {
                String readmeContent = fetchFileContent(repositoryName, readmeFile, token);
                if (!readmeContent.isBlank()) {
                    declaredPurpose = extractPurpose(readmeContent);
                }
            }

            StackProfile profile = new StackProfile(primaryLanguage, framework, database, hasCI, hasTests,
                    isMonorepo, declaredPurpose, defaultBranch, baselineCommitSha, totalFiles, analyzedFiles);

            return new AnalysisResult(profile, filesToScan);

        } catch (Exception e) {
            log.error("Error during repository stack analysis for {}", repositoryName, e);
            StackProfile errorProfile = new StackProfile("Unknown", "None", "None", false, false, false,
                    "Error during analysis: " + e.getMessage(), "main", "", 0, 0);
            return new AnalysisResult(errorProfile, Collections.emptyList());
        }
    }

    private HttpRequest createGetRequest(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
    }

    private boolean hasDependency(JsonNode deps, JsonNode devDeps, String name) {
        return (deps != null && deps.has(name)) || (devDeps != null && devDeps.has(name));
    }

    public String fetchFileContent(String repositoryName, String path) {
        String token = settingsService.effectiveValue("github_token");
        return fetchFileContent(repositoryName, path, token);
    }

    public String fetchFileContent(String repositoryName, String path, String token) {
        try {
            String url = apiBaseUrl + "/repos/" + organization + "/" + repositoryName + "/contents/" + path;
            HttpRequest request = createGetRequest(url, token);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String base64Content = json.path("content").asText("").replaceAll("\\s+", "");
                if (!base64Content.isEmpty()) {
                    byte[] decoded = Base64.getDecoder().decode(base64Content);
                    return new String(decoded, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch file content for {}/{}", repositoryName, path, e);
        }
        return "";
    }

    private boolean matchesPattern(String path, String pattern) {
        String pat = pattern.trim();
        if (pat.isEmpty()) return false;
        String regex = pat
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return path.matches(regex) || path.contains("/" + pat + "/") || path.startsWith(pat + "/") || path.endsWith("/" + pat);
    }

    private int fileScore(String path) {
        String lower = path.toLowerCase();
        // Priority 0: main configs
        if (lower.equals("package.json") || lower.equals("pom.xml") || lower.equals("requirements.txt") ||
            lower.equals("docker-compose.yml") || lower.equals(".gitignore") || lower.equals(".env") ||
            lower.equals(".env.example") || lower.endsWith("application.properties") || lower.endsWith("application.yml")) {
            return 0;
        }
        // Priority 1: source code by depth
        int slashes = countSlashes(path);
        if (lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".py") ||
            lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".php") || lower.endsWith(".rb")) {
            return 10 + slashes;
        }
        // Priority 2: markup/configs
        if (lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".yml") || lower.endsWith(".yaml") ||
            lower.endsWith(".md") || lower.endsWith(".txt")) {
            return 50 + slashes;
        }
        return 100 + slashes;
    }

    private int countSlashes(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '/') count++;
        }
        return count;
    }

    private String extractPurpose(String readme) {
        String[] lines = readme.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean foundTitle = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                foundTitle = true;
                continue;
            }
            if (trimmed.isEmpty() || trimmed.startsWith("[") || trimmed.startsWith("!")) {
                continue;
            }
            if (foundTitle) {
                sb.append(trimmed).append(" ");
                if (sb.length() > 200) {
                    break;
                }
            }
        }
        String purpose = sb.toString().trim();
        if (purpose.isEmpty()) {
            purpose = readme.replaceAll("#+.*", "").replaceAll("\r?\n", " ").trim();
        }
        return purpose.length() > 300 ? purpose.substring(0, 300) + "..." : purpose;
    }
}
