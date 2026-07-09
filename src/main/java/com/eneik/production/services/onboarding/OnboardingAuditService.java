package com.eneik.production.services.onboarding;

import com.eneik.production.models.persistence.OnboardingAuditFindingEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.RoleCapabilityLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OnboardingAuditService {
    private static final Logger log = LoggerFactory.getLogger(OnboardingAuditService.class);

    private static final Pattern AWS_PAT = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern GENERIC_KEY_PAT = Pattern.compile("(?i)(key|token|secret|password).*['\"]([0-9a-zA-Z]{32,45})['\"]|['\"]([0-9a-zA-Z]{32,45})['\"].*(key|token|secret|password)");
    private static final Pattern PRIVATE_KEY_PAT = Pattern.compile("-----BEGIN.*PRIVATE KEY-----");
    private static final Pattern CONN_STR_PAT = Pattern.compile("://[^:]+:[^@]+@");

    private final OnboardingAuditFindingRepository auditFindingRepository;
    private final RepositoryStackAnalyzer stackAnalyzer;
    private final RoleRepository roleRepository;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final ProjectRepository projectRepository;

    public OnboardingAuditService(
            OnboardingAuditFindingRepository auditFindingRepository,
            RepositoryStackAnalyzer stackAnalyzer,
            RoleRepository roleRepository,
            RoleCapabilityLoader roleCapabilityLoader,
            MLPredictionServiceClient mlPredictionServiceClient,
            ProjectRepository projectRepository) {
        this.auditFindingRepository = auditFindingRepository;
        this.stackAnalyzer = stackAnalyzer;
        this.roleRepository = roleRepository;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public StackProfile runOnboardingAudit(ProjectEntity project) {
        return runOnboardingAudit(project, false);
    }

    @Transactional
    public StackProfile runOnboardingAudit(ProjectEntity project, boolean force) {
        log.info("Starting onboarding audit for project {}", project.getName());

        List<OnboardingAuditFindingEntity> existing = auditFindingRepository.findByProjectIdOrderByCreatedAtAsc(project.getId());
        if (!existing.isEmpty() && project.getStatus() != ProjectStatus.analyzing && !force) {
            throw new IllegalStateException("audit_already_conducted");
        }

        // 1. Analyze stack
        RepositoryStackAnalyzer.AnalysisResult result = stackAnalyzer.analyze(project.getRepositoryName());
        StackProfile stackProfile = result.profile();
        log.info("Stack Profile analyzed: Language={}, Framework={}, DB={}, Branch={}, SHA={}, TotalFiles={}, Analyzed={}",
                stackProfile.primaryLanguage(), stackProfile.framework(), stackProfile.database(),
                stackProfile.defaultBranch(), stackProfile.baselineCommitSha(),
                stackProfile.totalFiles(), stackProfile.analyzedFiles());

        // Save default branch & baseline SHA
        project.setDefaultBranch(stackProfile.defaultBranch());
        project.setBaselineCommitSha(stackProfile.baselineCommitSha());
        projectRepository.save(project);

        // Clean up previous findings
        auditFindingRepository.deleteAll(existing);

        List<OnboardingAuditFindingEntity> findings = new ArrayList<>();

        // 2. Traversal & Secrets Scan
        findings.addAll(scanForSecrets(project, result.filesToScan()));

        // Critical: Missing .gitignore while node_modules or similar folders exist
        boolean hasNodeModules = result.filesToScan().stream().anyMatch(f -> f.path().contains("node_modules/"));
        boolean hasGitignore = result.filesToScan().stream().anyMatch(f -> f.path().equals(".gitignore"));
        if (hasNodeModules && !hasGitignore) {
            findings.add(createFindingEntity(project, "BARCAN-TAG-05", "critical", ".gitignore", null,
                    "Critical: .gitignore is missing while build/dependencies directory like node_modules exists."));
        }

        // Critical: No tests but production claimed in README
        if (!stackProfile.hasTests() && stackProfile.declaredPurpose().toLowerCase().contains("production")) {
            findings.add(createFindingEntity(project, "BARCAN-TAG-06", "critical", "README.md", null,
                    "Critical quality gap: Project claims production readiness, but no test suite was found."));
        }

        // Major: MVC boundaries violation
        for (RepositoryStackAnalyzer.FileEntry file : result.filesToScan()) {
            if (file.path().contains("Controller") && (file.path().endsWith(".java") || file.path().endsWith(".ts") || file.path().endsWith(".js"))) {
                String content = stackAnalyzer.fetchFileContent(project.getRepositoryName(), file.path());
                if (content.contains("JdbcTemplate") || content.contains("SELECT ") || content.contains("select ") || content.contains("Repository")) {
                    findings.add(createFindingEntity(project, "BARCAN-TAG-01", "major", file.path(), null,
                            "Major architectural warning: DB queries or repository operations found inside controller layer, violating Bounded Contexts."));
                    break;
                }
            }
        }

        // Major: God-file (>1000 lines or >50KB)
        for (RepositoryStackAnalyzer.FileEntry file : result.filesToScan()) {
            if (file.size() > 50000 && (file.path().endsWith(".java") || file.path().endsWith(".py") || file.path().endsWith(".js") || file.path().endsWith(".ts"))) {
                findings.add(createFindingEntity(project, "BARCAN-TAG-00", "major", file.path(), null,
                        "Major code smell: God-file detected (size exceeds 50KB). High cognitive complexity and single responsibility violation."));
                break;
            }
        }

        // Major: Lack of CI
        if (!stackProfile.hasCI()) {
            findings.add(createFindingEntity(project, "BARCAN-TAG-05", "major", ".github/workflows/ci.yml", null,
                    "Major DevOps finding: No automated CI workflows found in .github/workflows/. PR builds and checks cannot run automatically."));
        }

        // Minor: Incomplete documentation / design system
        findings.add(createFindingEntity(project, "BARCAN-TAG-11", "minor", "README.md", null,
                "Minor: Incomplete user documentation or missing CSS design system details."));

        // Save findings to DB
        auditFindingRepository.saveAll(findings);

        // 4. Generate Markdown Report docs/reports/onboarding-audit-{slug}.md
        generateMarkdownReport(project, stackProfile, findings);

        return stackProfile;
    }

    private List<OnboardingAuditFindingEntity> scanForSecrets(ProjectEntity project, List<RepositoryStackAnalyzer.FileEntry> files) {
        List<OnboardingAuditFindingEntity> findings = new ArrayList<>();
        for (RepositoryStackAnalyzer.FileEntry file : files) {
            String path = file.path();
            if (isBinaryFile(path)) continue;

            String content = stackAnalyzer.fetchFileContent(project.getRepositoryName(), path);
            if (content.isBlank()) continue;

            String[] lines = content.split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNum = i + 1;

                if (AWS_PAT.matcher(line).find()) {
                    findings.add(createFindingEntity(project, "BARCAN-TAG-05", "critical", path, lineNum,
                            "Critical security risk: Potential AWS secret key detected (pattern AKIA[0-9A-Z]{16})"));
                }
                if (GENERIC_KEY_PAT.matcher(line).find()) {
                    findings.add(createFindingEntity(project, "BARCAN-TAG-05", "critical", path, lineNum,
                            "Critical security risk: Potential generic API key/secret token detected"));
                }
                if (PRIVATE_KEY_PAT.matcher(line).find()) {
                    findings.add(createFindingEntity(project, "BARCAN-TAG-05", "critical", path, lineNum,
                            "Critical security risk: Potential private key header detected"));
                }
                if (CONN_STR_PAT.matcher(line).find()) {
                    findings.add(createFindingEntity(project, "BARCAN-TAG-05", "critical", path, lineNum,
                            "Critical security risk: Connection string with password detected"));
                }
            }
        }
        return findings;
    }

    private boolean isBinaryFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".gif") || lower.endsWith(".pdf") || lower.endsWith(".zip") ||
               lower.endsWith(".gz") || lower.endsWith(".tar") || lower.endsWith(".jar") ||
               lower.endsWith(".war") || lower.endsWith(".class") || lower.endsWith(".exe") ||
               lower.endsWith(".ico") || lower.endsWith(".woff") || lower.endsWith(".woff2") ||
               lower.endsWith(".ttf") || lower.endsWith(".mp3") || lower.endsWith(".mp4");
    }

    private OnboardingAuditFindingEntity createFindingEntity(
            ProjectEntity project, String roleTag, String severity, String filePath, Integer lineNumber, String text) {
        OnboardingAuditFindingEntity finding = new OnboardingAuditFindingEntity();
        finding.setProject(project);
        finding.setRoleTag(roleTag);
        finding.setSeverity(severity);
        finding.setFilePath(filePath);
        finding.setLineNumber(lineNumber);
        finding.setFindingText(text);
        finding.setCreatedAt(Instant.now());
        return finding;
    }

    private void generateMarkdownReport(ProjectEntity project, StackProfile profile, List<OnboardingAuditFindingEntity> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Onboarding Audit Report for ").append(project.getName()).append("\n\n");

        sb.append("## Stack Profile\n\n");
        sb.append("- **Primary Language:** ").append(profile.primaryLanguage()).append("\n");
        sb.append("- **Framework:** ").append(profile.framework()).append("\n");
        sb.append("- **Database:** ").append(profile.database()).append("\n");
        sb.append("- **Monorepo:** ").append(profile.isMonorepo() ? "Yes" : "No").append("\n");
        sb.append("- **Has CI:** ").append(profile.hasCI() ? "Yes" : "No").append("\n");
        sb.append("- **Has Tests:** ").append(profile.hasTests() ? "Yes" : "No").append("\n");
        sb.append("- **Declared Purpose:** ").append(profile.declaredPurpose()).append("\n");
        sb.append("- **Default Branch:** ").append(profile.defaultBranch()).append("\n");
        sb.append("- **Baseline Commit SHA:** ").append(profile.baselineCommitSha()).append("\n");
        if (profile.totalFiles() > 500) {
            sb.append("- **File Analysis:** Repository contains ").append(profile.totalFiles())
              .append(" files, analyzed ").append(profile.analyzedFiles()).append(" most relevant ones.\n\n");
        } else {
            sb.append("- **File Analysis:** Analyzed all ").append(profile.totalFiles()).append(" files.\n\n");
        }

        sb.append("## Audit Findings\n\n");

        Map<String, List<OnboardingAuditFindingEntity>> groupedBySeverity = findings.stream()
                .collect(Collectors.groupingBy(OnboardingAuditFindingEntity::getSeverity));

        String[] severities = {"critical", "major", "minor"};
        for (String sev : severities) {
            List<OnboardingAuditFindingEntity> list = groupedBySeverity.getOrDefault(sev, new ArrayList<>());
            sb.append("### ").append(sev.toUpperCase()).append(" FINDINGS (").append(list.size()).append(")\n\n");
            if (list.isEmpty()) {
                sb.append("No ").append(sev).append(" findings detected.\n\n");
            } else {
                Map<String, List<OnboardingAuditFindingEntity>> groupedByRole = list.stream()
                        .collect(Collectors.groupingBy(OnboardingAuditFindingEntity::getRoleTag));
                
                for (Map.Entry<String, List<OnboardingAuditFindingEntity>> entry : groupedByRole.entrySet()) {
                    sb.append("#### Role: ").append(entry.getKey()).append("\n");
                    for (OnboardingAuditFindingEntity f : entry.getValue()) {
                        sb.append("- **File:** `").append(f.getFilePath()).append("` ");
                        if (f.getLineNumber() != null) {
                            sb.append("(Line ").append(f.getLineNumber()).append(") ");
                        }
                        sb.append("  \n  ").append(f.getFindingText()).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        try {
            Path reportDir = Paths.get("docs/reports");
            Files.createDirectories(reportDir);
            Path reportFile = reportDir.resolve("onboarding-audit-" + project.getSlug() + ".md");
            Files.writeString(reportFile, sb.toString());
            log.info("Onboarding audit report written to {}", reportFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write onboarding audit report for {}", project.getSlug(), e);
        }
    }
}
